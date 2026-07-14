package io.horizontalsystems.swapapp.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

/**
 * Debug-only toggles, persisted in their own prefs file and exposed as Compose state so screens
 * react to changes immediately. Only reachable from the UI in debug builds (the settings menu item
 * is gated on `BuildConfig.DEBUG`); release builds always see the defaults.
 */
object DebugSettings {
    private const val PREFS_NAME = "debug_settings"
    private const val KEY_SHOW_ROUTE_PROVIDER_NAMES = "show_route_provider_names"

    private var prefs: SharedPreferences? = null

    private val showRouteProviderNamesState = mutableStateOf(false)

    /** Show each route's provider name on the route-selection screen. */
    val showRouteProviderNames: Boolean get() = showRouteProviderNamesState.value

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        showRouteProviderNamesState.value = p.getBoolean(KEY_SHOW_ROUTE_PROVIDER_NAMES, false)
    }

    fun setShowRouteProviderNames(value: Boolean) {
        showRouteProviderNamesState.value = value
        prefs?.edit()?.putBoolean(KEY_SHOW_ROUTE_PROVIDER_NAMES, value)?.apply()
    }
}