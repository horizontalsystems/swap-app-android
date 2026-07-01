package io.horizontalsystems.swapapp.swap.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.swapapp.swap.execution.SwapDepositRepository
import io.horizontalsystems.swapapp.swap.execution.SwapLeg
import io.horizontalsystems.swapapp.swap.execution.SwapStatus
import io.horizontalsystems.swapapp.swap.execution.SwapTrackUpdate
import kotlinx.coroutines.launch

/**
 * Backs the [SwapInfoScreen] for one recorded swap. Seeds from the stored [SwapRecord] and, while the
 * swap isn't in a terminal state, re-polls `POST /v2/track` so a still-in-progress swap keeps
 * advancing when reopened — mirroring each update back into [SwapHistoryStore].
 */
class SwapInfoViewModel(
    private val uuid: String,
    private val history: SwapHistoryStore,
    private val repository: SwapDepositRepository,
) : ViewModel() {

    var uiState by mutableStateOf(
        SwapInfoUiState(
            record = history.get(uuid),
            status = history.get(uuid)?.swapStatus ?: SwapStatus.Unknown,
            legs = emptyList(),
        )
    )
        private set

    init {
        if (uiState.record != null) {
            viewModelScope.launch {
                if (uiState.status.isTerminal) {
                    // Terminal already — one read to fetch the on-chain legs for the "View" links,
                    // without pointless polling.
                    apply(repository.trackOnce(uuid))
                } else {
                    // In progress — keep polling so the tracker advances while open.
                    repository.statusUpdates(uuid).collect(::apply)
                }
            }
        }
    }

    /** Apply a live update, but never downgrade a known status to [SwapStatus.Unknown] (e.g. offline). */
    private fun apply(update: SwapTrackUpdate) {
        if (update.status == SwapStatus.Unknown && uiState.status != SwapStatus.Unknown) {
            if (update.legs.isNotEmpty()) uiState = uiState.copy(legs = update.legs)
            return
        }
        uiState = uiState.copy(status = update.status, legs = update.legs)
        history.updateStatus(uuid, update.status)
    }

    class Factory(
        private val uuid: String,
        private val history: SwapHistoryStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SwapInfoViewModel(uuid, history, SwapDepositRepository()) as T
        }
    }
}

data class SwapInfoUiState(
    val record: SwapRecord?,
    val status: SwapStatus,
    val legs: List<SwapLeg>,
)
