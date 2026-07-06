package io.horizontalsystems.swapapp.swap.execution

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.swapapp.swap.SwapProvider
import io.horizontalsystems.swapapp.swap.SwapToken
import io.horizontalsystems.swapapp.swap.formatFiat
import io.horizontalsystems.swapapp.swap.history.RecordToken
import io.horizontalsystems.swapapp.swap.history.SwapHistoryStore
import io.horizontalsystems.swapapp.swap.history.SwapRecord
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
    private val amountOut: BigDecimal?,
    private val fiatIn: BigDecimal?,
    private val fiatOut: BigDecimal?,
    private val provider: SwapProvider,
    private val destinationAddress: String,
    private val refundAddress: String?,
    private val repository: SwapDepositRepository,
    private val history: SwapHistoryStore,
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
            expiresAtMillis = null,
            trackUrl = null,
            amountIn = amountIn,
            amountOut = amountOut,
            fiatOut = fiatOut,
            tokenInCode = tokenIn.ticker,
            tokenInNetwork = tokenIn.networkName,
            tokenInLogo = tokenIn.logoUrl,
            tokenOutCode = tokenOut.ticker,
            tokenOutNetwork = tokenOut.networkName,
            tokenOutLogo = tokenOut.logoUrl,
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
                    expiresAtMillis = intent.expiresAtMillis,
                    trackUrl = intent.trackUrl,
                    amountIn = intent.amountIn,
                )

                // The swap is now committed (real deposit address + uuid) — record it so it shows in
                // history even after this screen is gone.
                history.record(buildRecord(intent))

                // Poll for live status and walk the tracker forward, mirroring status into history.
                repository.statusUpdates(intent.uuid).collect { update ->
                    uiState = uiState.copy(status = update.status, pauseReason = update.pauseReason)
                    history.updateStatus(intent.uuid, update.status)
                }
            } catch (e: Throwable) {
                uiState = uiState.copy(
                    creatingIntent = false,
                    error = e.message ?: "Failed to create swap",
                )
            }
        }
    }

    private fun buildRecord(intent: SwapIntent): SwapRecord = SwapRecord(
        uuid = intent.uuid,
        createdAt = System.currentTimeMillis(),
        providerId = provider.id,
        providerTitle = provider.title,
        tokenIn = tokenIn.toRecordToken(),
        tokenOut = tokenOut.toRecordToken(),
        amountIn = intent.amountIn.stripTrailingZeros().toPlainString(),
        amountOut = amountOut?.stripTrailingZeros()?.toPlainString(),
        fiatIn = fiatIn?.let { formatFiat(it) },
        fiatOut = fiatOut?.let { formatFiat(it) },
        status = SwapStatus.NotStarted.name,
        destinationAddress = destinationAddress,
        depositAddress = intent.depositAddress,
        attachmentValue = intent.attachmentValue,
        attachmentLabel = intent.attachmentLabel,
        paymentUri = intent.paymentUri,
        deeplink = intent.deeplink,
        expiresAtMillis = intent.expiresAtMillis,
        trackUrl = intent.trackUrl,
    )

    class Factory(
        private val tokenIn: SwapToken,
        private val tokenOut: SwapToken,
        private val amountIn: BigDecimal,
        private val amountOut: BigDecimal?,
        private val fiatIn: BigDecimal?,
        private val fiatOut: BigDecimal?,
        private val provider: SwapProvider,
        private val destinationAddress: String,
        private val refundAddress: String?,
        private val history: SwapHistoryStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SwapExecutionViewModel(
                tokenIn = tokenIn,
                tokenOut = tokenOut,
                amountIn = amountIn,
                amountOut = amountOut,
                fiatIn = fiatIn,
                fiatOut = fiatOut,
                provider = provider,
                destinationAddress = destinationAddress,
                refundAddress = refundAddress,
                repository = SwapDepositRepository(),
                history = history,
            ) as T
        }
    }
}

private fun SwapToken.toRecordToken() = RecordToken(
    ticker = ticker,
    name = name,
    network = networkName,
    logoUrl = logoUrl,
    chainId = chainId,
)

data class ActiveSwapUiState(
    val creatingIntent: Boolean,
    val error: String?,
    val depositAddress: String?,
    /** Destination tag / memo that must accompany the send (chains like XRP, RUNE). */
    val attachmentValue: String?,
    val attachmentLabel: String?,
    val paymentUri: String?,
    val deeplink: String?,
    /** Order expiry (epoch ms) from the backend, or null when the provider sets none. */
    val expiresAtMillis: Long?,
    /** `swap.unstoppable.money/track` page for this order, when it could be built. */
    val trackUrl: String?,
    val amountIn: BigDecimal,
    val amountOut: BigDecimal?,
    val fiatOut: BigDecimal?,
    val tokenInCode: String,
    val tokenInNetwork: String,
    val tokenInLogo: String?,
    val tokenOutCode: String,
    val tokenOutNetwork: String,
    val tokenOutLogo: String?,
    val providerTitle: String,
    val destinationAddress: String,
    val status: SwapStatus,
    val pauseReason: String?,
) {
    val completed: Boolean get() = status == SwapStatus.Completed

    /** Terminal failure — funds refunded or the swap failed outright. */
    val failed: Boolean get() = status == SwapStatus.Refunded || status == SwapStatus.Failed
}
