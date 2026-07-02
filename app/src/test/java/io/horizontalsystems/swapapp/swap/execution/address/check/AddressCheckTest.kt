package io.horizontalsystems.swapapp.swap.execution.address.check

import io.horizontalsystems.swapapp.swap.execution.address.crypto.Base58
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressCheckTest {

    // --- ABI function selectors (Keccak-256 of the signature) ---

    @Test
    fun selector_matchesWellKnownSignatures() {
        // Universally-known ERC-20 selectors — lock in the keccak/selector machinery.
        assertEquals("a9059cbb", EvmAbi.selector("transfer(address,uint256)"))
        assertEquals("70a08231", EvmAbi.selector("balanceOf(address)"))
        // The blacklist/freeze methods the checks call.
        assertEquals("e47d6060", EvmAbi.selector("isBlackListed(address)")) // USDT
        assertEquals("fe575a87", EvmAbi.selector("isBlacklisted(address)")) // USDC
        assertEquals("e5839836", EvmAbi.selector("isFrozen(address)"))      // PYUSD
    }

    @Test
    fun encodeCall_padsAddressToWord() {
        val call = EvmAbi.encodeCall("isBlackListed(address)", "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed")
        assertEquals(
            "0xe47d6060" +
                "0000000000000000000000005aaeb6053f3e94c9b9a09f33669435e7ef1beaed",
            call
        )
    }

    @Test
    fun decodeBool_readsContractReturn() {
        assertFalse(EvmAbi.decodeBool("0x0000000000000000000000000000000000000000000000000000000000000000"))
        assertTrue(EvmAbi.decodeBool("0x0000000000000000000000000000000000000000000000000000000000000001"))
        assertFalse(EvmAbi.decodeBool("0x"))
    }

    // --- Tron base58 → contract hex (used to ABI-encode the address for TronGrid) ---

    @Test
    fun tronContract_decodesToKnownHex() {
        // USDT-TRON contract TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t → 41a614f803b6fd780986a42c78ec9c7f77e6ded13c
        val decoded = Base58.decodeChecked("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t")
        val hex = decoded.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals("41a614f803b6fd780986a42c78ec9c7f77e6ded13c", hex)
        assertEquals(0x41, decoded[0].toInt() and 0xff) // Tron version byte
    }
}