package io.horizontalsystems.swapapp.swap.execution.address.check

import io.horizontalsystems.swapapp.swap.SwapToken

/**
 * Checks whether a destination address is blacklisted/frozen by an EVM stablecoin contract, ported
 * from the reference wallet's `Eip20AddressValidator`. Instead of ethereumkit it uses a raw
 * `eth_call` ([EvmJsonRpc]) and our own ABI encoder ([EvmAbi]). Only the tokens that expose an
 * on-chain freeze list are supported: USDT (`isBlackListed`), USDC (`isBlacklisted`), PYUSD
 * (`isFrozen`). Coins are identified by CoinGecko id, matching the wallet's coin-uid switch.
 */
object Eip20AddressValidator {

    private data class Method(val signature: String, val chains: Set<String>)

    // Mirrors the wallet's coinUid → method/chain mapping (chain codes are the swap API's).
    private val METHODS = mapOf(
        "tether" to Method("isBlackListed(address)", setOf("ETH")),
        "usd-coin" to Method(
            "isBlacklisted(address)",
            setOf("ETH", "OP", "OPTIMISM", "AVAX", "ARB", "ARBITRUM", "MATIC", "POL", "POLYGON", "ZKSYNC", "BASE")
        ),
        "paypal-usd" to Method("isFrozen(address)", setOf("ETH")),
    )

    fun supports(token: SwapToken): Boolean = methodFor(token) != null

    /** Returns true if the address is NOT blacklisted. Throws on RPC/network failure (inconclusive). */
    suspend fun isClear(address: String, token: SwapToken): Boolean {
        val method = methodFor(token) ?: return true
        val contract = token.contractAddress ?: return true

        val data = EvmAbi.encodeCall(method.signature, address.trim())
        val result = EvmJsonRpc.call(token.chain, contract, data)
        return !EvmAbi.decodeBool(result)
    }

    private fun methodFor(token: SwapToken): Method? {
        val coinId = token.coingeckoId ?: return null
        val contract = token.contractAddress ?: return null
        if (!contract.startsWith("0x", ignoreCase = true)) return null
        val method = METHODS[coinId] ?: return null
        if (token.chain.uppercase() !in method.chains) return null
        if (!EvmJsonRpc.supportsChain(token.chain)) return null
        return method
    }
}