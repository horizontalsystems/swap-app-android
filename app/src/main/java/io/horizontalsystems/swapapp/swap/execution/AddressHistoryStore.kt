package io.horizontalsystems.swapapp.swap.execution

import android.content.Context

/**
 * Remembers the last few addresses the user entered, per token, so the address-input screens
 * (recipient / refund) can suggest them next time the same token's page is opened. Backed by
 * SharedPreferences; keyed on [io.horizontalsystems.swapapp.swap.SwapToken.identifier].
 *
 * Addresses are stored newest-first, de-duplicated, and capped at [MAX] entries. They're joined by
 * '\n' — safe, since no supported address format contains a newline.
 */
class AddressHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Recently used addresses for [tokenId], newest first. */
    fun recent(tokenId: String): List<String> =
        prefs.getString(key(tokenId), null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    /** Record [address] as the most recently used for [tokenId], keeping only the latest [MAX]. */
    fun add(tokenId: String, address: String) {
        val trimmed = address.trim()
        if (trimmed.isEmpty()) return
        val updated = (listOf(trimmed) + recent(tokenId))
            .distinct()
            .take(MAX)
        prefs.edit().putString(key(tokenId), updated.joinToString(SEPARATOR)).apply()
    }

    private fun key(tokenId: String) = "$KEY_PREFIX$tokenId"

    companion object {
        private const val PREFS_NAME = "address_history"
        private const val KEY_PREFIX = "addresses_"
        private const val SEPARATOR = "\n"
        private const val MAX = 5
    }
}
