package io.horizontalsystems.swapapp.swap

import io.horizontalsystems.swapapp.swap.api.SwapTokenDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwapTokenTest {

    private fun dto(identifier: String, address: String?, chain: String) = SwapTokenDto(
        identifier = identifier,
        address = address,
        chain = chain,
        chainId = null,
        coingeckoId = "tether",
        decimals = 6,
        logoURI = null,
        name = "Tether USD",
        ticker = "USDT",
        shortCode = null,
    )

    /**
     * The API uppercases identifiers (`ETH.USDT-0XDAC17F…`, Tron suffixes even gain non-Base58 chars
     * like 'O'/'I'), so contractAddress must come from the DTO's proper-cased `address` field — never
     * from the identifier suffix. Regression test for the blacklist checks silently going inert.
     */
    @Test
    fun contractAddress_comesFromDtoAddressField_notIdentifierSuffix() {
        val evm = SwapToken.fromDto(
            dto(
                identifier = "ETH.USDT-0XDAC17F958D2EE523A2206206994597C13D831EC7",
                address = "0xdAC17F958D2ee523a2206206994597C13D831ec7",
                chain = "ETH",
            ),
            providers = emptyList(),
        )!!
        assertEquals("0xdAC17F958D2ee523a2206206994597C13D831ec7", evm.contractAddress)

        val tron = SwapToken.fromDto(
            dto(
                identifier = "TRON.USDT-TR7NHQJEKQXGTCI8Q8ZY4PL8OTSZGJLJ6T",
                address = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",
                chain = "TRON",
            ),
            providers = emptyList(),
        )!!
        assertEquals("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", tron.contractAddress)
    }

    @Test
    fun contractAddress_isNullForNativeCoins() {
        val native = SwapToken.fromDto(
            dto(identifier = "BTC.BTC", address = null, chain = "BTC"),
            providers = emptyList(),
        )!!
        assertNull(native.contractAddress)
    }
}
