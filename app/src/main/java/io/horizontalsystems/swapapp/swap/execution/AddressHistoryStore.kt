package io.horizontalsystems.swapapp.swap.execution

import android.content.Context

/** A previously used address and when it was last used ([usedAt] is null for pre-timestamp entries). */
data class RecentAddress(val address: String, val usedAt: Long?)

/**
 * Remembers the last few addresses the user entered, per token, so the address-input screens
 * (recipient / refund) can suggest them next time the same token's page is opened. Backed by
 * SharedPreferences; keyed on [io.horizontalsystems.swapapp.swap.SwapToken.identifier].
 *
 * Addresses are stored newest-first, de-duplicated, and capped at [MAX] entries. Entries are joined
 * by '\n'; within an entry the address and its last-used timestamp are joined by [FIELD_SEPARATOR]
 * (a control char) — both safe, since no supported address format contains either.
 */
class AddressHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Recently used addresses for [tokenId], newest first. */
    fun recent(tokenId: String): List<RecentAddress> =
        prefs.getString(key(tokenId), null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?.map { entry ->
                val parts = entry.split(FIELD_SEPARATOR)
                RecentAddress(parts[0], parts.getOrNull(1)?.toLongOrNull())
            }
            ?: emptyList()

    /** Record [address] as the most recently used for [tokenId], keeping only the latest [MAX]. */
    fun add(tokenId: String, address: String) {
        val trimmed = address.trim()
        if (trimmed.isEmpty()) return
        val now = System.currentTimeMillis()
        val updated = (listOf(RecentAddress(trimmed, now)) + recent(tokenId))
            .distinctBy { it.address }
            .take(MAX)
        val serialized = updated.joinToString(SEPARATOR) { "${it.address}$FIELD_SEPARATOR${it.usedAt ?: ""}" }
        prefs.edit().putString(key(tokenId), serialized).apply()
    }

    private fun key(tokenId: String) = "$KEY_PREFIX$tokenId"

    companion object {
        private const val PREFS_NAME = "address_history"
        private const val KEY_PREFIX = "addresses_"
        private const val SEPARATOR = "\n"
        private const val FIELD_SEPARATOR = "\u0001"
        private const val MAX = 5
    }
}
