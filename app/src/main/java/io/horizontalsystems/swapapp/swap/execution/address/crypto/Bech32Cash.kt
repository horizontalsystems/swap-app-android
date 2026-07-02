package io.horizontalsystems.swapapp.swap.execution.address.crypto

/**
 * CashAddr (BCH / eCash) decoding, mirroring the reference wallet's `CashAddressConverter`. Uses the
 * same base32 charset as bech32 but a 40-bit polymod checksum and a version byte that encodes the
 * hash size — all of which are verified here.
 */
object Bech32Cash {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

    /** Returns true if [address] is a valid CashAddr for [hrp] (e.g. "bitcoincash" or "ecash"). */
    fun isValid(address: String, hrp: String): Boolean {
        // A CashAddr may be written with or without its "prefix:" — normalise to include it.
        val full = if (address.contains(":")) address else "$hrp:$address"

        val parts = full.split(":")
        if (parts.size != 2) return false
        val prefix = parts[0].lowercase()
        if (prefix != hrp) return false

        val body = parts[1]
        // Reject mixed case.
        if (body != body.lowercase() && body != body.uppercase()) return false
        val payloadChars = body.lowercase()
        if (payloadChars.isEmpty()) return false

        val values = IntArray(payloadChars.length)
        for (i in payloadChars.indices) {
            val idx = CHARSET.indexOf(payloadChars[i])
            if (idx < 0) return false
            values[i] = idx
        }

        if (!verifyChecksum(prefix, values)) return false

        // Drop the 8 checksum symbols and regroup 5-bit -> 8-bit.
        val payload5 = values.copyOfRange(0, values.size - 8)
        val data = convertBits(payload5, 5, 8, false) ?: return false
        if (data.isEmpty()) return false

        val version = data[0].toInt() and 0xff
        if (version and 0x80 != 0) return false

        val type = (version shr 3) and 0x1f
        if (type != 0 && type != 1) return false

        var hashSize = 20 + 4 * (version and 0x03)
        if (version and 0x04 != 0) hashSize *= 2
        return data.size == hashSize + 1
    }

    private fun verifyChecksum(prefix: String, payload: IntArray): Boolean {
        val values = ArrayList<Int>()
        for (c in prefix) values.add(c.code and 0x1f)
        values.add(0)
        for (v in payload) values.add(v)
        return polymod(values) == 0L
    }

    private fun polymod(values: List<Int>): Long {
        var c = 1L
        for (d in values) {
            val c0 = (c ushr 35) and 0xff
            c = ((c and 0x07ffffffffL) shl 5) xor d.toLong()
            if (c0 and 0x01L != 0L) c = c xor 0x98f2bc8e61L
            if (c0 and 0x02L != 0L) c = c xor 0x79b76d99e2L
            if (c0 and 0x04L != 0L) c = c xor 0xf33e5fb3c4L
            if (c0 and 0x08L != 0L) c = c xor 0xae2eabe2a8L
            if (c0 and 0x10L != 0L) c = c xor 0x1e4f43e470L
        }
        return c xor 1L
    }

    private fun convertBits(data: IntArray, fromBits: Int, toBits: Int, pad: Boolean): ByteArray? {
        var acc = 0
        var bits = 0
        val out = ArrayList<Byte>()
        val maxv = (1 shl toBits) - 1
        for (value in data) {
            if (value < 0 || (value shr fromBits) != 0) return null
            acc = (acc shl fromBits) or value
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                out.add(((acc shr bits) and maxv).toByte())
            }
        }
        if (pad) {
            if (bits > 0) out.add(((acc shl (toBits - bits)) and maxv).toByte())
        } else if (bits >= fromBits || ((acc shl (toBits - bits)) and maxv) != 0) {
            return null
        }
        return out.toByteArray()
    }
}