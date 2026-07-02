package io.horizontalsystems.swapapp.swap.execution.address

import io.horizontalsystems.swapapp.swap.execution.address.crypto.Base58
import io.horizontalsystems.swapapp.swap.execution.address.crypto.Bech32
import io.horizontalsystems.swapapp.swap.execution.address.crypto.Bech32Cash
import io.horizontalsystems.swapapp.swap.execution.address.crypto.Keccak256

/**
 * Address handlers ported from the reference wallet's `modules.address.IAddressHandler`. Each handler
 * knows one address encoding and answers a single question — [isSupported] — used by
 * [AddressParserChain] to decide whether a string is a valid address for a given chain. Unlike the
 * wallet these are dependency-free: the checksum math lives in the sibling `crypto` package instead
 * of per-chain SDK kits.
 */
interface IAddressHandler {
    fun isSupported(value: String): Boolean
}

/** EVM `0x…` addresses. Validates length/charset and, for mixed-case input, the EIP-55 checksum. */
object AddressHandlerEvm : IAddressHandler {
    private val HEX = Regex("^[0-9a-fA-F]{40}$")

    override fun isSupported(value: String): Boolean {
        if (!value.startsWith("0x") && !value.startsWith("0X")) return false
        val hex = value.substring(2)
        if (!HEX.matches(hex)) return false

        val lower = hex.lowercase()
        val upper = hex.uppercase()
        // All-lower or all-upper carries no checksum, so it's accepted as-is (matches ethereumkit).
        if (hex == lower || hex == upper) return true
        return hex == eip55(lower)
    }

    /** Applies the EIP-55 mixed-case checksum to a lowercase 40-char hex address. */
    private fun eip55(lowerHex: String): String {
        val hash = Keccak256.digest(lowerHex.toByteArray(Charsets.US_ASCII))
        val sb = StringBuilder(40)
        for (i in lowerHex.indices) {
            val c = lowerHex[i]
            if (c in '0'..'9') {
                sb.append(c)
            } else {
                // Uppercase the letter when the corresponding hash nibble is >= 8.
                val nibble = (hash[i / 2].toInt() shr (if (i % 2 == 0) 4 else 0)) and 0x0f
                sb.append(if (nibble >= 8) c.uppercaseChar() else c)
            }
        }
        return sb.toString()
    }
}

/**
 * Legacy Base58Check addresses (P2PKH / P2SH). Constructed with the chain's version bytes so a BTC
 * address isn't accepted for, say, Litecoin. Also covers Tron when given its `0x41` version byte.
 */
class AddressHandlerBase58(private val versions: Set<Int>) : IAddressHandler {
    override fun isSupported(value: String): Boolean = try {
        val data = Base58.decodeChecked(value)
        // 1 version byte + 20-byte hash.
        data.size == 21 && (data[0].toInt() and 0xff) in versions
    } catch (_: Throwable) {
        false
    }
}

/** SegWit bech32/bech32m addresses for a given human-readable prefix (e.g. "bc", "ltc"). */
class AddressHandlerBech32(private val hrp: String) : IAddressHandler {
    override fun isSupported(value: String): Boolean = try {
        Bech32.isValid(value, hrp)
    } catch (_: Throwable) {
        false
    }
}

/** CashAddr addresses (BCH / eCash) for a given prefix (e.g. "bitcoincash", "ecash"). */
class AddressHandlerCash(private val hrp: String) : IAddressHandler {
    override fun isSupported(value: String): Boolean = try {
        Bech32Cash.isValid(value, hrp)
    } catch (_: Throwable) {
        false
    }
}

/**
 * Solana addresses: Base58 with no checksum, decoding to a 32-byte public key (mirrors the wallet's
 * `AddressHandlerSolana` size check).
 */
object AddressHandlerSolana : IAddressHandler {
    override fun isSupported(value: String): Boolean = try {
        Base58.decode(value).size == 32
    } catch (_: Throwable) {
        false
    }
}

/**
 * Fallback for chains we don't have a real validator for (Cosmos, XRP, ADA, DOT, TON, …). Accepts
 * anything address-shaped — non-empty, no whitespace, reasonable length — so the swap flow never
 * blocks a chain just because it isn't modelled yet. Matches the wallet's `AddressHandlerPure`
 * intent of "let it through" while still catching empty/garbage input.
 */
object AddressHandlerPermissive : IAddressHandler {
    override fun isSupported(value: String): Boolean =
        value.length in 16..120 && value.none { it.isWhitespace() }
}