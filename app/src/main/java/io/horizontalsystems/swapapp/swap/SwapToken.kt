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
    val decimals: Int,
    val logoUrl: String?,
    val coingeckoId: String?,
    val providers: List<String>,
) {
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
