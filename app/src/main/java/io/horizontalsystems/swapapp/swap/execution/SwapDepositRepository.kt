package io.horizontalsystems.swapapp.swap.execution

import android.util.Log
import com.google.gson.Gson
import io.horizontalsystems.swapapp.swap.SwapProvider
import io.horizontalsystems.swapapp.swap.SwapProviderError
import io.horizontalsystems.swapapp.swap.SwapRouteNotFound
import io.horizontalsystems.swapapp.swap.SwapToken
import io.horizontalsystems.swapapp.swap.api.ProviderErrorDto
import io.horizontalsystems.swapapp.swap.api.SwapApi
import io.horizontalsystems.swapapp.swap.api.SwapApiClient
import io.horizontalsystems.swapapp.swap.api.SwapRequestDto
import io.horizontalsystems.swapapp.swap.api.TrackRequestDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.math.BigDecimal
import java.net.URLEncoder

/**
 * Live swap status, mapped from `POST /v2/track`. The first four are the ordered progress stages
 * shown in the stepper; the rest are terminal/exceptional states surfaced as banners.
 */
enum class SwapStatus(val label: String) {
    NotStarted("Awaiting deposit…"),
    Pending("Confirming deposit…"),
    Swapping("Exchanging…"),
    Completed("Completed!"),
    Refunded("Refunded"),
    Failed("Failed"),
    ActionRequired("Action required"),
    Unknown("Checking status…");

    /** Polling stops on a terminal status. */
    val isTerminal: Boolean get() = this == Completed || this == Refunded || this == Failed

    /** Position in the [stages] stepper (action_required sits at the exchanging stage). */
    val stageIndex: Int
        get() = when (this) {
            NotStarted, Unknown -> 0
            Pending -> 1
            Swapping, ActionRequired -> 2
            Completed -> 3
            Refunded, Failed -> 3
        }

    companion object {
        /** Ordered stages rendered by the vertical tracker. */
        val stages = listOf(NotStarted, Pending, Swapping, Completed)

        fun fromApi(status: String?): SwapStatus = when (status?.lowercase()) {
            "not_started" -> NotStarted
            "pending" -> Pending
            "swapping" -> Swapping
            "completed" -> Completed
            "refunded" -> Refunded
            "failed" -> Failed
            "action_required" -> ActionRequired
            else -> Unknown
        }
    }
}

/** A poll result: the current [status] plus the provider's `pauseReason` on action_required. */
data class SwapTrackUpdate(
    val status: SwapStatus,
    val pauseReason: String? = null,
)

/**
 * A committed swap: the real deposit address the user must send to and the exact amount.
 *
 *  - [paymentUri] — a BIP21 payment URI (`bitcoin:bc1…?amount=…`) the QR encodes and [deeplink]
 *    wraps, so a tap/scan opens the user's wallet pre-filled.
 *  - [attachmentValue] — a destination tag / memo that MUST accompany the send for some chains
 *    (XRP, RUNE, …), or the funds are lost. [attachmentLabel] names it for the UI.
 *  - [uuid] — the tracking handle for `POST /v2/track`.
 */
data class SwapIntent(
    val uuid: String,
    val depositAddress: String,
    val amountIn: BigDecimal,
    val tokenIn: SwapToken,
    val attachmentValue: String?,
    val attachmentLabel: String?,
    val paymentUri: String?,
    val deeplink: String?,
    val secondsRemaining: Long?,
)

/**
 * Backs the swap execution flow against the `/v2` API.
 *
 * [createIntent] commits the swap (`POST /v2/swap`) with the chosen `transfer` provider and returns
 * the deposit details from its `execution` block. [statusUpdates] then polls `POST /v2/track` for
 * live status until a terminal state.
 */
