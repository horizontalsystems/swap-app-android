package io.horizontalsystems.swapapp.swap

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
        val response = try {
            api.quote(
                QuoteRequestDto(
                    sellAsset = tokenIn.identifier,
                    buyAsset = tokenOut.identifier,
                    sellAmount = amountIn.stripTrailingZeros().toPlainString(),
                    providers = providers,
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
            // that requires a refund address). Surface a clean "no route" rather than a raw HTTP code.
            if (e.code() == 404 || e.code() == 400) throw SwapRouteNotFound() else throw e
        }

        val route = response.routes.orEmpty().firstOrNull() ?: throw SwapRouteNotFound()
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
            providerSwapId = route.providerSwapId,
            secondsRemaining = expiresIn,
        )
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
    val providerSwapId: String?,
    val secondsRemaining: Long?,
)
