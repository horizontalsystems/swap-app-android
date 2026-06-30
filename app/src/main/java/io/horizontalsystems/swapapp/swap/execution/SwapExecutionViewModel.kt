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
 *   1. Commit the swap via the chosen [provider] → real deposit address + exact input amount
 *      (+ attachment for chains that need a tag/memo), through [SwapDepositRepository.createIntent].
 *   2. Poll `POST /v2/track` for live status and advance the tracker through the [SwapStatus] stages
 *      ([SwapDepositRepository.statusUpdates]).
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
            attachmentValue = null,
            attachmentLabel = null,
            paymentUri = null,
            deeplink = null,
            amountIn = amountIn,
            tokenInCode = tokenIn.ticker,
            tokenOutCode = tokenOut.ticker,
            providerTitle = provider.title,
            destinationAddress = destinationAddress,
            status = SwapStatus.NotStarted,
            pauseReason = null,
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
                    attachmentValue = intent.attachmentValue,
                    attachmentLabel = intent.attachmentLabel,
                    paymentUri = intent.paymentUri,
                    deeplink = intent.deeplink,
                    amountIn = intent.amountIn,
                )

                // Poll for live status and walk the tracker forward.
                repository.statusUpdates(intent.uuid).collect { update ->
                    uiState = uiState.copy(status = update.status, pauseReason = update.pauseReason)
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
    /** Destination tag / memo that must accompany the send (chains like XRP, RUNE). */
    val attachmentValue: String?,
    val attachmentLabel: String?,
    val paymentUri: String?,
    val deeplink: String?,
    val amountIn: BigDecimal,
    val tokenInCode: String,
    val tokenOutCode: String,
    val providerTitle: String,
    val destinationAddress: String,
    val status: SwapStatus,
    val pauseReason: String?,
) {
    val completed: Boolean get() = status == SwapStatus.Completed

    /** Terminal failure — funds refunded or the swap failed outright. */
    val failed: Boolean get() = status == SwapStatus.Refunded || status == SwapStatus.Failed
}
