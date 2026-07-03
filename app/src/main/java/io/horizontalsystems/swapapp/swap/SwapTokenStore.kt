package io.horizontalsystems.swapapp.swap

import android.content.Context
import com.google.gson.Gson

/**
 * Persists the user's last-selected swap pair (the "You pay" / "You get" tokens) in
 * SharedPreferences, so the swap screen can pre-select them on the next launch. Besides the stable
 * [SwapToken.identifier], a full JSON snapshot of each token is stored so cold start can show the
 * pair instantly — without first downloading the swap API token universe. The snapshot is later
 * re-resolved against [SwapTokenRepository] to pick up current metadata.
 */
class SwapTokenStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

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

    /** Snapshot of the last "You pay" token; null on first run or if the stored JSON is unreadable. */
    var tokenIn: SwapToken?
        get() = readToken(KEY_TOKEN_IN_SNAPSHOT)
        set(value) = writeToken(KEY_TOKEN_IN_SNAPSHOT, value)

    /** Snapshot of the last "You get" token; null on first run or if the stored JSON is unreadable. */
    var tokenOut: SwapToken?
        get() = readToken(KEY_TOKEN_OUT_SNAPSHOT)
        set(value) = writeToken(KEY_TOKEN_OUT_SNAPSHOT, value)

    private fun readToken(key: String): SwapToken? {
        val json = prefs.getString(key, null) ?: return null
        // We wrote this JSON ourselves, but guard against a corrupted/incompatible blob anyway.
        return runCatching { gson.fromJson(json, SwapToken::class.java) }.getOrNull()
    }

    private fun writeToken(key: String, token: SwapToken?) {
        prefs.edit().putString(key, token?.let { gson.toJson(it) }).apply()
    }

    companion object {
        private const val PREFS_NAME = "swap_selection"
        private const val KEY_TOKEN_IN = "token_in_id"
        private const val KEY_TOKEN_OUT = "token_out_id"
        private const val KEY_TOKEN_IN_SNAPSHOT = "token_in_snapshot"
        private const val KEY_TOKEN_OUT_SNAPSHOT = "token_out_snapshot"
    }
}
