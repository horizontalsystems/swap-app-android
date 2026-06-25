package io.horizontalsystems.swapapp.swap.api

/**
 * DTOs for the Unstoppable swap API (`swap-dev.unstoppable.money/api/v1`), matching the live
 * response shapes verified against `GET /tokens/all` and `POST /quote`.
 */

/**
 * One entry of `GET /tokens/all`. `identifier` is the CHAIN.TICKER[-ADDRESS] used by `/quote`.
 *
 * All fields are nullable on purpose: Gson ignores Kotlin nullability and will happily set a
 * declared non-null field to null when the JSON value is null/missing (some real tokens have
 * `"name": null`). Keeping these nullable lets a single bad token be skipped in mapping instead of
 * throwing an NPE that nukes the whole list.
 */
data class SwapTokenDto(
    val identifier: String?,
    val address: String?,
    val chain: String?,
    val chainId: String?,
    val coingeckoId: String?,
    val decimals: Int?,
    val logoURI: String?,
    val name: String?,
    val ticker: String?,
    val shortCode: String?,
    val providers: List<String>? = null,
)

/** Request body for `POST /quote`. `dry = true` prices a route without registering it. */
data class QuoteRequestDto(
    val sellAsset: String,
    val buyAsset: String,
    val sellAmount: String,
    val providers: List<String>,
    val slippage: Int = 1,
    val dry: Boolean = true,
    val destinationAddress: String? = null,
    val refundAddress: String? = null,
)

data class QuoteResponseDto(
    val routes: List<RouteDto>? = null,
    val providerErrors: List<ProviderErrorDto>? = null,
)

data class RouteDto(
    val providers: List<String> = emptyList(),
    val buyAsset: String?,
    val sellAsset: String?,
    val expectedBuyAmount: String?,
    val expectedBuyAmountMaxSlippage: String?,
    val sellAmount: String?,
    val fees: List<FeeDto> = emptyList(),
    val estimatedTime: EstimatedTimeDto?,
    val inboundAddress: String?,
    val targetAddress: String?,
    val expiration: String?,
    val memo: String?,
    val qrCodeDataURL: String?,
    val providerSwapId: String?,
)

data class FeeDto(
    val type: String?,
    val chain: String?,
    val asset: String?,
    val amount: String?,
    val protocol: String?,
)

data class EstimatedTimeDto(
    val inbound: Long?,
    val swap: Long?,
    val outbound: Long?,
    val total: Long?,
)

data class ProviderErrorDto(
    val provider: String?,
    val error: String?,
    val errorCode: String?,
    val minimumAmount: Double?,
)
