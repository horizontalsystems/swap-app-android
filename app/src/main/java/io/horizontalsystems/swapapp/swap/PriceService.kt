package io.horizontalsystems.swapapp.swap

import android.util.Log
import io.horizontalsystems.swapapp.swap.api.PriceApi
import io.horizontalsystems.swapapp.swap.api.PriceApiClient
import java.math.BigDecimal

/**
 * USD spot prices keyed by CoinGecko id, mirroring the swap-bot's price service: a single
 * blocksdecoded `/v1/coins` call backed by a short in-memory cache. Missing prices come back absent
 * from the result so callers can simply omit the fiat value.
 */
class PriceService(
    private val api: PriceApi = PriceApiClient.api,
) {
    private data class Entry(val price: BigDecimal, val fetchedAt: Long)

    private val cache = mutableMapOf<String, Entry>()

    /** Prices for [ids]; fetches only the ids whose cache entry is missing or stale. */
    suspend fun prices(ids: List<String>): Map<String, BigDecimal> {
        val now = System.currentTimeMillis()
        val stale = ids.filter { id ->
            val cached = cache[id]
            cached == null || now - cached.fetchedAt >= CACHE_TTL_MS
        }

        if (stale.isNotEmpty()) {
            try {
                api.coins(uids = stale.joinToString(",")).forEach { coin ->
                    val uid = coin.uid ?: return@forEach
                    val price = coin.price ?: return@forEach
                    cache[uid] = Entry(BigDecimal.valueOf(price), now)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "price fetch failed", e)
            }
        }

        return ids.mapNotNull { id -> cache[id]?.let { id to it.price } }.toMap()
    }

    suspend fun price(id: String?): BigDecimal? =
        if (id == null) null else prices(listOf(id))[id]

    companion object {
        private const val TAG = "PriceService"
        private const val CACHE_TTL_MS = 300_000L
    }
}
