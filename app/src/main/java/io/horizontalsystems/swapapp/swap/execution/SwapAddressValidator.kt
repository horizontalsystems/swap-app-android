package io.horizontalsystems.swapapp.swap.execution

import io.horizontalsystems.swapapp.swap.SwapToken

/**
 * Basic destination-address validation, adapted from the swap-bot's addressValidator.ts. This is a
 * lightweight, dependency-free heuristic check (format/length/charset), not full checksum
 * verification — enough to catch obvious mistakes before showing a deposit address. Keyed on the
 * swap API's chain code (e.g. "ETH", "BTC", "ARB").
 */
object SwapAddressValidator {

    private val EVM_CHAINS = setOf(
        "ETH", "BSC", "ARB", "MATIC", "POL", "OP", "OPTIMISM", "BASE", "AVAX", "GNOSIS", "FTM"
    )

    private val EVM = Regex("^0x[0-9a-fA-F]{40}$")
    private val BTC_BECH32 = Regex("^bc1[0-9ac-hj-np-z]{11,71}$")
    private val BASE58 = Regex("^[1-9A-HJ-NP-Za-km-z]{25,62}$")
    private val TRON = Regex("^T[1-9A-HJ-NP-Za-km-z]{33}$")

    /** Returns null if [address] looks valid for [token]'s chain, otherwise an error message. */
    fun validate(address: String, token: SwapToken): String? {
        val value = address.trim()
        if (value.isEmpty()) return "Address can't be empty"

        val chain = token.chain.uppercase()

        val ok = when {
            chain in EVM_CHAINS -> EVM.matches(value)
            chain == "BTC" -> BTC_BECH32.matches(value) || BASE58.matches(value)
            chain == "TRX" || chain == "TRON" -> TRON.matches(value)
            // Unknown chain: accept anything that looks address-like (no spaces, reasonable length).
            else -> value.length in 16..120 && !value.any { it.isWhitespace() }
        }

        return if (ok) null else "This doesn't look like a valid ${token.name} address"
    }
}
