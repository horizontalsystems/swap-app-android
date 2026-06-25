package io.horizontalsystems.swapapp.swap

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Token selector backed by the swap API token list ([SwapTokenRepository]). Searches locally over
 * the cached list. Accountless — no balances, no wallet.
 */
class SwapSelectCoinViewModel(
    private val otherSelectedToken: SwapToken?,
    private val repository: SwapTokenRepository = SwapTokenRepository(),
) : ViewModel() {

    private var query = ""
    private var searchJob: Job? = null

    var uiState by mutableStateOf(
        SwapSelectCoinUiState(query = "", loading = true, error = false, items = emptyList())
    )
        private set

    init {
        loadItems()
    }

    fun setQuery(q: String) {
        query = q
        loadItems()
    }

    private fun loadItems() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                val items = repository.search(query, exclude = otherSelectedToken)
                    .map { it.toViewItem() }
                uiState = SwapSelectCoinUiState(query, loading = false, error = false, items = items)
            } catch (e: Throwable) {
                android.util.Log.e("SwapSelectCoin", "token load failed", e)
                uiState = uiState.copy(query = query, loading = false, error = true)
            }
        }
    }

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
    val items: List<TokenViewItem>,
)
