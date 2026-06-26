package io.horizontalsystems.swapapp.swap.api

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Blocksdecoded coin price API — the same source the swap-bot uses for USD values.
 * `GET /v1/coins?uids=<coingeckoId,…>&fields=price` returns a list of `{ uid, price }`. Public,
 * no API key required.
 */
interface PriceApi {

    @GET("v1/coins")
    suspend fun coins(
        @Query("uids") uids: String,
        @Query("fields") fields: String = "price",
    ): List<CoinPriceDto>
}

/**
 * One coin's price. `price` is declared as [Double] because Gson's reader parses both numeric and
 * string JSON values into it (the API has been seen to return either).
 */
data class CoinPriceDto(
    val uid: String?,
    val price: Double?,
)
