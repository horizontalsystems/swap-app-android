package io.horizontalsystems.swapapp.swap

import io.horizontalsystems.swapapp.swap.api.SwapTokenDto

/**
 * A swappable token, sourced from the swap API's `GET /tokens/all`. [identifier] (e.g. "BTC.BTC",
 * "ETH.USDT-0x…") is the key the `/quote` endpoint uses, so this is the single token model across
 * the swap feature (replacing the earlier MarketKit-based one).
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
     * match `address == null` across the whole `/tokens/all` universe.
     */
    val isNative: Boolean get() = !identifier.contains("-")

    companion object {
        /** Maps a DTO to a token, or null if it lacks the fields we need (so it's skipped). */
        fun fromDto(dto: SwapTokenDto): SwapToken? {
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
                providers = dto.providers ?: emptyList(),
            )
        }
    }
}

/** A swap route's provider (from a quote route's `providers[0]`). */
data class SwapProvider(val id: String) {
    val title: String get() = TITLES[id] ?: id.lowercase().replaceFirstChar { it.uppercase() }

    /**
     * Whether a non-dry `/quote` needs a refund address. CEX providers reject the request without
     * one ("Refund address is required if dry is false"); on-chain DEX providers (THORChain,
     * MayaChain) auto-return funds to the sender, so they don't. Mirrors the swap-bot.
     */
    val requiresRefundAddress: Boolean
        get() = id.uppercase() !in ON_CHAIN_DEX_PROVIDERS

    /**
     * On-chain DEX providers whose deposit carries a memo (THORChain & MayaChain families). These
     * are only "memoless" if a separate service can fold the memo into the send amount — which the
     * Unstoppable memoless service does for THORChain only.
     */
    val requiresMemoDeposit: Boolean
        get() = id.uppercase() in ON_CHAIN_DEX_PROVIDERS

    companion object {
        // On-chain memo DEXes: they auto-refund to the sender (so need no refund address) and their
        // deposit carries a memo.
        private val ON_CHAIN_DEX_PROVIDERS = setOf(
            "THORCHAIN", "THORCHAIN_STREAMING",
            "MAYACHAIN", "MAYACHAIN_STREAMING",
        )

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
