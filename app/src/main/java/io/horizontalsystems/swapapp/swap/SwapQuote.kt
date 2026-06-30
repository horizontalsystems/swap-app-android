package io.horizontalsystems.swapapp.swap

import java.math.BigDecimal

/**
 * A single provider's route, mapped from the swap API's `POST /rate` response. Pricing only — the
 * deposit address + tracking handle (`uuid`) come later from `POST /swap`.
 */
data class SwapQuote(
    val amountOut: BigDecimal,
    val tokenIn: SwapToken,
    val tokenOut: SwapToken,
    val amountIn: BigDecimal,
    val estimationTime: Long? = null,
    val fee: SwapFee? = null,
)

/** A fee from the quote, expressed as an amount in a named asset (e.g. 0.037 in "ETH"). */
data class SwapFee(
    val amount: BigDecimal,
    val asset: String,
)

data class SwapProviderQuote(
    val provider: SwapProvider,
    val swapQuote: SwapQuote,
    val createdAt: Long = System.currentTimeMillis()
) {
    val tokenIn by swapQuote::tokenIn
    val tokenOut by swapQuote::tokenOut
    val amountIn by swapQuote::amountIn
    val amountOut by swapQuote::amountOut
    val estimationTime by swapQuote::estimationTime
    val fee by swapQuote::fee
}
