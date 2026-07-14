package io.horizontalsystems.swapapp.swap

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Route-selection helpers for [SwapSelectRouteScreen]. Pure (no ViewModel): the screen owns the
 * sort state and recomputes these rows from the live quote list on each open, so it can never show
 * a stale snapshot vs the swap screen. Each row carries the output amount, its fiat value, the
 * deviation from the best rate, the provider's safety [RouteRating] (from the API's `amlPolicy`)
 * and the estimated time.
 */
fun routeViewItems(
    quotes: List<SwapProviderQuote>,
    sortType: RouteSortType,
    priceOut: BigDecimal?,
    showProviderNames: Boolean = false,
): List<RouteViewItem> {
    val bestAmount = quotes.maxByOrNull { it.amountOut }?.amountOut

    val sorted = when (sortType) {
        RouteSortType.BestRate -> quotes.sortedByDescending { it.amountOut }
        RouteSortType.Fastest -> quotes.sortedBy { it.estimationTime ?: Long.MAX_VALUE }
    }

    return sorted.map { quote ->
        RouteViewItem(
            quote = quote,
            amountOut = "${formatRouteAmount(quote.amountOut)} ${quote.tokenOut.ticker}",
            fiatOut = priceOut?.let { formatFiat(it.multiply(quote.amountOut)) },
            diffPercent = diffPercent(quote.amountOut, bestAmount),
            rating = RouteRating.from(quote.amlPolicy),
            estimatedTime = quote.estimationTime?.let { formatDurationShort(it) } ?: "N/A",
            providerName = quote.provider.title.takeIf { showProviderNames },
        )
    }
}

/**
 * Deviation from the best route's output as "(-6.4%)", or null within [DIFF_SHOW_THRESHOLD] of the
 * best — small spreads are noise, only a notably worse rate is called out.
 */
private fun diffPercent(amount: BigDecimal, best: BigDecimal?): String? {
    if (best == null || best.signum() <= 0) return null
    val pct = amount.subtract(best)
        .divide(best, 3, RoundingMode.HALF_UP)
        .multiply(BigDecimal(100))
    if (pct >= DIFF_SHOW_THRESHOLD) return null
    return "(${pct.setScale(1, RoundingMode.HALF_UP).toPlainString()}%)"
}

private val DIFF_SHOW_THRESHOLD = BigDecimal("-1")

private fun formatRouteAmount(value: BigDecimal): String =
    value.setScale(value.scale().coerceAtMost(8), RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()

data class RouteViewItem(
    val quote: SwapProviderQuote,
    val amountOut: String,
    /** Fiat value of the output, e.g. "$24.90"; null while the USD price is unavailable. */
    val fiatOut: String?,
    /** Deviation from the best rate, e.g. "(-6.4%)"; null when at/near the best. */
    val diffPercent: String?,
    val rating: RouteRating?,
    val estimatedTime: String,
    /** Provider title, e.g. "ChangeNOW"; non-null only with the debug "provider names" setting on. */
    val providerName: String? = null,
)

/** Provider safety rating, from the API's per-route `amlPolicy`. */
enum class RouteRating(val label: String) {
    Excellent("Excellent"),
    Good("Good"),
    Fair("Fair");

    companion object {
        fun from(amlPolicy: String?): RouteRating? = when (amlPolicy?.lowercase()) {
            "excellent" -> Excellent
            "good" -> Good
            "fair" -> Fair
            else -> null
        }
    }
}

enum class RouteSortType(val title: String) {
    BestRate("Best Rate"),
    Fastest("Fastest"),
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
