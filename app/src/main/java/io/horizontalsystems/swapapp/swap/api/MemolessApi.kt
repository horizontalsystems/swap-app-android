package io.horizontalsystems.swapapp.swap.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Unstoppable "memoless" API (`swap.unstoppable.money/memoless/api/v1`). Public, no API key.
 *
 * THORChain deposits normally require a memo attached to the send — easy to get wrong and lose
 * funds. The memoless service avoids that by nudging the send amount by a few sub-units that encode
 * a reference (e.g. `0.05` → `0.05014881`), so the backend can match the incoming deposit without a
 * memo. The returned [MemolessPreflightData.qrCode] is the BIP21 payment URI carrying that exact
 * amount.
 *
 * Mirrors `swap-bot`'s `utils/memoless-api.ts`.
 */
interface MemolessApi {

    /** Register a THORChain [MemolessRegisterRequest.memo] → reference + amount to actually send. */
    @POST("register")
    suspend fun register(@Body body: MemolessRegisterRequest): MemolessRegisterResponse

    /** Resolve a registered swap to its deposit address + payment URI. */
    @POST("preflight")
    suspend fun preflight(@Body body: MemolessPreflightRequest): MemolessPreflightResponse
}

data class MemolessRegisterRequest(
    val asset: String,
    val memo: String,
    @SerializedName("requested_in_asset_amount") val requestedInAssetAmount: String,
)

data class MemolessRegisterResponse(
    val reference: String?,
    @SerializedName("suggested_in_asset_amount") val suggestedInAssetAmount: String?,
)

data class MemolessPreflightRequest(
    val asset: String,
    val reference: String,
    val amount: String,
)

data class MemolessPreflightResponse(
    val data: MemolessPreflightData?,
)

data class MemolessPreflightData(
    @SerializedName("qr_code_data_url") val qrCodeDataUrl: String?,
    @SerializedName("qr_code") val qrCode: String?,
    @SerializedName("inbound_address") val inboundAddress: String?,
    @SerializedName("seconds_remaining") val secondsRemaining: Long?,
)
