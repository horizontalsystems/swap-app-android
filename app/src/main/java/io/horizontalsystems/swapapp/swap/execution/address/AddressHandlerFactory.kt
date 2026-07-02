package io.horizontalsystems.swapapp.swap.execution.address

/**
 * Builds the [AddressParserChain] for a swap chain code, ported from the reference wallet's
 * `AddressHandlerFactory` (which keys on `BlockchainType`; here we key on the swap API's chain code
 * string, e.g. "ETH", "BTC", "TRX"). The address version bytes and HRPs come from the wallet's
 * per-network `MainNet` classes.
 */
object AddressHandlerFactory {

    /**
     * EVM chain codes that share the `0x…` address format. Kept in sync with [addressScope] so a
     * single history bucket and a single validator cover every EVM token.
     */
    val EVM_CHAINS = setOf(
        "ETH", "BSC", "BNB", "ARB", "ARBITRUM", "MATIC", "POL", "POLYGON",
        "OP", "OPTIMISM", "BASE", "AVAX", "GNOSIS", "FTM", "FANTOM", "ZKSYNC"
    )

    fun parserChain(chainCode: String): AddressParserChain {
        val chain = chainCode.uppercase()
        val handlers: List<IAddressHandler> = when {
            chain in EVM_CHAINS -> listOf(AddressHandlerEvm)

            // versions: PubKeyHash / ScriptHash from the reference wallet's MainNet classes.
            chain == "BTC" -> listOf(AddressHandlerBase58(setOf(0x00, 0x05)), AddressHandlerBech32("bc"))
            // 0x05 is Litecoin's deprecated legacy P2SH prefix ("3…", shared with BTC) — still valid
            // and spendable on the LTC network, so it must not be rejected.
            chain == "LTC" -> listOf(AddressHandlerBase58(setOf(0x30, 0x32, 0x05)), AddressHandlerBech32("ltc"))
            chain == "DOGE" -> listOf(AddressHandlerBase58(setOf(0x1E, 0x16)))
            chain == "DASH" -> listOf(AddressHandlerBase58(setOf(76, 16)))
            chain == "BCH" -> listOf(AddressHandlerBase58(setOf(0x00, 0x05)), AddressHandlerCash("bitcoincash"))
            chain == "XEC" || chain == "ECASH" -> listOf(AddressHandlerBase58(setOf(0x00, 0x05)), AddressHandlerCash("ecash"))

            // Tron: Base58Check with the 0x41 version byte.
            chain == "TRX" || chain == "TRON" -> listOf(AddressHandlerBase58(setOf(0x41)))

            chain == "SOL" || chain == "SOLANA" -> listOf(AddressHandlerSolana)

            // Chains we don't model yet (Cosmos, XRP, ADA, DOT, TON, …) — accept address-shaped input.
            else -> listOf(AddressHandlerPermissive)
        }
        return AddressParserChain(handlers)
    }
}