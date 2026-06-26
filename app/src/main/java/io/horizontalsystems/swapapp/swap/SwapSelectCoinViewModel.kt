package io.horizontalsystems.swapapp.swap

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Token selector backed by the swap API token list ([SwapTokenRepository]). Accountless — no
 * balances, no wallet.
 *
 * With an empty query it shows two sections: context-aware **Popular** tokens (built by
 * [SwapPopularTokens]) rendered as a horizontal row, and **Top tokens** — a short list of the
 * highest market-cap coins (ranked via [MarketCapService]), one representative token per coin,
 * excluding everything already shown in Popular. Typing switches to a flat list of local search
 * results over the whole token universe.
 */
class SwapSelectCoinViewModel(
    private val otherSelectedToken: SwapToken?,
    private val repository: SwapTokenRepository = SwapTokenRepository(),
    private val marketCapService: MarketCapService = MarketCapService(),
) : ViewModel() {

    private var query = ""
    private var loading = true
    private var error = false
    private var popular = listOf<TokenViewItem>()
    private var topTokens = listOf<TokenViewItem>()
    private var searchResults = listOf<TokenViewItem>()

    private var searchJob: Job? = null

    var uiState by mutableStateOf(buildState())
        private set

    init {
        loadSections()
    }

    fun setQuery(q: String) {
        query = q
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                searchResults = if (q.isBlank()) {
                    emptyList()
                } else {
                    repository.search(q, exclude = otherSelectedToken).map { it.toViewItem() }
                }
                error = false
            } catch (e: Throwable) {
                android.util.Log.e("SwapSelectCoin", "token search failed", e)
                error = true
            }
            uiState = buildState()
        }
    }

    private fun loadSections() {
        viewModelScope.launch {
            try {
                val (all, ranks) = coroutineScope {
                    val allDeferred = async { repository.all() }
                    val ranksDeferred = async { marketCapService.ranks() }
                    allDeferred.await() to ranksDeferred.await()
                }

                val popularTokens = SwapPopularTokens.build(all, otherSelectedToken)
                // Coins already shown in Popular — excluded from Top tokens so nothing repeats.
                val popularCoinIds = popularTokens.mapNotNull { it.coingeckoId }.toSet()

                popular = popularTokens.map { it.toViewItem() }
                topTokens = all
                    .filter { it.identifier != otherSelectedToken?.identifier }
                    .filter { it.coingeckoId != null && it.coingeckoId !in popularCoinIds }
                    // One representative token per coin (a coin can live on many chains).
                    .groupBy { it.coingeckoId!! }
                    .map { (_, group) -> group.minWith(REPRESENTATIVE_ORDER) }
                    // Highest market cap first; unranked coins sink and fall off the short list.
                    .sortedBy { ranks[it.coingeckoId] ?: Int.MAX_VALUE }
                    .take(TOP_TOKENS_LIMIT)
                    .map { it.toViewItem() }
                loading = false
                error = false
            } catch (e: Throwable) {
                android.util.Log.e("SwapSelectCoin", "token load failed", e)
                loading = false
                error = true
            }
            uiState = buildState()
        }
    }

    private fun buildState() = SwapSelectCoinUiState(
        query = query,
        loading = loading,
        error = error,
        searching = query.isNotBlank(),
        popular = popular,
        topTokens = topTokens,
        searchResults = searchResults,
    )

    private fun SwapToken.toViewItem() = TokenViewItem(
        token = this,
        code = ticker,
        name = name,
        blockchain = chain,
        logoUrl = logoUrl,
    )

    class Factory(private val otherSelectedToken: SwapToken?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SwapSelectCoinViewModel(otherSelectedToken) as T
        }
    }

    companion object {
        private const val TOP_TOKENS_LIMIT = 25

        // Preferred chains when a coin lives on several, so the representative token shown is the
        // canonical one (e.g. ETH-hosted USDC, rather than some L2 variant).
        private val CHAIN_PRIORITY =
            listOf("ETH", "BTC", "BSC", "BASE", "ARB", "OP", "POL", "SOL", "TRON")
                .withIndex().associate { (i, chain) -> chain to i }

        /** Native coin first, then by chain priority, then a stable id tiebreak. */
        private val REPRESENTATIVE_ORDER = compareBy<SwapToken>(
            { if (it.isNative) 0 else 1 },
            { CHAIN_PRIORITY[it.chain] ?: Int.MAX_VALUE },
            { it.identifier },
        )
    }
}

data class TokenViewItem(
    val token: SwapToken,
    val code: String,
    val name: String,
    val blockchain: String,
    val logoUrl: String?,
)

data class SwapSelectCoinUiState(
    val query: String,
    val loading: Boolean,
    val error: Boolean,
    val searching: Boolean,
    val popular: List<TokenViewItem>,
    val topTokens: List<TokenViewItem>,
    val searchResults: List<TokenViewItem>,
)
