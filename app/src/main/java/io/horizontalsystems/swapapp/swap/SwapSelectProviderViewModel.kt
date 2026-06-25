package io.horizontalsystems.swapapp.swap

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Provider-selection logic, adapted from Unstoppable Wallet's SwapSelectProviderViewModel for the
 * accountless app. Removed: CurrencyManager/fiat conversion, live coinPrice subscriptions,
 * PriceImpactCalculator and RiskScore — none of that infrastructure exists here. Kept: the list of
 * competing [SwapProviderQuote]s, the BestPrice/BestTime sorting, and the current selection.
 *
 * Each row shows what the task asked for: provider name, output amount, estimated time, fee.
 */
class SwapSelectProviderViewModel(
    private val quotes: List<SwapProviderQuote>,
    private val selectedQuote: SwapProviderQuote?,
) : ViewModel() {

    private var sortType = ProviderSortType.BestPrice

    var uiState by mutableStateOf(buildState())
        private set

    fun setSortType(sortType: ProviderSortType) {
        this.sortType = sortType
        uiState = buildState()
    }

    private fun buildState(): SwapSelectProviderUiState {
        val bestPriceId = quotes.maxByOrNull { it.amountOut }?.provider?.id
        val bestTimeId = quotes
            .filter { it.estimationTime != null }
            .minByOrNull { it.estimationTime!! }
            ?.provider?.id

        val items = quotes.sorted().map { quote ->
            ProviderQuoteViewItem(
                quote = quote,
                providerTitle = quote.provider.title,
                amountOut = "${formatAmount(quote.amountOut)} ${quote.tokenOut.ticker}",
                fee = quote.fee?.let { "${formatAmount(it.amount)} ${it.asset}" },
                estimatedTime = quote.estimationTime?.let { formatDurationShort(it) } ?: "—",
                isBestPrice = quote.provider.id == bestPriceId,
                isBestTime = quote.provider.id == bestTimeId,
            )
        }

        return SwapSelectProviderUiState(
            quoteViewItems = items,
            selectedQuote = selectedQuote,
            sortType = sortType,
        )
    }

    private fun List<SwapProviderQuote>.sorted(): List<SwapProviderQuote> = when (sortType) {
        ProviderSortType.BestPrice -> sortedByDescending { it.amountOut }
        ProviderSortType.BestTime -> sortedBy { it.estimationTime ?: Long.MAX_VALUE }
    }

    private fun formatAmount(value: BigDecimal): String =
        value.setScale(value.scale().coerceAtMost(8), RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()

    class Factory(
        private val quotes: List<SwapProviderQuote>,
        private val selectedQuote: SwapProviderQuote?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SwapSelectProviderViewModel(quotes, selectedQuote) as T
        }
    }
}

data class SwapSelectProviderUiState(
    val quoteViewItems: List<ProviderQuoteViewItem>,
    val selectedQuote: SwapProviderQuote?,
    val sortType: ProviderSortType,
)

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
