package io.horizontalsystems.swapapp.swap

import android.util.Log
import io.horizontalsystems.swapapp.swap.api.QuoteRequestDto
import io.horizontalsystems.swapapp.swap.api.RouteDto
import io.horizontalsystems.swapapp.swap.api.SwapApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.math.BigDecimal

/**
 * Fetches quotes from the swap API's `POST /quote`. Unlike the old per-provider fan-out, a single
 * request returns every provider's route; we map each [RouteDto] to a [SwapProviderQuote].
 */
class SwapQuoteRepository(
    private val api: io.horizontalsystems.swapapp.swap.api.SwapApi = SwapApiClient.api,
    private val memolessAssets: MemolessAssetRepository = MemolessAssetRepository(),
) {
    /** Providers that can route both tokens (the API rejects unsupported ones). */
    fun supportedProviders(tokenIn: SwapToken, tokenOut: SwapToken): List<String> =
        tokenIn.providers.intersect(tokenOut.providers.toSet()).toList()

    /**
     * Dry quote for [amountIn]. Returns routes sorted best-price first. Empty list = no route.
     * @throws retrofit2.HttpException / IOException on transport errors (handled by the caller).
     */
    suspend fun quote(
        tokenIn: SwapToken,
        tokenOut: SwapToken,
        amountIn: BigDecimal,
        providers: List<String>,
    ): List<SwapProviderQuote> = withContext(Dispatchers.IO) {
        // The app supports memoless swaps only — deposits the user can pay without manually
        // attaching a memo. Drop providers that can't comply (see [applyMemolessPolicy]) up front,
        // so they're never shown and we never make a doomed memoless register call.
        val effectiveProviders = applyMemolessPolicy(providers, tokenIn, tokenOut)
        if (effectiveProviders.isEmpty()) return@withContext emptyList()

        val response = try {
            api.quote(
                QuoteRequestDto(
                    sellAsset = tokenIn.identifier,
                    buyAsset = tokenOut.identifier,
                    sellAmount = amountIn.stripTrailingZeros().toPlainString(),
                    providers = effectiveProviders,
                    dry = true,
                )
            )
        } catch (e: HttpException) {
            // The API returns 404 when every provider fails to route the pair — treat as "no route".
            if (e.code() == 404) return@withContext emptyList() else throw e
        }

        response.routes.orEmpty()
            .mapNotNull { route -> route.toProviderQuote(tokenIn, tokenOut, amountIn) }
            .sortedByDescending { it.amountOut }
    }

    /**
     * Register a real swap with the chosen [providerId] (`dry = false` + [destinationAddress]) and
     * return its deposit details. The route comes back with a real `inboundAddress` (and, for
     * THORChain, a `memo` that must accompany the deposit). Some CEX providers also require a
     * [refundAddress] or they reject the request.
     */
    suspend fun createDeposit(
        tokenIn: SwapToken,
        tokenOut: SwapToken,
        amountIn: BigDecimal,
        providerId: String,
        destinationAddress: String,
        refundAddress: String? = null,
    ): SwapDeposit = withContext(Dispatchers.IO) {
        Log.d(
            TAG,
            "register request: provider=$providerId ${tokenIn.identifier} -> ${tokenOut.identifier} " +
                "amount=${amountIn.stripTrailingZeros().toPlainString()} refundAddress=${refundAddress != null}"
        )
        val response = try {
            api.quote(
                QuoteRequestDto(
                    sellAsset = tokenIn.identifier,
                    buyAsset = tokenOut.identifier,
                    sellAmount = amountIn.stripTrailingZeros().toPlainString(),
                    providers = listOf(providerId),
                    dry = false,
                    destinationAddress = destinationAddress,
                    refundAddress = refundAddress,
                )
            )
        } catch (e: HttpException) {
            // 404 / 400 here usually means the provider couldn't register this swap (e.g. a CEX
            // that requires a refund address). Log the server's actual reason (BASIC OkHttp logging
            // hides the body) before surfacing a clean "no route".
            val errorBody = e.response()?.errorBody()?.string()
            Log.e(
                TAG,
                "register failed: provider=$providerId ${tokenIn.identifier} -> ${tokenOut.identifier} " +
                    "http=${e.code()} body=$errorBody"
            )
            if (e.code() == 404 || e.code() == 400) throw SwapRouteNotFound() else throw e
        }

        val route = response.routes.orEmpty().firstOrNull() ?: run {
            Log.w(TAG, "register returned no route: provider=$providerId ${tokenIn.identifier} -> ${tokenOut.identifier}")
            throw SwapRouteNotFound()
        }
        Log.d(
            TAG,
            "register OK: provider=$providerId inbound=${!route.inboundAddress.isNullOrBlank()} " +
                "target=${!route.targetAddress.isNullOrBlank()} memo=${!route.memo.isNullOrBlank()} " +
                "qrCodeStr=${!route.qrCodeStr.isNullOrBlank()} providerSwapId=${route.providerSwapId}"
        )
        val address = route.inboundAddress?.takeIf { it.isNotBlank() }
            ?: route.targetAddress?.takeIf { it.isNotBlank() }
            ?: throw SwapRouteNotFound()

        val expiresIn = route.expiration?.toLongOrNull()
            ?.let { it - System.currentTimeMillis() / 1000 }
            ?.coerceAtLeast(0)

        SwapDeposit(
            depositAddress = address,
            sendAmount = amountIn,
            amountOut = route.expectedBuyAmount?.toBigDecimalOrNull(),
            memo = route.memo?.takeIf { it.isNotBlank() },
            paymentUri = route.qrCodeStr?.takeIf { it.isNotBlank() },
            providerSwapId = route.providerSwapId,
            secondsRemaining = expiresIn,
        )
    }

    /**
     * Keep only providers that can deliver a memoless deposit for this pair:
     *  - **Non-memo providers** (CEX / intents like NEAR) hand the user a payment URI directly → kept.
     *  - **THORChain** is memo-based, but the memoless service folds its memo into the send amount —
     *    kept only when both sides of the pair are in the live memoless asset list. (Kept defensively
     *    if that list can't be fetched; the deposit step still has a memo fallback.)
     *  - **MayaChain** (and any other on-chain memo DEX, incl. THORChain streaming) has no memoless
     *    path — the memoless service is THORChain-only — so it's dropped.
     */
    private suspend fun applyMemolessPolicy(
        providers: List<String>,
        tokenIn: SwapToken,
        tokenOut: SwapToken,
    ): List<String> {
        if (providers.none { SwapProvider(it).requiresMemoDeposit }) return providers

        val supported = memolessAssets.supportedAssets()
        val thorchainMemoless = supported == null ||
            (tokenIn.identifier.uppercase() in supported && tokenOut.identifier.uppercase() in supported)

        return providers.filter { id ->
            val provider = SwapProvider(id)
            when {
                !provider.requiresMemoDeposit -> true
                provider.id.equals("THORCHAIN", ignoreCase = true) -> thorchainMemoless
                else -> false
            }
        }
    }

    private fun RouteDto.toProviderQuote(
        tokenIn: SwapToken,
        tokenOut: SwapToken,
        amountIn: BigDecimal,
    ): SwapProviderQuote? {
        val out = expectedBuyAmount?.toBigDecimalOrNull() ?: return null
        val providerId = providers.firstOrNull() ?: return null

        return SwapProviderQuote(
            provider = SwapProvider(providerId),
            swapQuote = SwapQuote(
                amountOut = out,
                tokenIn = tokenIn,
                tokenOut = tokenOut,
                amountIn = amountIn,
                estimationTime = estimatedTime?.total,
                fee = toFee(),
                inboundAddress = inboundAddress?.takeIf { it.isNotBlank() },
                providerSwapId = providerSwapId,
            ),
        )
    }

    /** Pick a representative fee (prefer the "service" fee), with its asset ticker for display. */
    private fun RouteDto.toFee(): SwapFee? {
        val fee = fees.firstOrNull { it.type == "service" } ?: fees.firstOrNull() ?: return null
        val amount = fee.amount?.toBigDecimalOrNull() ?: return null
        val ticker = fee.asset?.substringAfterLast('.')?.substringBefore('-') ?: ""
        return SwapFee(amount, ticker)
    }

    companion object {
        private const val TAG = "SwapProviders"
    }
}

/**
 * Real deposit details for a registered swap (from a non-dry `/quote`). [memo] is required for
 * THORChain deposits and must be attached to the send.
 */
data class SwapDeposit(
    val depositAddress: String,
    val sendAmount: BigDecimal,
    val amountOut: BigDecimal?,
    val memo: String?,
    /** BIP21 payment URI from the provider's route (`qrCodeStr`); null for THORChain. */
    val paymentUri: String?,
    val providerSwapId: String?,
    val secondsRemaining: Long?,
)
