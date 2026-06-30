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
) {
    /**
     * True for a chain's native coin (e.g. `BTC.BTC`, `ETH.ETH`). Native tokens have no contract
     * address, so their [identifier] is just `CHAIN.TICKER` with no `-ADDRESS` suffix — verified to
     * match `address == null` across the whole token universe.
     */
    val isNative: Boolean get() = !identifier.contains("-")

    companion object {
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
