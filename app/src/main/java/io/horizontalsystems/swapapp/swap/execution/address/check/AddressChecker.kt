package io.horizontalsystems.swapapp.swap.execution.address.check

import io.horizontalsystems.swapapp.swap.SwapToken

/**
 * A single family of address checks, ported from the reference wallet's `AddressChecker`. [isClear]
 * answers whether the address passes; [supports] says whether this checker applies to the token at
 * all (so unsupported checks are silently skipped rather than run).
 */
interface AddressChecker {
    suspend fun isClear(address: String, token: SwapToken): Boolean
    fun supports(token: SwapToken): Boolean
}

/**
 * On-chain contract blacklist/freeze checks, ported from the wallet's `BlacklistAddressChecker`.
 * Tron USDT goes through TronGrid; EVM stablecoins through `eth_call`. HashDit (a risk-score API) is
 * consulted too when a key is configured.
 */
class BlacklistAddressChecker(
    private val hashDit: HashDitAddressValidator,
) : AddressChecker {

    override suspend fun isClear(address: String, token: SwapToken): Boolean {
        val chain = token.chain.uppercase()
        if (chain == "TRX" || chain == "TRON") {
            return Trc20AddressValidator.isClear(address, token)
        }
        if (Eip20AddressValidator.supports(token) && !Eip20AddressValidator.isClear(address, token)) {
            return false
        }
        if (hashDit.supports(token) && !hashDit.isClear(address, token)) {
            return false
        }
        return true
    }

    override fun supports(token: SwapToken): Boolean {
        val chain = token.chain.uppercase()
        if (chain == "TRX" || chain == "TRON") return Trc20AddressValidator.supports(token)
        return Eip20AddressValidator.supports(token) || hashDit.supports(token)
    }
}

/** Sanctions screening, ported from the wallet's `SanctionAddressChecker`. */
class SanctionAddressChecker(
    private val chainalysis: ChainalysisAddressValidator,
) : AddressChecker {

    override suspend fun isClear(address: String, token: SwapToken): Boolean =
        chainalysis.isClear(address)

    // Chainalysis screening is address-only (chain-agnostic); gated on a configured API key.
    override fun supports(token: SwapToken): Boolean = chainalysis.isEnabled
}