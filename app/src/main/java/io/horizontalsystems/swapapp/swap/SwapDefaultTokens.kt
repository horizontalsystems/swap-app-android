package io.horizontalsystems.swapapp.swap

/**
 * Bundled seed for the very first launch: the most popular pair (BTC → USDT on Ethereum, what
 * [SwapPopularTokens] resolves to), captured verbatim from the swap API. It lets a fresh install
 * show a usable pair instantly instead of waiting for the token universe to download.
 *
 * This is NOT a source of truth — [MainSwapViewModel.restoreSelectedPair] replaces the seed with
 * the live-universe tokens as soon as they load, and it is never used again once any pair has been
 * persisted. The [SwapToken.providers] lists below only bridge quoting for those first seconds;
 * provider availability always comes from the API afterwards.
 */
object SwapDefaultTokens {

    // All transfer providers listing both tokens at capture time (2026-07-03).
    private val providers = listOf(
        "CCE", "EXOLIX", "LETSEXCHANGE", "NEAR", "PEGASUS", "QUICKEX", "STEALTHEX", "SWAPUZ",
    )

    /** The "You pay" seed: native Bitcoin. */
    val tokenIn = SwapToken(
        identifier = "BTC.BTC",
        name = "Bitcoin",
        ticker = "BTC",
        chain = "BTC",
        chainId = "bitcoin",
        decimals = 8,
        logoUrl = "https://assets.coingecko.com/coins/images/1/large/bitcoin.png",
        coingeckoId = "bitcoin",
        providers = providers,
    )

    /** The "You get" seed: USDT on Ethereum. */
    val tokenOut = SwapToken(
        identifier = "ETH.USDT-0XDAC17F958D2EE523A2206206994597C13D831EC7",
        name = "Tether",
        ticker = "USDT",
        chain = "ETH",
        chainId = "1",
        decimals = 6,
        logoUrl = "https://assets.coingecko.com/coins/images/325/large/Tether.png",
        coingeckoId = "tether",
        providers = providers,
        contractAddress = "0xdAC17F958D2ee523a2206206994597C13D831ec7",
    )
}
