package io.horizontalsystems.swapapp.swap

import android.content.Context

/**
 * Persists the user's last-selected swap pair (the "You pay" / "You get" tokens) in
 * SharedPreferences, so the swap screen can pre-select them on the next launch. Only the stable
 * [SwapToken.identifier] is stored; the token itself is re-resolved from the swap API token
 * universe ([SwapTokenRepository]) so it always reflects current metadata.
 */
class SwapTokenStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var tokenInId: String?
        get() = prefs.getString(KEY_TOKEN_IN, null)
        set(value) {
            prefs.edit().putString(KEY_TOKEN_IN, value).apply()
        }

    var tokenOutId: String?
        get() = prefs.getString(KEY_TOKEN_OUT, null)
        set(value) {
            prefs.edit().putString(KEY_TOKEN_OUT, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "swap_selection"
        private const val KEY_TOKEN_IN = "token_in_id"
        private const val KEY_TOKEN_OUT = "token_out_id"
    }
}
