package io.horizontalsystems.swapapp.swap

import io.horizontalsystems.swapapp.swap.api.SwapApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Loads and caches the swap token universe from `GET /tokens/all`, and supports local search.
 * Single source of truth for selectable tokens.
 */
class SwapTokenRepository(
    private val api: io.horizontalsystems.swapapp.swap.api.SwapApi = SwapApiClient.api,
) {
    private val mutex = Mutex()
    private var cache: List<SwapToken>? = null

    suspend fun all(): List<SwapToken> {
        cache?.let { return it }
        return mutex.withLock {
            cache ?: withContext(Dispatchers.IO) {
                api.tokens().mapNotNull { SwapToken.fromDto(it) }
            }.also { cache = it }
        }
    }

    /**
     * Tokens matching [query] by ticker / name / identifier. Blank query returns the full list.
     * [exclude] drops a token already chosen on the other side of the swap.
     */
    suspend fun search(query: String, exclude: SwapToken? = null): List<SwapToken> {
        val tokens = all().filter { it.identifier != exclude?.identifier }
        val q = query.trim()
        if (q.isEmpty()) return tokens
        return tokens.filter {
            it.ticker.contains(q, ignoreCase = true) ||
                it.name.contains(q, ignoreCase = true) ||
                it.identifier.contains(q, ignoreCase = true)
        }
    }
}
