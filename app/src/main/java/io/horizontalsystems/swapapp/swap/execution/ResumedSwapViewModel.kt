package io.horizontalsystems.swapapp.swap.execution

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.swapapp.swap.history.SwapHistoryStore
import io.horizontalsystems.swapapp.swap.history.SwapRecord
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * Re-drives [ActiveSwapTrackingScreen] for a swap reopened from history while it's still awaiting
 * its deposit. No new intent is created: the deposit details were snapshotted into the [SwapRecord]
 * when the swap was committed, so the state is rebuilt from the record and polling of `POST
 * /v2/track` resumes, mirroring each update back into [SwapHistoryStore].
 */
class ResumedSwapViewModel(
    private val uuid: String,
    private val history: SwapHistoryStore,
    private val repository: SwapDepositRepository,
) : ViewModel() {

    var uiState by mutableStateOf(initialState(history.get(uuid)))
        private set

    init {
        if (!uiState.status.isTerminal && uiState.error == null) {
            viewModelScope.launch {
                repository.statusUpdates(uuid).collect { update ->
                    // Never downgrade a known status to Unknown (e.g. a transient network error).
                    if (update.status == SwapStatus.Unknown && uiState.status != SwapStatus.Unknown) {
                        return@collect
                    }
                    uiState = uiState.copy(status = update.status, pauseReason = update.pauseReason)
                    history.updateStatus(uuid, update.status)
                }
            }
        }
    }

    private fun initialState(record: SwapRecord?): ActiveSwapUiState = ActiveSwapUiState(
        creatingIntent = false,
        // Callers only open this screen for records with stored deposit details; this is defensive.
        error = if (record?.depositAddress == null) "Deposit details for this swap are no longer available." else null,
        depositAddress = record?.depositAddress,
        attachmentValue = record?.attachmentValue,
        attachmentLabel = record?.attachmentLabel,
        paymentUri = record?.paymentUri,
        deeplink = record?.deeplink,
        expiresAtMillis = record?.expiresAtMillis,
        trackUrl = record?.trackUrl,
        amountIn = record?.amountIn?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
        amountOut = record?.amountOut?.toBigDecimalOrNull(),
        // record.fiatOut is stored pre-formatted for the history list, so it can't feed the
        // BigDecimal field here; the header simply omits the fiat subtitle when resumed.
        fiatOut = null,
        tokenInCode = record?.tokenIn?.ticker ?: "",
        tokenInNetwork = record?.tokenIn?.network ?: "",
        tokenInLogo = record?.tokenIn?.logoUrl,
        tokenOutCode = record?.tokenOut?.ticker ?: "",
        tokenOutNetwork = record?.tokenOut?.network ?: "",
        tokenOutLogo = record?.tokenOut?.logoUrl,
        providerTitle = record?.providerTitle ?: "",
        destinationAddress = record?.destinationAddress ?: "",
        status = record?.swapStatus ?: SwapStatus.Unknown,
        pauseReason = null,
    )

    class Factory(
        private val uuid: String,
        private val history: SwapHistoryStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ResumedSwapViewModel(uuid, history, SwapDepositRepository()) as T
        }
    }
}
