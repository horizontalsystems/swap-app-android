package io.horizontalsystems.swapapp.swap.execution.address.check

import io.horizontalsystems.swapapp.swap.SwapToken
import io.horizontalsystems.swapapp.swap.execution.address.crypto.Base58
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Checks whether a Tron address is blacklisted by the USDT-TRON contract, ported from the reference
 * wallet's `Trc20AddressValidator`. Uses TronGrid's `triggerconstantcontract` over plain OkHttp; the
 * base58→hex conversion that the wallet gets from tronkit is done here with our own [Base58].
 */
object Trc20AddressValidator {

    private const val TRONGRID_URL = "https://api.trongrid.io/wallet/triggerconstantcontract"

    // Only USDT on Tron exposes an on-chain blacklist (isBlackListed).
    private val METHODS = mapOf("tether" to "isBlackListed")

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS) // real per-check bound; see EvmJsonRpc
            .build()
    }

    fun supports(token: SwapToken): Boolean {
        val chain = token.chain.uppercase()
        if (chain != "TRX" && chain != "TRON") return false
        val coinId = token.coingeckoId ?: return false
        return METHODS.containsKey(coinId) && token.contractAddress != null
    }

    /** Returns true if the address is NOT blacklisted. Throws on RPC/network failure (inconclusive). */
    suspend fun isClear(address: String, token: SwapToken): Boolean {
        val methodName = token.coingeckoId?.let { METHODS[it] } ?: return true
        val contract = token.contractAddress ?: return true
        val addr = address.trim()

        return checkBlacklistStatus(addr, contract, methodName)
    }

    private suspend fun checkBlacklistStatus(
        base58Address: String,
        contractAddress: String,
        methodName: String
    ): Boolean = withContext(Dispatchers.IO) {
        val requestBody = JSONObject().apply {
            put("owner_address", base58Address)
            put("contract_address", contractAddress)
            put("function_selector", "$methodName(address)")
            put("parameter", encodeAddressForContract(base58Address))
            put("visible", true)
        }

        val request = Request.Builder()
            .url(TRONGRID_URL)
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
                ?: throw RuntimeException("TronGrid empty response")
            if (!response.isSuccessful) throw RuntimeException("TronGrid HTTP ${response.code}")

            val json = JSONObject(responseBody)
            if (json.has("Error")) throw RuntimeException("TronGrid contract error")

            val constantResult = json.optJSONArray("constant_result")
            if (constantResult != null && constantResult.length() > 0) {
                val result = constantResult.getString(0)
                // A trailing "01" means the address is blacklisted ⇒ not clear.
                return@withContext !result.endsWith("01")
            }
            throw RuntimeException("TronGrid no result")
        }
    }

    /** ABI-encodes a base58 Tron address as a left-padded 32-byte word (drops the 0x41 version). */
    private fun encodeAddressForContract(base58Address: String): String {
        val decoded = Base58.decodeChecked(base58Address) // 0x41 + 20-byte hash
        val hash = decoded.copyOfRange(1, decoded.size)
        return hash.toHex().padStart(64, '0')
    }

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xff
            sb.append("0123456789abcdef"[v shr 4])
            sb.append("0123456789abcdef"[v and 0x0f])
        }
        return sb.toString()
    }
}