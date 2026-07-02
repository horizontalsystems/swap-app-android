package io.horizontalsystems.swapapp.swap.execution.address.check

import io.horizontalsystems.swapapp.swap.SwapToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * HashDit address risk score, ported from the reference wallet's `HashDitAddressValidator`. Scaffold:
 * the swap app ships without a HashDit API key, so [supports] returns false (and the check is skipped)
 * until [apiKey] is supplied via BuildConfig. Score >= 60 is treated as safe, matching the wallet.
 */
class HashDitAddressValidator(
    private val baseUrl: String,
    private val apiKey: String,
) {
    private val supportedChains = setOf("ETH", "BSC", "BNB", "MATIC", "POL", "POLYGON")
    private val chainIds = mapOf(
        "ETH" to "1", "BSC" to "56", "BNB" to "56", "MATIC" to "137", "POL" to "137", "POLYGON" to "137"
    )

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS) // real per-check bound; see EvmJsonRpc
            .build()
    }

    fun supports(token: SwapToken): Boolean =
        apiKey.isNotBlank() && token.chain.uppercase() in supportedChains

    /** Returns true if the address scores as safe. Throws on network failure (inconclusive). */
    suspend fun isClear(address: String, token: SwapToken): Boolean = withContext(Dispatchers.IO) {
        val chainId = chainIds[token.chain.uppercase()] ?: return@withContext true

        var score = requestScore(chainId, address.trim())
        // The API may answer "in progress" — retry once after a short delay, as the wallet does.
        if (score == null) {
            delay(1000L)
            score = requestScore(chainId, address.trim()) ?: throw RuntimeException("HashDit in progress")
        }
        score >= 60
    }

    /** Returns the overall score, or null if the API reports the check is still in progress. */
    private fun requestScore(chainId: String, address: String): Int? {
        val body = JSONObject().apply {
            put("chainId", chainId)
            put("address", address)
        }
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/address-security-v2")
            .addHeader("Accept", "application/json")
            .addHeader("X-API-Key", apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: throw RuntimeException("HashDit empty response")
            if (!response.isSuccessful) throw RuntimeException("HashDit HTTP ${response.code}")
            val json = JSONObject(responseBody)
            if (json.optString("status") == "in progress") return null
            // A 200 without a parseable score (error/quota payload, schema change) is inconclusive —
            // throwing lets AddressCheckManager skip the check instead of hard-blocking on score 0.
            return json.optJSONObject("data")?.optString("overall_score")?.toIntOrNull()
                ?: throw RuntimeException("HashDit response missing overall_score")
        }
    }
}