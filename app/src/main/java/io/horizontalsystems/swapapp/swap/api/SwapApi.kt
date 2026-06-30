package io.horizontalsystems.swapapp.swap.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * USwap aggregator `/v2` API. The base URL and `X-API-Key` are injected by [SwapApiClient] from
 * BuildConfig (sourced from local.properties).
 *
 * The app is accountless — it can only relay deposit details to the user, never sign or build a
 * transaction — so it offers only providers whose `executionType` is `transfer` (see [providers]).
 */
interface SwapApi {

    /** All providers with their `executionType`, suspension state and supported chains. */
    @GET("providers")
    suspend fun providers(): List<ProviderDto>

    /** One provider's supported token list. v2 has no global list, so callers union per provider. */
    @GET("tokens")
    suspend fun tokens(@Query("provider") provider: String): ProviderTokensDto

    /** Compare routes across providers (read-only pricing; no order created). */
    @POST("rate")
    suspend fun rate(@Body body: RateRequestDto): RateResponseDto

    /** Commit a swap with one provider. Returns the executable route directly (no wrapper). */
    @POST("swap")
    suspend fun swap(@Body body: SwapRequestDto): RouteDto

    /** Poll a committed swap's live status by its route `uuid`. */
    @POST("track")
    suspend fun track(@Body body: TrackRequestDto): TrackResponseDto
}
