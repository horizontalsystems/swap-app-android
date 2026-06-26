package io.horizontalsystems.swapapp.swap

/**
 * Builds the context-aware "Popular Tokens" list shown in the swap token selector, ported from the
 * reference wallet's `multiswap.SwapPopularTokens`.
 *
 * The reference works over MarketKit `Token`s keyed by `BlockchainType` + coin uid; this app has no
 * MarketKit, so the same formula is expressed over [SwapToken]s using the swap API's chain codes
 * (`BTC`, `ETH`, `XMR`, `ZEC`, `TRON`, …) and ticker. The list is assembled from a fixed formula
 * based on the opposite (context) token, then cleaned up: nulls and the context token are dropped
 * and duplicates removed (first occurrence kept).
 */
object SwapPopularTokens {

    // Swap API chain codes (see GET /tokens/all `chain` field).
    private const val BTC = "BTC"
    private const val ETH = "ETH"
    private const val XMR = "XMR"
    private const val ZEC = "ZEC"
    private const val TRON = "TRON"
    private const val BSC = "BSC"
    private const val BASE = "BASE"

    private val baseNativeChains = listOf(BTC, ETH, XMR, ZEC, TRON)

    fun build(tokens: List<SwapToken>, context: SwapToken?): List<SwapToken> {
        // Native coin per chain. A chain can expose more than one native entry (e.g. ZEC.ZEC and
        // ZEC.ZECSHIELDED) — prefer the shortest identifier, which is the plain native (ZEC.ZEC).
        val natives = tokens.filter { it.isNative }
            .groupBy { it.chain }
            .mapValues { (_, list) -> list.minByOrNull { it.identifier.length }!! }

        fun stable(chain: String, ticker: String): SwapToken? =
            tokens.firstOrNull { it.chain == chain && it.ticker.equals(ticker, ignoreCase = true) }

        val usdtEth = stable(ETH, "USDT")
        val usdcEth = stable(ETH, "USDC")

        val baseNatives = baseNativeChains.map { natives[it] }
        val tailStables = listOf(
            usdtEth,
            stable(TRON, "USDT"),
            stable(BSC, "USDT"),
            stable(BASE, "USDT"),
        )

        val ordered: List<SwapToken?> = when {
            context == null ->
                baseNatives + listOf(usdtEth, usdcEth) + tailStables

            context.isNative -> {
                // Case B — context is a native coin
                val usdtSame = stable(context.chain, "USDT") ?: usdtEth
                val usdcSame = stable(context.chain, "USDC") ?: usdcEth
                listOf(usdtSame, usdcSame) + tailStables + baseNatives
            }

            else -> {
                // Case A — context is a stablecoin or any other non-native token
                val nativeRaw = natives[context.chain]
                // If the context chain's native coin is already a base native (e.g. ETH on an L2
                // like Arbitrum, where ARB.ETH shares the `ethereum` coingecko id with ETH.ETH),
                // reuse that base-native token so it collapses into a single entry moved to the
                // front, instead of a chain-specific duplicate.
                val nativeSame = baseNatives.firstOrNull {
                    it?.coingeckoId != null && it.coingeckoId == nativeRaw?.coingeckoId
                } ?: nativeRaw
                val usdtSame = stable(context.chain, "USDT") ?: usdtEth
                val usdcSame = stable(context.chain, "USDC") ?: usdcEth
                listOf(nativeSame) + baseNatives + listOf(usdtSame, usdcSame) + tailStables
            }
        }

        val seen = mutableSetOf<String>()
        val result = mutableListOf<SwapToken>()
        for (token in ordered) {
            if (token == null) continue
            // can't swap into the context token itself
            if (context != null && token.identifier == context.identifier) continue
            if (!seen.add(token.identifier)) continue
            result.add(token)
        }
        return result
    }
}
