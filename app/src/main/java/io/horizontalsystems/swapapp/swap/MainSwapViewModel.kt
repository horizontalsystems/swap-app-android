package io.horizontalsystems.swapapp.swap

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * Main swap screen logic, adapted from Unstoppable Wallet's SwapViewModel for an accountless,
 * "memoless" app. Everything tied to a local wallet is removed: TokenBalanceService, FiatService,
 * PriceImpactService, TimerService, NetworkAvailabilityService, SwapDefaultTokenService,
 * SwapTermsManager, SwapRecordManager, AdapterManager, WalletManager and the AML / proceed /
 * transaction-signing flow.
 *
 * What's kept is the quote pipeline: [SwapQuoteService] drives a single, quote-focused UI state.
 * Proceeding is delegated to the screen's onProceed callback instead of building a transaction.
 */
class MainSwapViewModel(application: Application) : AndroidViewModel(application) {

    private val quoteService = SwapQuoteService()
    private val priceService = PriceService()
    private val tokenRepository = SwapTokenRepository()
    private val tokenStore = SwapTokenStore(application)

    private var quoteState = quoteService.stateFlow.value
    private var prices: Map<String, BigDecimal> = emptyMap()
    private var pricedIds: Set<String> = emptySet()
    private var priceJob: Job? = null

    var uiState by mutableStateOf(buildState())
        private set

    init {
        quoteService.start()
        viewModelScope.launch {
            quoteService.stateFlow.collect { state ->
                quoteState = state
                uiState = buildState()
                refreshPrices(state.tokenIn, state.tokenOut)
            }
        }
        restoreSelectedPair()
    }

    fun onEnterAmount(amount: BigDecimal?) = quoteService.setAmount(amount)

    fun onSelectTokenIn(token: SwapToken) {
        quoteService.setTokenIn(token)
        persistSelectedPair()
    }

    fun onSelectTokenOut(token: SwapToken) {
        quoteService.setTokenOut(token)
        persistSelectedPair()
    }

    fun onSwitchPairs() {
        quoteService.switchPairs()
        persistSelectedPair()
    }

    /** Apply the user's preferred provider; SwapQuoteService promotes it to the primary quote. */
    fun onSelectQuote(quote: SwapProviderQuote) = quoteService.selectQuote(quote)

    /**
     * Pre-select the pair on launch. The instant path is a plain prefs read — no network: the
     * persisted token snapshots of the last-used pair, or, on a fresh install, the bundled
     * [SwapDefaultTokens] pair. The token universe then loads in the background and the pair is
     * re-resolved against it: snapshots pick up current metadata, and the first-run seed is
     * replaced by the live most-popular pair (the top tokens from [SwapPopularTokens]).
     */
    private fun restoreSelectedPair() {
        val snapshotIn = tokenStore.tokenIn
        val snapshotOut = tokenStore.tokenOut
        // The id-only keys also cover installs that predate the snapshots.
        val storedInId = snapshotIn?.identifier ?: tokenStore.tokenInId
        val storedOutId = snapshotOut?.identifier ?: tokenStore.tokenOutId
        // First run = nothing was ever persisted. (An id without a snapshot — a pre-snapshot
        // install — must NOT be overridden by the seed; it resolves below instead.)
        val firstRun = storedInId == null && storedOutId == null

        val instantIn = snapshotIn ?: SwapDefaultTokens.tokenIn.takeIf { firstRun }
        val instantOut = snapshotOut ?: SwapDefaultTokens.tokenOut.takeIf { firstRun }
        instantIn?.let { quoteService.setTokenIn(it) }
        instantOut?.let { quoteService.setTokenOut(it) }

        viewModelScope.launch {
            val all = try {
                tokenRepository.all()
            } catch (e: Throwable) {
                return@launch // the instant pair (if any) stays; the user can still pick manually
            }
            val byId = all.associateBy { it.identifier }

            val tokenIn: SwapToken?
            val tokenOut: SwapToken?
            if (!firstRun) {
                // A stored token that has since disappeared from the universe is simply skipped.
                tokenIn = storedInId?.let { byId[it] }
                tokenOut = storedOutId?.let { byId[it] }
            } else {
                // No swap was ever done — resolve the live most-popular pair.
                tokenIn = SwapPopularTokens.build(all, context = null).firstOrNull()
                tokenOut = tokenIn?.let { SwapPopularTokens.build(all, context = it).firstOrNull() }
            }

            // Apply the freshly resolved tokens, but never clobber a token the user changed while
            // the universe was loading. Re-setting an identical token is a no-op in the service.
            val current = quoteService.stateFlow.value
            if (tokenIn != null && current.tokenIn?.identifier == instantIn?.identifier) {
                quoteService.setTokenIn(tokenIn)
            }
            if (tokenOut != null && current.tokenOut?.identifier == instantOut?.identifier) {
                quoteService.setTokenOut(tokenOut)
            }
            // Converge the stored snapshots to the fresh metadata for the next launch.
            persistSelectedPair()
        }
    }

