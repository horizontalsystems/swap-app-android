package io.horizontalsystems.swapapp.swap

import android.util.Log
import io.horizontalsystems.swapapp.swap.api.SwapApiClient
import io.horizontalsystems.swapapp.swap.api.SwapTokenDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Loads and caches the swap token universe and supports local search. v2 has no global token list,
 * so the universe is built by unioning each transfer provider's `GET /tokens?provider=` list; each
 * token records the set of (transfer) providers whose list contained it. Single source of truth for
 * selectable tokens.
 */
class SwapTokenRepository(
    private val api: io.horizontalsystems.swapapp.swap.api.SwapApi = SwapApiClient.api,
) {
    private val mutex = Mutex()
    private var cache: List<SwapToken>? = null

    suspend fun all(): List<SwapToken> {
        cache?.let { return it }
        return mutex.withLock {
            cache ?: load().also { cache = it }
        }
    }

    /**
     * Fetch the transfer-only provider set (`executionType == "transfer"`, not suspended) and union
     * their token lists. Providers are read dynamically — no hardcoded allow-list. Per-provider
     * fetches run in parallel; a provider whose list fails to load is skipped.
     */
    private suspend fun load(): List<SwapToken> = withContext(Dispatchers.IO) {
        val providers = transferProviders()
        if (providers.isEmpty()) return@withContext emptyList()

        // identifier -> (first DTO seen, accumulated providers that support it)
        val byId = LinkedHashMap<String, Pair<SwapTokenDto, MutableSet<String>>>()

        coroutineScope {
            providers.map { provider ->
                async {
                    provider to runCatching { api.tokens(provider).tokens.orEmpty() }
                        .onFailure { Log.e(TAG, "tokens fetch failed for $provider", it) }
                        .getOrDefault(emptyList())
                }
            }.awaitAll()
        }.forEach { (provider, tokens) ->
            tokens.forEach { dto ->
                val id = dto.identifier ?: return@forEach
                val entry = byId.getOrPut(id) { dto to linkedSetOf() }
                entry.second.add(provider)
            }
        }

        byId.values.mapNotNull { (dto, providers) -> SwapToken.fromDto(dto, providers.toList()) }
    }

    private suspend fun transferProviders(): List<String> =
        runCatching {
            api.providers()
                .filter { it.executionType.equals("transfer", ignoreCase = true) && it.suspended != true }
                .mapNotNull { it.provider }
        }.onFailure { Log.e(TAG, "providers fetch failed", it) }
            .getOrDefault(emptyList())

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

    companion object {
        private const val TAG = "SwapTokenRepository"
    }
}
