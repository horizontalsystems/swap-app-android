package io.horizontalsystems.swapapp.swap.api

/**
 * DTOs for the USwap `/v2` API (`swap-dev.unstoppable.money/api/v2`), matching the live response
 * shapes verified against `GET /providers`, `GET /tokens`, `POST /rate`, `POST /swap`, `POST /track`.
 */

/**
 * One entry of `GET /providers`. [executionType] is the single method a provider commits to —
 * `transfer`, `signed_transaction` or `thorchain_deposit`. We keep only `transfer` providers (the
 * user sends funds to a deposit address; no wallet integration needed).
 */
data class ProviderDto(
    val name: String?,
    val provider: String?,
    val executionType: String?,
    val suspended: Boolean? = null,
    val supportedChainIds: List<String>? = null,
    val amlPolicy: String? = null,
    val amlPolicyDescription: String? = null,
)

/** `GET /tokens?provider=`. The provider's supported tokens plus its execution metadata. */
data class ProviderTokensDto(
    val provider: String?,
    val executionType: String?,
    val tokens: List<SwapTokenDto>? = null,
)

/**
 * One token from `GET /tokens`. `identifier` is the CHAIN.TICKER[-ADDRESS] used by `/rate` & `/swap`.
 *
 * Every field is nullable on purpose: Gson ignores Kotlin nullability and will set a declared
 * non-null field to null when the JSON value is null/missing (some real tokens have `"name": null`).
 * Keeping these nullable lets a single bad token be skipped in mapping instead of throwing an NPE
 * that nukes the whole list. v2 token objects carry no `providers` array — the repository assigns
 * each token the set of (transfer) providers whose list contained it.
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
)

/** Request body for `POST /rate`. Read-only pricing across [providers]; no order is created. */
data class RateRequestDto(
    val sellAsset: String,
    val buyAsset: String,
    val sellAmount: String,
    val slippage: Int = 1,
    val providers: List<String>? = null,
    val chainId: String? = null,
)

data class RateResponseDto(
    val routes: List<RouteDto>? = null,
    val providerErrors: List<ProviderErrorDto>? = null,
)

/** Request body for `POST /swap`. Commits the order with the single chosen [provider]. */
data class SwapRequestDto(
    val sellAsset: String,
    val buyAsset: String,
    val sellAmount: String,
    val provider: String,
    val destinationAddress: String,
    val slippage: Int = 1,
    val refundAddress: String? = null,
    val chainId: String? = null,
)

/**
 * A route. From `/rate` it carries economics only; from `/swap` it additionally carries the
 * [execution] block and a top-level [uuid] tracking handle.
 */
data class RouteDto(
    val providers: List<String> = emptyList(),
    val sellAsset: String?,
    val sellAmount: String?,
    val buyAsset: String?,
    val expectedBuyAmount: String?,
    val minBuyAmount: String?,
    val fees: List<FeeDto> = emptyList(),
    val estimatedTime: EstimatedTimeDto?,
    /** P2P rate-lock / order expiry, epoch ms. */
    val expiresAt: Long?,
    val amlPolicy: String?,
    val execution: ExecutionDto?,
    val uuid: String?,
    /** The provider's own order id; the `swap.unstoppable.money/track` web page keys on it. */
    val providerSwapId: String?,
)

/**
 * How to send funds for a committed route. The app offers only `transfer` providers, so only the
 * `transfer` branch's fields are modelled (deposit to [depositAddress]).
 */
data class ExecutionDto(
    val method: String?,
    val chain: String?,
    val depositAddress: String?,
    val amount: String?,
    val asset: String?,
    /** Order identifier (XRP destination tag / chain memo) that MUST accompany the send. */
    val attachment: AttachmentDto?,
    /** Server-rendered deposit QR: [QrDto.str] = BIP21 payload, [QrDto.dataURL] = PNG. */
    val qr: QrDto?,
)

/** [type] is `destination_tag` (XRP) or `text` (RUNE / GAIA / TON / NEAR memo). */
data class AttachmentDto(
    val type: String?,
    val value: String?,
)

data class QrDto(
    val str: String?,
    val dataURL: String?,
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
    val maximumAmount: Double?,
)

/** Request body for `POST /track`. Transfer providers watch their own deposit; no tx hash needed. */
data class TrackRequestDto(
    val uuid: String,
    val inboundTxHash: String? = null,
)

/**
 * Live swap status. [status] is one of not_started / pending / swapping / action_required /
 * completed / refunded / failed / unknown. [meta] carries `pauseReason` on action_required.
 */
data class TrackResponseDto(
    val status: String?,
    val providers: List<String>? = null,
    val fromAsset: String?,
    val fromAmount: String?,
    val fromAddress: String?,
    val toAsset: String?,
    val toAmount: String?,
    val toAddress: String?,
    val legs: List<LegDto>? = null,
    val meta: TrackMetaDto? = null,
)

data class LegDto(
    val chainId: String?,
    val hash: String?,
    val type: String?,
    val status: String?,
    val fromAsset: String?,
    val fromAmount: String?,
    val toAsset: String?,
    val toAmount: String?,
    val toAddress: String?,
)

data class TrackMetaDto(
    val pauseReason: String? = null,
    val sellAmountUsd: String? = null,
)