    /** Persist the current pair (id + full snapshot) so it's pre-selected next launch. */
    private fun persistSelectedPair() {
        val state = quoteService.stateFlow.value
        tokenStore.tokenInId = state.tokenIn?.identifier
        tokenStore.tokenOutId = state.tokenOut?.identifier
        tokenStore.tokenIn = state.tokenIn
        tokenStore.tokenOut = state.tokenOut
    }

    /** Fetch USD prices when the pair's CoinGecko ids change; the service caches the rest. */
    private fun refreshPrices(tokenIn: SwapToken?, tokenOut: SwapToken?) {
        val ids = listOfNotNull(tokenIn?.coingeckoId, tokenOut?.coingeckoId).toSet()
        if (ids == pricedIds) return
        pricedIds = ids

        // Cancel any in-flight fetch for a previous id set — e.g. the tokenIn-only fetch fired
        // while the pair was still being restored. Letting it finish would overwrite [prices]
        // with its subset and silently drop the other token's fiat values.
        priceJob?.cancel()

        if (ids.isEmpty()) {
            prices = emptyMap()
            uiState = buildState()
            return
        }

        priceJob = viewModelScope.launch {
            // Merge instead of replace so a fetch for a subset can never drop known prices.
            prices = prices + priceService.prices(ids.toList())
            uiState = buildState()
        }
    }

    private fun buildState(): MainSwapUiState {
        val s = quoteState
        val amountOut = s.quote?.amountOut
        val priceIn = s.tokenIn?.coingeckoId?.let { prices[it] }
        val priceOut = s.tokenOut?.coingeckoId?.let { prices[it] }
        return MainSwapUiState(
            amountIn = s.amountIn,
            tokenIn = s.tokenIn,
            tokenOut = s.tokenOut,
            amountOut = amountOut,
            quoting = s.quoting,
            quote = s.quote,
            quotes = s.quotes,
            error = s.error,
            priceIn = priceIn,
            priceOut = priceOut,
            fiatIn = priceIn?.multiply(s.amountIn ?: BigDecimal.ZERO),
            fiatOut = priceOut?.multiply(amountOut ?: BigDecimal.ZERO),
        )
    }
}

data class MainSwapUiState(
    val amountIn: BigDecimal?,
    val tokenIn: SwapToken?,
    val tokenOut: SwapToken?,
    val amountOut: BigDecimal?,
    val quoting: Boolean,
    val quote: SwapProviderQuote?,
    val quotes: List<SwapProviderQuote>,
    val error: Throwable?,
    val priceIn: BigDecimal? = null,
    val priceOut: BigDecimal? = null,
    val fiatIn: BigDecimal? = null,
    val fiatOut: BigDecimal? = null,
) {
    val selectedProvider: SwapProvider? get() = quote?.provider

    /** Next is actionable only once a valid amount, both tokens and a fetched quote exist. */
    val canProceed: Boolean
        get() = !quoting &&
            amountIn != null && amountIn > BigDecimal.ZERO &&
            tokenIn != null && tokenOut != null && quote != null
}
