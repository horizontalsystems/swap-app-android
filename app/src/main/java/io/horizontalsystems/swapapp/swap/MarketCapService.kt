package io.horizontalsystems.swapapp.swap

import android.util.Log
import io.horizontalsystems.swapapp.swap.api.PriceApi
import io.horizontalsystems.swapapp.swap.api.PriceApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Market-cap ranks (CoinGecko id → rank) from the blocksdecoded catalog — the same backend
 * MarketKit syncs from. One call fetches the whole catalog (~66 KB), cached for the process
 * lifetime. A failed fetch is not cached, so the next caller retries.
 *
 * This is the lightweight stand-in for MarketKit's `marketCapRank`: the swap app has no wallet,
 * database or sync managers, so a single ranked lookup is all the "Top tokens" list needs.
 */
class MarketCapService(
    private val api: PriceApi = PriceApiClient.api,
) {
    private val mutex = Mutex()
    private var cache: Map<String, Int>? = null

    /** Map of CoinGecko id → market-cap rank. Empty if the catalog couldn't be fetched. */
    suspend fun ranks(): Map<String, Int> {
        cache?.let { return it }
        return mutex.withLock {
            cache ?: withContext(Dispatchers.IO) {
                try {
                    api.marketInfo().mapNotNull { dto ->
                        val uid = dto.uid ?: return@mapNotNull null
                        val rank = dto.rank ?: return@mapNotNull null
                        uid to rank
                    }.toMap()
                } catch (e: Throwable) {
                    Log.e(TAG, "market cap rank fetch failed", e)
                    emptyMap()
                }
            }.also { if (it.isNotEmpty()) cache = it }
        }
    }

    companion object {
        private const val TAG = "MarketCapService"
    }
}
