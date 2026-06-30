package io.horizontalsystems.swapapp.swap

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Provider-selection helpers for [SwapSelectProviderScreen]. Pure (no ViewModel): the screen owns
 * the sort state and recomputes these rows from the live quote list on each open, so it can never
 * show a stale snapshot vs the swap screen. Each row carries provider name, output amount, estimated
 * time and fee, plus BestPrice/BestTime badges. Adapted from Unstoppable Wallet's
 * SwapSelectProviderViewModel (CurrencyManager/fiat, coinPrice subscriptions, PriceImpact/RiskScore
 * removed — none of that infrastructure exists here).
 */
fun providerQuoteViewItems(
    quotes: List<SwapProviderQuote>,
    sortType: ProviderSortType,
): List<ProviderQuoteViewItem> {
    val bestPriceId = quotes.maxByOrNull { it.amountOut }?.provider?.id
    val bestTimeId = quotes
        .filter { it.estimationTime != null }
        .minByOrNull { it.estimationTime!! }
        ?.provider?.id

    val sorted = when (sortType) {
        ProviderSortType.BestPrice -> quotes.sortedByDescending { it.amountOut }
        ProviderSortType.BestTime -> quotes.sortedBy { it.estimationTime ?: Long.MAX_VALUE }
    }

    return sorted.map { quote ->
        ProviderQuoteViewItem(
            quote = quote,
            providerTitle = quote.provider.title,
            amountOut = "${formatProviderAmount(quote.amountOut)} ${quote.tokenOut.ticker}",
            fee = quote.fee?.let { "${formatProviderAmount(it.amount)} ${it.asset}" },
            estimatedTime = quote.estimationTime?.let { formatDurationShort(it) } ?: "—",
            isBestPrice = quote.provider.id == bestPriceId,
            isBestTime = quote.provider.id == bestTimeId,
        )
    }
}

private fun formatProviderAmount(value: BigDecimal): String =
    value.setScale(value.scale().coerceAtMost(8), RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()

data class ProviderQuoteViewItem(
    val quote: SwapProviderQuote,
    val providerTitle: String,
    val amountOut: String,
    val fee: String?,
    val estimatedTime: String,
    val isBestPrice: Boolean,
    val isBestTime: Boolean,
)

enum class ProviderSortType(val title: String) {
    BestPrice("Best Price"),
    BestTime("Best Time"),
}

/** Compact duration formatter, e.g. 1830 -> "30m 30s", 600 -> "10m". */
fun formatDurationShort(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return buildString {
        if (hours > 0) append("${hours}h ")
        if (minutes > 0) append("${minutes}m ")
        if (seconds > 0 || (hours == 0L && minutes == 0L)) append("${seconds}s")
    }.trim()
}