class SwapDepositRepository(
    private val api: SwapApi = SwapApiClient.api,
) {

    /** Commit the swap and return its real deposit details. */
    suspend fun createIntent(
        tokenIn: SwapToken,
        tokenOut: SwapToken,
        amountIn: BigDecimal,
        provider: SwapProvider,
        destinationAddress: String,
        refundAddress: String?,
    ): SwapIntent = withContext(Dispatchers.IO) {
        val sellAmount = amountIn.stripTrailingZeros().toPlainString()
        Log.d(
            TAG,
            "swap request: provider=${provider.id} ${tokenIn.identifier} -> ${tokenOut.identifier} " +
                "amount=$sellAmount refundAddress=${refundAddress != null}"
        )

        val route = try {
            api.swap(
                SwapRequestDto(
                    sellAsset = tokenIn.identifier,
                    buyAsset = tokenOut.identifier,
                    sellAmount = sellAmount,
                    provider = provider.id,
                    destinationAddress = destinationAddress,
                    refundAddress = refundAddress,
                )
            )
        } catch (e: HttpException) {
            // The provider error body carries a human-readable reason; surface it to the user
            // (BASIC OkHttp logging hides the body, so log it too).
            val body = e.response()?.errorBody()?.string()
            Log.e(
                TAG,
                "swap failed: provider=${provider.id} ${tokenIn.identifier} -> ${tokenOut.identifier} " +
                    "http=${e.code()} body=$body"
            )
            throw SwapProviderError(parseProviderError(body) ?: "Couldn't create the swap. Please try again.")
        }

        val execution = route.execution ?: throw SwapRouteNotFound()
        if (!execution.method.equals("transfer", ignoreCase = true)) {
            // We only offer transfer providers, so this is defensive against a backend change.
            throw SwapProviderError("This provider isn't supported by the app.")
        }
        val depositAddress = execution.depositAddress?.takeIf { it.isNotBlank() }
            ?: throw SwapRouteNotFound()
        val uuid = route.uuid?.takeIf { it.isNotBlank() } ?: throw SwapRouteNotFound()

        Log.d(
            TAG,
            "swap OK: provider=${provider.id} uuid=$uuid attachment=${execution.attachment?.type} " +
                "paymentUri=${!execution.qr?.str.isNullOrBlank()}"
        )

        SwapIntent(
            uuid = uuid,
            depositAddress = depositAddress,
            amountIn = execution.amount?.toBigDecimalOrNull() ?: amountIn,
            tokenIn = tokenIn,
            attachmentValue = execution.attachment?.value?.takeIf { it.isNotBlank() },
            attachmentLabel = execution.attachment?.let { attachmentLabel(it.type) },
            paymentUri = execution.qr?.str?.takeIf { it.isNotBlank() },
            deeplink = deeplink(execution.qr?.str),
            secondsRemaining = route.expiresAt
                ?.let { it - System.currentTimeMillis() }
                ?.let { it / 1000 }
                ?.coerceAtLeast(0),
        )
    }

    /**
     * Poll `POST /v2/track` for the swap's status, emitting on each tick until a terminal state.
     * Transfer providers watch their own deposit address, so no inbound tx hash is needed. A 409
     * ("not trackable yet") or transient error is treated as "keep polling".
     */
    fun statusUpdates(uuid: String): Flow<SwapTrackUpdate> = flow {
        while (true) {
            val update = try {
                val resp = api.track(TrackRequestDto(uuid))
                SwapTrackUpdate(SwapStatus.fromApi(resp.status), resp.meta?.pauseReason)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                if (e.code() == 409) {
                    SwapTrackUpdate(SwapStatus.NotStarted) // recorded but not yet trackable
                } else {
                    Log.e(TAG, "track failed http=${e.code()}", e)
                    SwapTrackUpdate(SwapStatus.Unknown)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "track failed", e)
                SwapTrackUpdate(SwapStatus.Unknown)
            }

            emit(update)
            if (update.status.isTerminal) break
            delay(POLL_INTERVAL_MS)
        }
    }

    /** Wrap a payment URI in the Unstoppable pay deeplink so a tap opens the user's wallet. */
    private fun deeplink(paymentUri: String?): String? = paymentUri?.takeIf { it.isNotBlank() }?.let {
        "https://swap.unstoppable.money/pay?uri=" + URLEncoder.encode(it, "UTF-8")
    }

    private fun attachmentLabel(type: String?): String = when (type?.lowercase()) {
        "destination_tag" -> "Destination tag"
        else -> "Memo"
    }

    /** Extract the `error` message from a `{ "error": "...", "provider": "..." }` body. */
    private fun parseProviderError(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            Gson().fromJson(body, ProviderErrorDto::class.java)?.error?.takeIf { it.isNotBlank() }
        } catch (e: Throwable) {
            null
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 30_000L
        private const val TAG = "SwapDeposit"
    }
}
