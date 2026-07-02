package io.horizontalsystems.swapapp.swap.execution.address.crypto

/**
 * BIP173/BIP350 bech32 & bech32m decoding for SegWit addresses (BTC "bc1…", LTC "ltc1…"), mirroring
 * the reference wallet's `SegwitAddressConverter`. Validates the human-readable prefix, the charset,
 * the 6-symbol polymod checksum, the witness version, and the decoded program length.
 */
object Bech32 {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private const val BECH32_CONST = 1
    private const val BECH32M_CONST = 0x2bc830a3

    /**
     * Returns true if [address] is a valid SegWit address for [hrp] (e.g. "bc" or "ltc"). A v0
     * program must be 20 bytes (P2WPKH) or 32 bytes (P2WSH); v1 (Taproot) must be 32 bytes; the
     * checksum constant must match the witness version (bech32 for v0, bech32m for v1+).
     */
    fun isValid(address: String, hrp: String): Boolean {
        val decoded = decode(address) ?: return false
        if (decoded.hrp != hrp) return false

        val data = decoded.data
        if (data.isEmpty()) return false
        val version = data[0].toInt()
        if (version < 0 || version > 16) return false

        val expectedConst = if (version == 0) BECH32_CONST else BECH32M_CONST
        if (decoded.checksumConst != expectedConst) return false

        val program = convertBits(data, 1, data.size - 1, 5, 8, false) ?: return false
        if (program.size < 2 || program.size > 40) return false
        if (version == 0 && program.size != 20 && program.size != 32) return false
        if (version == 1 && program.size != 32) return false
        return true
    }

    private data class Decoded(val hrp: String, val data: ByteArray, val checksumConst: Int)

    private fun decode(input: String): Decoded? {
        if (input.length < 8 || input.length > 90) return null
        // Reject mixed case (BIP173): must be all-lower or all-upper.
        val lower = input.lowercase()
        val upper = input.uppercase()
        if (input != lower && input != upper) return null
        val s = lower

        val sep = s.lastIndexOf('1')
        if (sep < 1 || sep + 7 > s.length) return null

        val hrp = s.substring(0, sep)
        val dataPart = s.substring(sep + 1)

        val values = ByteArray(dataPart.length)
        for (i in dataPart.indices) {
            val idx = CHARSET.indexOf(dataPart[i])
            if (idx < 0) return null
            values[i] = idx.toByte()
        }

        val checksumConst = polymod(hrpExpand(hrp) + values.map { it.toInt() })
        val data = values.copyOfRange(0, values.size - 6)
        return Decoded(hrp, data, checksumConst)
    }

    private fun hrpExpand(hrp: String): List<Int> {
        val high = hrp.map { it.code shr 5 }
        val low = hrp.map { it.code and 31 }
        return high + listOf(0) + low
    }

    private fun polymod(values: List<Int>): Int {
        val gen = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
        var chk = 1
        for (v in values) {
            val b = chk shr 25
            chk = ((chk and 0x1ffffff) shl 5) xor v
            for (i in 0 until 5) {
                if ((b shr i) and 1 != 0) chk = chk xor gen[i]
            }
        }
        return chk
    }

    /** Regroups [data] bytes from [fromBits] to [toBits]; returns null on invalid padding. */
    fun convertBits(data: ByteArray, start: Int, length: Int, fromBits: Int, toBits: Int, pad: Boolean): ByteArray? {
        var acc = 0
        var bits = 0
        val out = ArrayList<Byte>()
        val maxv = (1 shl toBits) - 1
        for (i in 0 until length) {
            val value = data[start + i].toInt() and 0xff
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