package io.horizontalsystems.swapapp.swap.execution.address

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressValidationTest {

    private fun supported(chain: String, address: String): Boolean =
        AddressHandlerFactory.parserChain(chain).isSupported(address)

    // --- EVM / EIP-55 ---

    @Test
    fun evm_acceptsValidChecksum() {
        // Canonical EIP-55 vectors.
        assertTrue(supported("ETH", "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"))
        assertTrue(supported("ETH", "0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359"))
        assertTrue(supported("ETH", "0xdbF03B407c01E7cD3CBea99509d93f8DDDC8C6FB"))
        assertTrue(supported("ETH", "0xD1220A0cf47c7B9Be7A2E6BA89F429762e7b9aDb"))
    }

    @Test
    fun evm_acceptsAllLowerAndAllUpper() {
        assertTrue(supported("ARB", "0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed"))
        assertTrue(supported("BASE", "0x5AAEB6053F3E94C9B9A09F33669435E7EF1BEAED"))
    }

    @Test
    fun evm_rejectsBadChecksumAndShape() {
        // One character's case flipped from the valid checksum above.
        assertFalse(supported("ETH", "0x5AAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"))
        assertFalse(supported("ETH", "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAe")) // too short
        assertFalse(supported("ETH", "5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed")) // missing 0x
    }

    // --- Bitcoin ---

    @Test
    fun btc_acceptsLegacyAndSegwit() {
        assertTrue(supported("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"))                 // P2PKH
        assertTrue(supported("BTC", "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy"))                 // P2SH
        assertTrue(supported("BTC", "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"))         // bech32 P2WPKH
        assertTrue(supported("BTC", "bc1p0xlxvlhemja6c4dqv22uapctqupfhlxm9h8z3k2e72q4k9hcz7vqzk5jj0")) // taproot
    }

    @Test
    fun btc_rejectsGarbageAndWrongChecksum() {
        assertFalse(supported("BTC", "1A1zP1eP5QGefi2DMPTfTL5SLmv7Divfna"))  // altered -> bad checksum
        assertFalse(supported("BTC", "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t5")) // bad bech32
    }

    // --- Cross-chain isolation ---

    @Test
    fun litecoin_acceptsOwnAddressesButNotBitcoin() {
        assertTrue(supported("LTC", "ltc1qqqqsyqcyq5rqwzqfpg9scrgwpugpzysn3s44dy")) // bech32 P2WPKH
        assertTrue(supported("LTC", "LKDyUEtTR1HXamkiEphisSiBJu6o3ZPE34"))           // legacy P2PKH
        // Deprecated legacy P2SH ("3…", version 0x05) is still valid/spendable on the LTC network.
        assertTrue(supported("LTC", "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy"))
        // A Bitcoin bech32 address must not validate for Litecoin (different HRP / checksum).
        assertFalse(supported("LTC", "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"))
    }

    // --- Tron ---

    @Test
    fun tron_acceptsBase58CheckAndRejectsOthers() {
        assertTrue(supported("TRX", "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t")) // USDT-TRON contract addr
        assertFalse(supported("TRX", "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"))
    }

    // --- Solana ---

    @Test
    fun solana_accepts32ByteBase58() {
        assertTrue(supported("SOL", "So11111111111111111111111111111111111111112"))
        // All-'1' System Program address (32 zero bytes) — regression test for the Base58 decode
        // that used to yield 33 bytes for 32 ones.
        assertTrue(supported("SOL", "11111111111111111111111111111111"))
        assertFalse(supported("SOL", "not-a-solana-address"))
    }

    // --- BCH cashaddr ---

    @Test
    fun bch_acceptsCashaddrWithAndWithoutPrefix() {
        assertTrue(supported("BCH", "bitcoincash:qpm2qsznhks23z7629mms6s4cwef74vcwvy22gdx6a"))
        assertTrue(supported("BCH", "qpm2qsznhks23z7629mms6s4cwef74vcwvy22gdx6a"))
        assertFalse(supported("BCH", "bitcoincash:qpm2qsznhks23z7629mms6s4cwef74vcwvy22gdx6b"))
    }

    // --- Unmodelled chain falls back to permissive shape check ---

    @Test
    fun unknownChain_permissiveButRejectsEmptyAndWhitespace() {
        assertTrue(supported("COSMOS", "cosmos1qypqxpq9qcrsszg2pvxq6rs0zqg3yyc5lzv7xu"))
        assertFalse(supported("COSMOS", "has space in it right here"))
        assertFalse(supported("COSMOS", "short"))
    }
}