package io.horizontalsystems.swapapp.swap.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Blocksdecoded coin price API — the same source MarketKit / the swap-bot use for USD values and
 * market-cap data. Public, no API key required.
 */
interface PriceApi {

    /** `GET /v1/coins?uids=<coingeckoId,…>&fields=price` → list of `{ uid, price }`. */
    @GET("v1/coins")
    suspend fun coins(
        @Query("uids") uids: String,
        @Query("fields") fields: String = "price",
    ): List<CoinPriceDto>

    /**
     * The full coin catalog with market-cap ranks (`GET /v1/coins?fields=uid,market_cap_rank`).
     * Returns every coin (~1500, ~66 KB); ranked coins carry a non-null [CoinMarketDto.rank]. Used
     * to order the "Top tokens" short list without pulling in the whole MarketKit stack.
     */
    @GET("v1/coins")
    suspend fun marketInfo(
        @Query("fields") fields: String = "uid,market_cap_rank",
    ): List<CoinMarketDto>
}

/** One coin's market-cap rank. `rank` is null for coins CoinGecko hasn't ranked. */
data class CoinMarketDto(
    val uid: String?,
    @SerializedName("market_cap_rank") val rank: Int?,
)

/**
 * One coin's price. `price` is declared as [Double] because Gson's reader parses both numeric and
 * string JSON values into it (the API has been seen to return either).
 */
data class CoinPriceDto(
    val uid: String?,
    val price: Double?,
)
