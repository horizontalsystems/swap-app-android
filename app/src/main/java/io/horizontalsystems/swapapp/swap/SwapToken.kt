package io.horizontalsystems.swapapp.swap

import io.horizontalsystems.swapapp.swap.api.SwapTokenDto

/**
 * A swappable token, unioned from each transfer provider's `GET /tokens?provider=`. [identifier]
 * (e.g. "BTC.BTC", "ETH.USDT-0x…") is the key the `/rate` & `/swap` endpoints use, so this is the
 * single token model across the swap feature. [providers] is assigned by [SwapTokenRepository].
 */
data class SwapToken(
    val identifier: String,
    val name: String,
    val ticker: String,
    val chain: String,
    /** API chain slug (e.g. "bitcoin", "ethereum"); enriches the swap tracking URL. */
    val chainId: String?,
    val decimals: Int,
    val logoUrl: String?,
    val coingeckoId: String?,
    val providers: List<String>,
    /**
     * The token's contract address, or null for a native coin. Taken from the DTO's `address` field —
     * NOT from the [identifier] suffix, which the API uppercases (`ETH.USDT-0XDAC17F…`), destroying
     * the case-sensitive Base58 of Tron contracts and the `0x` prefix EVM checks look for. Used by
     * the on-chain blacklist checks to know which contract to query.
     */
    val contractAddress: String? = null,
) {
    /**
     * True for a chain's native coin (e.g. `BTC.BTC`, `ETH.ETH`). Native tokens have no contract
     * address, so their [identifier] is just `CHAIN.TICKER` with no `-ADDRESS` suffix — verified to
     * match `address == null` across the whole token universe.
     */
    val isNative: Boolean get() = !identifier.contains("-")

    /**
     * Human-friendly network name for the token's [chain] (e.g. `ETH` → "Ethereum", `MATIC` →
     * "Polygon"), used to tell the user which chain an address must be on. Falls back to the API
     * chain slug or raw [chain] code for anything not in the map — no chain is ever hidden.
     */
    val networkName: String
        get() = when (chain.uppercase()) {
            "ETH" -> "Ethereum"
            "BTC" -> "Bitcoin"
            "BCH" -> "Bitcoin Cash"
            "LTC" -> "Litecoin"
            "DOGE" -> "Dogecoin"
            "DASH" -> "Dash"
            "MATIC", "POLYGON" -> "Polygon"
            "BSC", "BNB" -> "BNB Smart Chain"
            "AVAX" -> "Avalanche"
            "ARB", "ARBITRUM" -> "Arbitrum"
            "OP", "OPTIMISM" -> "Optimism"
            "BASE" -> "Base"
            "TRX", "TRON" -> "Tron"
            "SOL", "SOLANA" -> "Solana"
            "ATOM", "COSMOS", "GAIA" -> "Cosmos"
            "THOR", "THORCHAIN", "RUNE" -> "THORChain"
            "MAYA", "MAYACHAIN", "CACAO" -> "Maya"
            "XRP" -> "XRP Ledger"
            "ADA", "CARDANO" -> "Cardano"
            "DOT", "POLKADOT" -> "Polkadot"
            else -> chainId?.replaceFirstChar { it.uppercase() } ?: chain
        }

    /**
     * Icon URL for the token's [chain] on the blocksdecoded CDN (same source the reference wallet
     * uses for its chain badges), or null for chains with no icon there. Every uid is verified to
     * exist on the CDN; the unmapped chains (BERA, DOGE, FIRO, MAYA, XRD, ZANO…) host effectively
     * only native coins, which render without a badge anyway.
     */
    val chainIconUrl: String?
        get() = CHAIN_ICON_UIDS[chain.uppercase()]?.let {
            "https://cdn.blocksdecoded.com/blockchain-icons/32px/$it@3x.png"
        }

    companion object {
        // Swap API chain code (plus common aliases) → blocksdecoded blockchain-icon uid.
        private val CHAIN_ICON_UIDS = mapOf(
            "ADA" to "cardano",
            "ARB" to "arbitrum-one",
            "ARBITRUM" to "arbitrum-one",
            "AVAX" to "avalanche",
            "BASE" to "base",
            "BCH" to "bitcoin-cash",
            "BSC" to "binance-smart-chain",
            "BNB" to "binance-smart-chain",
            "BTC" to "bitcoin",
            "DASH" to "dash",
            "DOT" to "polkadot",
            "ETH" to "ethereum",
            "GAIA" to "cosmos",
            "ATOM" to "cosmos",
            "COSMOS" to "cosmos",
            "GNO" to "gnosis",
            "LTC" to "litecoin",
            "NEAR" to "near-protocol",
            "OP" to "optimistic-ethereum",
            "OPTIMISM" to "optimistic-ethereum",
            "POL" to "polygon-pos",
            "MATIC" to "polygon-pos",
            "POLYGON" to "polygon-pos",
            "SOL" to "solana",
            "SOLANA" to "solana",
            "SUI" to "sui",
            "THOR" to "thorchain",
            "THORCHAIN" to "thorchain",
            "TON" to "the-open-network",
            "TRON" to "tron",
            "TRX" to "tron",
            "XEC" to "ecash",
            "XLM" to "stellar",
            "XMR" to "monero",
            "XRP" to "xrp",
            "ZEC" to "zcash",
        )

        /**
         * Maps a DTO to a token, or null if it lacks the fields we need (so it's skipped). v2 token
         * objects carry no providers — [SwapTokenRepository] supplies the supporting [providers].
         */
        fun fromDto(dto: SwapTokenDto, providers: List<String>): SwapToken? {
            val identifier = dto.identifier ?: return null
            val ticker = dto.ticker ?: return null
            val chain = dto.chain ?: return null
            return SwapToken(
                identifier = identifier,
                name = dto.name ?: ticker,
                ticker = ticker,
                chain = chain,
                chainId = dto.chainId,
                decimals = dto.decimals ?: 8,
                logoUrl = dto.logoURI,
                coingeckoId = dto.coingeckoId,
                providers = providers,
                contractAddress = dto.address?.takeIf { it.isNotBlank() },
            )
        }
    }
}

/** A swap route's provider (from a quote route's `providers[0]`). */
data class SwapProvider(val id: String) {
    val title: String get() = TITLES[id] ?: id.lowercase().replaceFirstChar { it.uppercase() }

    /**
     * Whether `POST /swap` needs a refund address. The app offers only `transfer` (P2P / NEAR)
     * providers, which reject a swap without one ("Refund address … is not valid"), so this is true
     * for every provider we surface. Drives the extra refund-address step in `MainActivity`.
     */
    val requiresRefundAddress: Boolean
        get() = true

    companion object {
        private val TITLES = mapOf(
            "THORCHAIN" to "THORChain",
            "MAYACHAIN" to "MayaChain",
            "CHANGENOW" to "ChangeNOW",
            "LETSEXCHANGE" to "LetsExchange",
            "STEALTHEX" to "StealthEX",
            "QUICKEX" to "QuickEx",
            "SWAPUZ" to "Swapuz",
            "EXOLIX" to "Exolix",
            "NEAR" to "NEAR Intents",
            "CCE" to "CCE",
            "PEGASUS" to "Pegasus",
            "UNISWAP" to "Uniswap",
            "PANCAKESWAP" to "PancakeSwap",
            "ALLBRIDGE" to "Allbridge",
        )
    }
}
