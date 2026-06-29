package io.horizontalsystems.swapapp.swap

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
     * Pre-select the pair on launch: restore the last-used tokens from [SwapTokenStore], or — when
     * nothing was ever selected — default to the most popular pair (the top tokens from
     * [SwapPopularTokens], e.g. BTC → USDT). Resolved against the live token universe, so a stored
     * token that has since disappeared is simply skipped.
     */
    private fun restoreSelectedPair() {
        viewModelScope.launch {
            val all = try {
                tokenRepository.all()
            } catch (e: Throwable) {
                return@launch // leave the pair empty; the user can still pick manually
            }
            val byId = all.associateBy { it.identifier }

            val storedIn = tokenStore.tokenInId?.let { byId[it] }
            val storedOut = tokenStore.tokenOutId?.let { byId[it] }

            val tokenIn: SwapToken?
            val tokenOut: SwapToken?
            if (storedIn != null || storedOut != null) {
                tokenIn = storedIn
                tokenOut = storedOut
            } else {
                // No swap was ever done — seed the most popular pair.
                tokenIn = SwapPopularTokens.build(all, context = null).firstOrNull()
                tokenOut = tokenIn?.let { SwapPopularTokens.build(all, context = it).firstOrNull() }
            }

            tokenIn?.let { quoteService.setTokenIn(it) }
            tokenOut?.let { quoteService.setTokenOut(it) }
        }
    }

    /** Persist the current pair so it's pre-selected next launch. */
    private fun persistSelectedPair() {
        val state = quoteService.stateFlow.value
        tokenStore.tokenInId = state.tokenIn?.identifier
        tokenStore.tokenOutId = state.tokenOut?.identifier
    }

    /** Fetch USD prices when the pair's CoinGecko ids change; the service caches the rest. */
    private fun refreshPrices(tokenIn: SwapToken?, tokenOut: SwapToken?) {
        val ids = listOfNotNull(tokenIn?.coingeckoId, tokenOut?.coingeckoId).toSet()
        if (ids == pricedIds) return
        pricedIds = ids

        if (ids.isEmpty()) {
            prices = emptyMap()
            uiState = buildState()
            return
        }

        viewModelScope.launch {
            prices = priceService.prices(ids.toList())
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
