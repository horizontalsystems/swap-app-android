package io.horizontalsystems.swapapp.swap.execution.address.check

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Tiny JSON-RPC `eth_call` client over the OkHttp already on the classpath — the swap app has no
 * ethereumkit, so this stands in for `EthereumKit.call`. Maps the swap API's chain code to a public
 * RPC endpoint. Read-only `eth_call` at "latest" is all the blacklist check needs.
 */
object EvmJsonRpc {

    /** Public JSON-RPC endpoints keyed by the swap API's uppercase chain code. */
    private val RPC_URLS = mapOf(
        "ETH" to "https://ethereum-rpc.publicnode.com",
        "BSC" to "https://bsc-rpc.publicnode.com",
        "BNB" to "https://bsc-rpc.publicnode.com",
        "MATIC" to "https://polygon-bor-rpc.publicnode.com",
        "POL" to "https://polygon-bor-rpc.publicnode.com",
        "POLYGON" to "https://polygon-bor-rpc.publicnode.com",
        "OP" to "https://optimism-rpc.publicnode.com",
        "OPTIMISM" to "https://optimism-rpc.publicnode.com",
        "ARB" to "https://arbitrum-one-rpc.publicnode.com",
        "ARBITRUM" to "https://arbitrum-one-rpc.publicnode.com",
        "BASE" to "https://base-rpc.publicnode.com",
        "AVAX" to "https://avalanche-c-chain-rpc.publicnode.com",
        "ZKSYNC" to "https://mainnet.era.zksync.io",
    )

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            // Bounds the whole call — the blocking execute() can't be interrupted by coroutine
            // cancellation, so this is what actually caps AddressCheckManager's per-check wait.
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    fun supportsChain(chainCode: String): Boolean = RPC_URLS.containsKey(chainCode.uppercase())

    /**
     * Performs `eth_call` to [to] with call [data] and returns the raw hex result. Throws on network
     * failure or a JSON-RPC error, so callers can treat those as "couldn't verify" (inconclusive).
     */
    suspend fun call(chainCode: String, to: String, data: String): String = withContext(Dispatchers.IO) {
        val url = RPC_URLS[chainCode.uppercase()] ?: throw IllegalArgumentException("No RPC for $chainCode")

        val body = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "eth_call")
            put("params", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("to", to)
                    put("data", data)
                })
                put("latest")
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
            if (!response.isSuccessful || responseBody == null) {
                throw RuntimeException("RPC HTTP ${response.code}")
            }
            val json = JSONObject(responseBody)
            if (json.has("error")) {
                throw RuntimeException("RPC error: ${json.getJSONObject("error").optString("message")}")
            }
            json.optString("result").ifEmpty { throw RuntimeException("RPC empty result") }
        }
    }
}