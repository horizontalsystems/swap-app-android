package io.horizontalsystems.swapapp.swap.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Unstoppable swap aggregator API. The base URL and `x-api-key` are injected by [SwapApiClient]
 * from BuildConfig (sourced from local.properties).
 */
interface SwapApi {

    /** Full supported token list, each carrying the providers that route it. */
    @GET("tokens/all")
    suspend fun tokens(): List<SwapTokenDto>

    /** Compare routes across providers. Use `dry = true` for display-only quotes. */
    @POST("quote")
    suspend fun quote(@Body body: QuoteRequestDto): QuoteResponseDto
}
