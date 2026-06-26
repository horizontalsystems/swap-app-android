package io.horizontalsystems.swapapp.swap.execution

import android.util.Log
import io.horizontalsystems.swapapp.swap.SwapProvider
import io.horizontalsystems.swapapp.swap.SwapQuoteRepository
import io.horizontalsystems.swapapp.swap.SwapToken
import io.horizontalsystems.swapapp.swap.api.MemolessApi
import io.horizontalsystems.swapapp.swap.api.MemolessApiClient
import io.horizontalsystems.swapapp.swap.api.MemolessPreflightRequest
import io.horizontalsystems.swapapp.swap.api.MemolessRegisterRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.net.URLEncoder

/**
 * The execution-flow status. The backend hands the user a deposit address; the swap then progresses
 * through these stages.
 */
enum class SwapStatus(val order: Int, val label: String) {
    AwaitingDeposit(0, "Awaiting Deposit…"),
    ConfirmingOnBlockchain(1, "Confirming on Blockchain…"),
    Exchanging(2, "Exchanging…"),
    SendingToWallet(3, "Sending to your wallet…"),
    Completed(4, "Completed!");

    companion object {
        val ordered = entries.sortedBy { it.order }
    }
}

/**
 * A registered swap: the real deposit address the user must send to and the exact amount.
 *
 * Deposits are conveyed two ways, mirroring the swap-bot:
 *  - [paymentUri] — a BIP21 payment URI (`bitcoin:bc1…?amount=…`) that the QR encodes and that
 *    [deeplink] wraps, so a tap/scan opens the user's wallet pre-filled.
 *  - [memo] — only set for a non-memoless THORChain fallback, where the memo must accompany the
 *    send manually. Normally null: the memoless flow folds the memo into [amountIn] instead.
 */
data class SwapIntent(
    val reference: String,
    val depositAddress: String,
    val amountIn: BigDecimal,
    val tokenIn: SwapToken,
    val memo: String?,
    val paymentUri: String?,
    val deeplink: String?,
    val secondsRemaining: Long?,
)

/**
 * Backs the swap execution flow.
 *
 * [createIntent] is REAL: it registers the swap via a non-dry `/quote` (SwapQuoteRepository) and
 * returns the provider's actual inbound deposit address + memo + expiry.
 *
 * [statusUpdates] is still a MOCK timer progression — the dev API exposes no JSON status endpoint
 * (live tracking is a hosted web page), so per the project decision the in-app 5-stage tracker
 * stays simulated until a status feed is available.
 */
class SwapDepositRepository(
    private val quoteRepository: SwapQuoteRepository = SwapQuoteRepository(),
    private val memolessApi: MemolessApi = MemolessApiClient.api,
) {

    /** Register the swap and return its real deposit details. */
    suspend fun createIntent(
        tokenIn: SwapToken,
        tokenOut: SwapToken,
        amountIn: BigDecimal,
        provider: SwapProvider,
        destinationAddress: String,
        refundAddress: String?,
    ): SwapIntent {
        val deposit = quoteRepository.createDeposit(
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amountIn = amountIn,
            providerId = provider.id,
            destinationAddress = destinationAddress,
            refundAddress = refundAddress,
        )

        // THORChain deposits carry a memo. Instead of asking the user to attach it (error-prone),
        // run the memoless flow: it bumps the send amount by a few sub-units that encode the swap
        // reference, so the deposit matches without a memo. Falls back to the memo flow on failure.
        if (provider.id == "THORCHAIN" && deposit.memo != null) {
            runMemoless(tokenIn.identifier, deposit.memo, amountIn)?.let { memoless ->
                return SwapIntent(
                    reference = memoless.reference,
                    depositAddress = memoless.inboundAddress ?: deposit.depositAddress,
                    amountIn = memoless.sendAmount,
                    tokenIn = tokenIn,
                    memo = null,
                    paymentUri = memoless.paymentUri,
                    deeplink = deeplink(memoless.paymentUri),
                    secondsRemaining = memoless.secondsRemaining ?: deposit.secondsRemaining,
                )
            }
        }

        return SwapIntent(
            reference = deposit.providerSwapId ?: deposit.depositAddress,
            depositAddress = deposit.depositAddress,
            amountIn = deposit.sendAmount,
            tokenIn = tokenIn,
            memo = deposit.memo,
            paymentUri = deposit.paymentUri,
            deeplink = deeplink(deposit.paymentUri),
            secondsRemaining = deposit.secondsRemaining,
        )
    }

    /**
     * Register + preflight the memoless flow for a THORChain [memo]. Returns null (so the caller
     * falls back to the memo flow) if the asset isn't memoless-supported or the service errors.
     */
    private suspend fun runMemoless(
        asset: String,
        memo: String,
        amountIn: BigDecimal,
    ): MemolessResult? = withContext(Dispatchers.IO) {
        try {
            val requested = amountIn.stripTrailingZeros().toPlainString()
            val register = memolessApi.register(MemolessRegisterRequest(asset, memo, requested))
            val reference = register.reference ?: return@withContext null
            val suggested = register.suggestedInAssetAmount ?: return@withContext null

            val preflight = memolessApi.preflight(MemolessPreflightRequest(asset, reference, suggested))
            val data = preflight.data ?: return@withContext null

            MemolessResult(
                reference = reference,
                sendAmount = suggested.toBigDecimalOrNull() ?: amountIn,
                inboundAddress = data.inboundAddress?.takeIf { it.isNotBlank() },
                paymentUri = data.qrCode?.takeIf { it.isNotBlank() },
                secondsRemaining = data.secondsRemaining,
            )
        } catch (e: Throwable) {
            Log.e(TAG, "memoless flow failed; falling back to memo deposit", e)
            null
        }
    }

    /** Wrap a payment URI in the Unstoppable pay deeplink so a tap opens the user's wallet. */
    private fun deeplink(paymentUri: String?): String? = paymentUri?.let {
        "https://swap.unstoppable.money/pay?uri=" + URLEncoder.encode(it, "UTF-8")
    }

    private data class MemolessResult(
        val reference: String,
        val sendAmount: BigDecimal,
        val inboundAddress: String?,
        val paymentUri: String?,
        val secondsRemaining: Long?,
    )

    /**
     * Poll the backend for the swap status. MOCK: advances one stage at a time until
     * [SwapStatus.Completed]. A real impl would `GET /status?reference=…` on each tick.
     */
    fun statusUpdates(reference: String): Flow<SwapStatus> = flow {
        for (status in SwapStatus.ordered) {
            emit(status)
            if (status != SwapStatus.Completed) {
                delay(STAGE_INTERVAL_MS)
            }
        }
    }

    companion object {
        private const val STAGE_INTERVAL_MS = 3000L
        private const val TAG = "SwapDeposit"
    }
}
