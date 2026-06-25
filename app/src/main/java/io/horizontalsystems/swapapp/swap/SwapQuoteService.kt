package io.horizontalsystems.swapapp.swap

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * Quote engine backed by the swap API. Holds the current token pair + amount and, whenever they
 * change, fetches routes via [SwapQuoteRepository] (a single `/quote` call returning every
 * provider's route) and exposes the best/selected one through [stateFlow]. Accountless — no wallet,
 * balance or signing.
 */
class SwapQuoteService(
    private val repository: SwapQuoteRepository = SwapQuoteRepository(),
) {
    private val tag = "SwapQuoteService"

    private var amountIn: BigDecimal? = null
    private var tokenIn: SwapToken? = null
    private var tokenOut: SwapToken? = null
    private var quoting = false
    private var quotes: List<SwapProviderQuote> = listOf()
    private var preferredProvider: SwapProvider? = null
    private var error: Throwable? = null
    private var quote: SwapProviderQuote? = null

    private val _stateFlow = MutableStateFlow(buildState())
    val stateFlow = _stateFlow.asStateFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var quotingJob: Job? = null

    fun start() {
        runQuotation()
    }

    private fun buildState() = State(
        amountIn = amountIn,
        tokenIn = tokenIn,
        tokenOut = tokenOut,
        quoting = quoting,
        quotes = quotes,
        preferredProvider = preferredProvider,
        quote = quote,
        error = error,
    )

    private fun emitState() {
        _stateFlow.update { buildState() }
    }

    private fun runQuotation() {
        quotingJob?.cancel()
        quoting = false
        quotes = listOf()
        quote = null
        error = null

        val tokenIn = tokenIn
        val tokenOut = tokenOut
        val amountIn = amountIn

        if (tokenIn == null || tokenOut == null) {
            emitState()
            return
        }

        val providers = repository.supportedProviders(tokenIn, tokenOut)
        if (providers.isEmpty()) {
            error = NoSupportedSwapProvider()
            emitState()
            return
        }

        if (amountIn == null || amountIn <= BigDecimal.ZERO) {
            emitState()
            return
        }

        quoting = true
        emitState()

        quotingJob = coroutineScope.launch {
            try {
                val fetched = repository.quote(tokenIn, tokenOut, amountIn, providers)

                if (preferredProvider != null && fetched.none { it.provider == preferredProvider }) {
                    preferredProvider = null
                }

                quotes = fetched
                quote = when {
                    fetched.isEmpty() -> null
                    else -> preferredProvider
                        ?.let { p -> fetched.find { it.provider == p } }
                        ?: fetched.first()
                }
                error = if (fetched.isEmpty()) SwapRouteNotFound() else null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(tag, "quote error", e)
                quotes = listOf()
                quote = null
                error = e
            } finally {
                quoting = false
                emitState()
            }
        }
    }

    fun setAmount(v: BigDecimal?) {
        if (amountIn == v) return
        amountIn = v
        preferredProvider = null
        runQuotation()
    }

    fun setTokenIn(token: SwapToken) {
        if (tokenIn == token) return
        preferredProvider = null
        if (tokenOut == token) tokenOut = tokenIn
        tokenIn = token
        runQuotation()
    }

    fun setTokenOut(token: SwapToken) {
        if (tokenOut == token) return
        preferredProvider = null
        if (tokenIn == token) tokenIn = tokenOut
        tokenOut = token
        runQuotation()
    }

    fun switchPairs() {
        val tmp = tokenIn
        tokenIn = tokenOut
        tokenOut = tmp
        amountIn = quote?.amountOut
        runQuotation()
    }

    fun selectQuote(selected: SwapProviderQuote) {
        preferredProvider = selected.provider
        quote = selected
        emitState()
    }

    data class State(
        val amountIn: BigDecimal?,
        val tokenIn: SwapToken?,
        val tokenOut: SwapToken?,
        val quoting: Boolean,
        val quotes: List<SwapProviderQuote>,
        val preferredProvider: SwapProvider?,
        val quote: SwapProviderQuote?,
        val error: Throwable?,
    )
}
