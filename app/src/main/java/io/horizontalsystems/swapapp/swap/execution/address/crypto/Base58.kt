package io.horizontalsystems.swapapp.swap.execution.address.crypto

import java.math.BigInteger
import java.security.MessageDigest

/**
 * Base58 / Base58Check, ported from the reference wallet's `bitcoincore.crypto.Base58`. Pure-Kotlin,
 * no SDK dependency. [decodeChecked] verifies the 4-byte double-SHA256 checksum that legacy
 * Bitcoin-family and Tron addresses carry, which is what makes this a real validity check rather
 * than a charset guess.
 */
object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val BASE = BigInteger.valueOf(58)
    private val INDEXES = IntArray(128) { -1 }.also { table ->
        for (i in ALPHABET.indices) table[ALPHABET[i].code] = i
    }

    /** Decodes a Base58 string to bytes. Throws [IllegalArgumentException] on an invalid character. */
    fun decode(input: String): ByteArray {
        if (input.isEmpty()) return ByteArray(0)

        // Convert from Base58 digits to a positional BigInteger, then to bytes.
        var num = BigInteger.ZERO
        for (c in input) {
            val digit = if (c.code < 128) INDEXES[c.code] else -1
            require(digit >= 0) { "Illegal character $c in Base58" }
            num = num.multiply(BASE).add(BigInteger.valueOf(digit.toLong()))
        }

        // BigInteger#toByteArray is minimal two's-complement: a leading 0x00 is only ever a sign
        // byte (or the sole byte of ZERO) — strip it so all-'1' input decodes to exactly its
        // leading-zero bytes (e.g. Solana's System Program address, 32 ones → 32 zero bytes).
        var bytes = num.toByteArray()
        if (bytes[0].toInt() == 0) {
            bytes = bytes.copyOfRange(1, bytes.size)
        }

        // Each leading '1' encodes a leading zero byte.
        var leadingZeros = 0
        while (leadingZeros < input.length && input[leadingZeros] == '1') leadingZeros++

        val decoded = ByteArray(leadingZeros + bytes.size)
        System.arraycopy(bytes, 0, decoded, leadingZeros + (decoded.size - leadingZeros - bytes.size), bytes.size)
        return decoded
    }

    /**
     * Decodes and verifies the trailing 4-byte checksum (first 4 bytes of double SHA-256 over the
     * payload). Returns the payload without the checksum. Throws if the string is malformed or the
     * checksum does not match.
     */
    fun decodeChecked(input: String): ByteArray {
        val decoded = decode(input)
        require(decoded.size >= 4) { "Input too short for checksum" }

        val payload = decoded.copyOfRange(0, decoded.size - 4)
        val checksum = decoded.copyOfRange(decoded.size - 4, decoded.size)
        val hash = doubleSha256(payload)
        for (i in 0 until 4) {
            require(hash[i] == checksum[i]) { "Checksum does not match" }
        }
        return payload
    }

    private fun doubleSha256(data: ByteArray): ByteArray {
        val sha = MessageDigest.getInstance("SHA-256")
        return sha.digest(sha.digest(data))
    }
}