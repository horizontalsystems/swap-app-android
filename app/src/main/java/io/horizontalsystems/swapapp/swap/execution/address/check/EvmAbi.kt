package io.horizontalsystems.swapapp.swap.execution.address.check

import io.horizontalsystems.swapapp.swap.execution.address.crypto.Keccak256

/**
 * Minimal ABI encoding for the single-`address`-argument, single-`bool`-return contract calls the
 * blacklist checks need (`isBlackListed`/`isBlacklisted`/`isFrozen`). Replaces the reference wallet's
 * dependency on ethereumkit's `ContractMethod`; the 4-byte selector comes from our own Keccak-256.
 */
object EvmAbi {

    /** 4-byte function selector for [signature], e.g. "isBlackListed(address)" → "e47d6060". */
    fun selector(signature: String): String {
        val hash = Keccak256.digest(signature.toByteArray(Charsets.US_ASCII))
        return hash.copyOfRange(0, 4).toHex()
    }

    /** ABI-encodes a 20-byte EVM address as a left-padded 32-byte word (64 hex chars). */
    fun encodeAddress(address: String): String {
        val hex = address.removePrefix("0x").removePrefix("0X").lowercase()
        require(hex.length == 40) { "Invalid EVM address" }
        return hex.padStart(64, '0')
    }

    /** `data` field for a call of [signature] with a single address argument. */
    fun encodeCall(signature: String, address: String): String =
        "0x" + selector(signature) + encodeAddress(address)

    /**
     * Decodes a bool return word. Contracts encode `true` as a 32-byte word ending in 0x01, so any
     * non-zero byte means the flag is set (mirrors the wallet's `response.contains(1.toByte())`).
     */
    fun decodeBool(hexResult: String): Boolean {
        val hex = hexResult.removePrefix("0x")
        if (hex.isEmpty()) return false
        // Any non-zero hex digit ⇒ the returned word is non-zero ⇒ true.
        return hex.any { it != '0' }
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