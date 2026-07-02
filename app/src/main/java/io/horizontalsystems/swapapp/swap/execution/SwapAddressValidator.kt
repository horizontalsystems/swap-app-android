package io.horizontalsystems.swapapp.swap.execution

import io.horizontalsystems.swapapp.swap.SwapToken
import io.horizontalsystems.swapapp.swap.execution.address.AddressHandlerFactory

/**
 * Destination-address validation, ported from the reference wallet's address handler chain
 * (`modules.address`). Delegates to per-chain [AddressHandlerFactory] handlers that do real
 * verification — EIP-55 checksums for EVM, Base58Check for Bitcoin-family and Tron, bech32/bech32m
 * for SegWit, CashAddr for BCH — instead of a charset heuristic. Chains without a dedicated handler
 * fall back to a permissive shape check so no swap is ever blocked. Keyed on the swap API's chain
 * code (e.g. "ETH", "BTC", "TRX").
 */
object SwapAddressValidator {

    /**
     * Grouping key for the recent-address history. All EVM chains collapse to a single "EVM" scope —
     * a `0x…` address is derived from the same key on every EVM chain, so an address the user entered
     * for USDT on Ethereum is equally valid for USDC (or any EVM token), and worth suggesting. Other
     * chains are keyed on their own chain code so their distinct address formats don't mix.
     */
    fun addressScope(token: SwapToken): String {
        val chain = token.chain.uppercase()
        return if (chain in AddressHandlerFactory.EVM_CHAINS) "EVM" else chain
    }

    /** Returns null if [address] looks valid for [token]'s chain, otherwise an error message. */
    fun validate(address: String, token: SwapToken): String? {
        val value = address.trim()
        if (value.isEmpty()) return "Address can't be empty"

        val chain = AddressHandlerFactory.parserChain(token.chain)
        return if (chain.isSupported(value)) null
        else "This doesn't look like a valid ${token.name} address"
    }
}