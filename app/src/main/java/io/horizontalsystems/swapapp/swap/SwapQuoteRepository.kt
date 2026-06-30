package io.horizontalsystems.swapapp.swap

import io.horizontalsystems.swapapp.swap.api.RateRequestDto
import io.horizontalsystems.swapapp.swap.api.RouteDto
import io.horizontalsystems.swapapp.swap.api.SwapApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.math.BigDecimal

/**
 * Fetches quotes from the swap API's `POST /rate`. A single request prices every eligible provider's
 * route; we map each [RouteDto] to a [SwapProviderQuote]. Read-only — committing happens later via
 * `POST /swap` ([io.horizontalsystems.swapapp.swap.execution.SwapDepositRepository]).
 */
class SwapQuoteRepository(
    private val api: io.horizontalsystems.swapapp.swap.api.SwapApi = SwapApiClient.api,
) {
    /** Providers that can route both tokens (the API rejects unsupported ones). */
    fun supportedProviders(tokenIn: SwapToken, tokenOut: SwapToken): List<String> =
        tokenIn.providers.intersect(tokenOut.providers.toSet()).toList()

    /**
     * Quote [amountIn] across [providers]. Returns routes sorted best-price first. Empty list = no
     * route.
     * @throws retrofit2.HttpException / IOException on transport errors (handled by the caller).
     */
    suspend fun quote(
        tokenIn: SwapToken,
        tokenOut: SwapToken,
        amountIn: BigDecimal,
        providers: List<String>,
    ): List<SwapProviderQuote> = withContext(Dispatchers.IO) {
        if (providers.isEmpty()) return@withContext emptyList()

        val response = try {
            api.rate(
                RateRequestDto(
                    sellAsset = tokenIn.identifier,
                    buyAsset = tokenOut.identifier,
                    sellAmount = amountIn.stripTrailingZeros().toPlainString(),
                    providers = providers,
                )
            )
        } catch (e: HttpException) {
            // 404 = no provider could route the pair — treat as "no route".
            if (e.code() == 404) return@withContext emptyList() else throw e
        }

        response.routes.orEmpty()
            .mapNotNull { route -> route.toProviderQuote(tokenIn, tokenOut, amountIn) }
            .sortedByDescending { it.amountOut }
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
