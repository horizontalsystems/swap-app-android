package io.horizontalsystems.swapapp.swap.execution.address.crypto

/**
 * Keccak-256 (the pre-standard variant Ethereum uses — note this is NOT FIPS SHA3-256, which pads
 * differently and is not what EIP-55 expects). Needed to verify the mixed-case checksum of EVM
 * addresses. Pure Kotlin so the swap app pulls in no crypto SDK.
 */
object Keccak256 {
    private const val RATE_BYTES = 136 // 1088-bit rate for Keccak-256

    // Kotlin hex Long literals wrap to signed, so top-bit-set constants are written directly.
    private val RC = longArrayOf(
        0x0000000000000001L, 0x0000000000008082L, -0x7fffffffffff7f76L, -0x7fffffff7fff8000L,
        0x000000000000808bL, 0x0000000080000001L, -0x7fffffff7fff7f7fL, -0x7fffffffffff7ff7L,
        0x000000000000008aL, 0x0000000000000088L, 0x0000000080008009L, 0x000000008000000aL,
        0x000000008000808bL, -0x7fffffffffffff75L, -0x7fffffffffff7f77L, -0x7fffffffffff7ffdL,
        -0x7fffffffffff7ffeL, -0x7fffffffffffff80L, 0x000000000000800aL, -0x7fffffff7ffffff6L,
        -0x7fffffff7fff7f7fL, -0x7fffffffffff7f80L, 0x0000000080000001L, -0x7fffffff7fff7ff8L
    )
    private val ROTC = intArrayOf(
        1, 3, 6, 10, 15, 21, 28, 36, 45, 55, 2, 14, 27, 41, 56, 8, 25, 43, 62, 18, 39, 61, 20, 44
    )
    private val PILN = intArrayOf(
        10, 7, 11, 17, 18, 3, 5, 16, 8, 21, 24, 4, 15, 23, 19, 13, 12, 2, 20, 14, 22, 9, 6, 1
    )

    fun digest(input: ByteArray): ByteArray {
        val state = LongArray(25)
        val padded = pad(input)

        var offset = 0
        while (offset < padded.size) {
            for (i in 0 until RATE_BYTES / 8) {
                var lane = 0L
                for (b in 0 until 8) {
                    lane = lane or ((padded[offset + i * 8 + b].toLong() and 0xff) shl (8 * b))
                }
                state[i] = state[i] xor lane
            }
            keccakF(state)
            offset += RATE_BYTES
        }

        val out = ByteArray(32)
        for (i in 0 until 32) {
            out[i] = (state[i / 8] ushr (8 * (i % 8))).toByte()
        }
        return out
    }

    private fun pad(input: ByteArray): ByteArray {
        val padLen = RATE_BYTES - (input.size % RATE_BYTES)
        val padded = input.copyOf(input.size + padLen)
        // Keccak padding: 0x01 domain byte, final byte gets 0x80 (may coincide when padLen == 1).
        padded[input.size] = (padded[input.size].toInt() xor 0x01).toByte()
        padded[padded.size - 1] = (padded[padded.size - 1].toInt() xor 0x80).toByte()
        return padded
    }

    private fun keccakF(a: LongArray) {
        val bc = LongArray(5)
        for (round in 0 until 24) {
            // Theta
            for (i in 0 until 5) {
                bc[i] = a[i] xor a[i + 5] xor a[i + 10] xor a[i + 15] xor a[i + 20]
            }
            for (i in 0 until 5) {
                val t = bc[(i + 4) % 5] xor java.lang.Long.rotateLeft(bc[(i + 1) % 5], 1)
                var j = 0
                while (j < 25) {
                    a[j + i] = a[j + i] xor t
                    j += 5
                }
            }
            // Rho & Pi
            var t = a[1]
            for (i in 0 until 24) {
                val j = PILN[i]
                val tmp = a[j]
                a[j] = java.lang.Long.rotateLeft(t, ROTC[i])
                t = tmp
            }
            // Chi
            var j = 0
            while (j < 25) {
                for (i in 0 until 5) bc[i] = a[j + i]
                for (i in 0 until 5) {
                    a[j + i] = a[j + i] xor (bc[(i + 1) % 5].inv() and bc[(i + 2) % 5])
                }
                j += 5
            }
            // Iota
            a[0] = a[0] xor RC[round]
        }
    }
}