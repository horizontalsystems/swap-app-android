package io.horizontalsystems.swapapp.swap.execution

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.swapapp.swap.SwapProvider
import io.horizontalsystems.swapapp.swap.SwapToken
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * Drives the swap execution flow once the user has confirmed a destination address:
 *
 *   1. Register the swap via the chosen [provider] → real deposit address + exact input amount
 *      (+ memo for THORChain), through [SwapDepositRepository.createIntent].
 *   2. Poll the backend for status and advance the tracker through the [SwapStatus] stages
 *      ([SwapDepositRepository.statusUpdates] — still mocked).
 */
class SwapExecutionViewModel(
    private val tokenIn: SwapToken,
    private val tokenOut: SwapToken,
    private val amountIn: BigDecimal,
    private val provider: SwapProvider,
    private val destinationAddress: String,
    private val refundAddress: String?,
    private val repository: SwapDepositRepository,
) : ViewModel() {

    var uiState by mutableStateOf(
        ActiveSwapUiState(
            creatingIntent = true,
            error = null,
            depositAddress = null,
            memo = null,
            paymentUri = null,
            deeplink = null,
            amountIn = amountIn,
            tokenInCode = tokenIn.ticker,
            tokenOutCode = tokenOut.ticker,
            providerTitle = provider.title,
            destinationAddress = destinationAddress,
            status = SwapStatus.AwaitingDeposit,
        )
    )
        private set

    init {
        start()
    }

    fun retry() {
        if (uiState.creatingIntent) return
        uiState = uiState.copy(creatingIntent = true, error = null)
        start()
    }

    private fun start() {
        viewModelScope.launch {
            try {
                val intent = repository.createIntent(
                    tokenIn = tokenIn,
                    tokenOut = tokenOut,
                    amountIn = amountIn,
                    provider = provider,
                    destinationAddress = destinationAddress,
                    refundAddress = refundAddress,
                )
                uiState = uiState.copy(
                    creatingIntent = false,
                    depositAddress = intent.depositAddress,
                    memo = intent.memo,
                    paymentUri = intent.paymentUri,
                    deeplink = intent.deeplink,
                    amountIn = intent.amountIn,
                )

                // Poll for status and walk the tracker forward.
                repository.statusUpdates(intent.reference).collect { status ->
                    uiState = uiState.copy(status = status)
                }
            } catch (e: Throwable) {
                uiState = uiState.copy(
                    creatingIntent = false,
                    error = e.message ?: "Failed to create swap",
                )
            }
        }
    }

    class Factory(
        private val tokenIn: SwapToken,
        private val tokenOut: SwapToken,
        private val amountIn: BigDecimal,
        private val provider: SwapProvider,
        private val destinationAddress: String,
        private val refundAddress: String?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SwapExecutionViewModel(
                tokenIn = tokenIn,
                tokenOut = tokenOut,
                amountIn = amountIn,
                provider = provider,
                destinationAddress = destinationAddress,
                refundAddress = refundAddress,
                repository = SwapDepositRepository(),
            ) as T
        }
    }
}

data class ActiveSwapUiState(
    val creatingIntent: Boolean,
    val error: String?,
    val depositAddress: String?,
    val memo: String?,
    val paymentUri: String?,
    val deeplink: String?,
    val amountIn: BigDecimal,
    val tokenInCode: String,
    val tokenOutCode: String,
    val providerTitle: String,
    val destinationAddress: String,
    val status: SwapStatus,
) {
    val completed: Boolean get() = status == SwapStatus.Completed
}
