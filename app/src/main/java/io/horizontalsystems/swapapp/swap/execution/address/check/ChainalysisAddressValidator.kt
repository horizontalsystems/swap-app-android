package io.horizontalsystems.swapapp.swap.execution.address.check

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Chainalysis sanctions screening, ported from the reference wallet's `ChainalysisAddressValidator`.
 * Scaffold: without an API key [isEnabled] is false and the sanction check is skipped, until [apiKey]
 * is supplied via BuildConfig. An address is clear when the API returns no `identifications`.
 */
class ChainalysisAddressValidator(
    private val baseUrl: String,
    private val apiKey: String,
) {
    val isEnabled: Boolean get() = apiKey.isNotBlank()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS) // real per-check bound; see EvmJsonRpc
            .build()
    }

    /** Returns true if the address is not on a sanctions list. Throws on network failure. */
    suspend fun isClear(address: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/address/" + address.trim())
            .addHeader("Accept", "application/json")
            .addHeader("X-API-KEY", apiKey)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: throw RuntimeException("Chainalysis empty response")
            if (!response.isSuccessful) throw RuntimeException("Chainalysis HTTP ${response.code}")
            val identifications = JSONObject(responseBody).optJSONArray("identifications")
            identifications == null || identifications.length() == 0
        }
    }
}