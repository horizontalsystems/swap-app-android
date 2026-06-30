package io.horizontalsystems.swapapp.swap

import android.util.Log
import io.horizontalsystems.swapapp.swap.api.MemolessApi
import io.horizontalsystems.swapapp.swap.api.MemolessApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Loads and caches the set of assets the memoless service supports (`GET /memoless/api/v1/assets`,
 * `status == "Available"`). Used to decide whether THORChain can offer a memo-free deposit for a
 * given pair — mirroring swap-bot's `memoless_assets` table. Identifiers are upper-cased to match
 * the swap API's [SwapToken.identifier] format.
 */
class MemolessAssetRepository(
    private val api: MemolessApi = MemolessApiClient.api,
) {
    private val mutex = Mutex()
    @Volatile
    private var cache: Set<String>? = null

    /**
     * Memoless-supported asset identifiers (UPPER-cased), or `null` if the list couldn't be
     * fetched — callers should treat `null` as "unknown" and not gate on it.
     */
    suspend fun supportedAssets(): Set<String>? {
        cache?.let { return it }
        return mutex.withLock {
            cache ?: try {
                withContext(Dispatchers.IO) {
                    api.assets().assets.orEmpty()
                        .filter { it.status.equals("Available", ignoreCase = true) }
                        .mapNotNull { it.asset?.uppercase() }
                        .toSet()
                }.also { cache = it }
            } catch (e: Throwable) {
                Log.e(TAG, "failed to fetch memoless assets", e)
                null
            }
        }
    }

    companion object {
        private const val TAG = "MemolessAssets"
    }
}
