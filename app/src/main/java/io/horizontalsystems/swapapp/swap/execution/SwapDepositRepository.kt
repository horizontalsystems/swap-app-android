package io.horizontalsystems.swapapp.swap.execution

import io.horizontalsystems.swapapp.swap.SwapProvider
import io.horizontalsystems.swapapp.swap.SwapQuoteRepository
import io.horizontalsystems.swapapp.swap.SwapToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.math.BigDecimal

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
 * A registered swap: the real deposit address the user must send to, the exact amount, and (for
 * THORChain) the [memo] that must accompany the deposit.
 */
data class SwapIntent(
    val reference: String,
    val depositAddress: String,
    val amountIn: BigDecimal,
    val tokenIn: SwapToken,
    val memo: String?,
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
) {

    /** Register the swap and return its real deposit details. */
    suspend fun createIntent(
        tokenIn: SwapToken,
        tokenOut: SwapToken,
        amountIn: BigDecimal,
        provider: SwapProvider,
        destinationAddress: String,
    ): SwapIntent {
        val deposit = quoteRepository.createDeposit(
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amountIn = amountIn,
            providerId = provider.id,
            destinationAddress = destinationAddress,
        )

        return SwapIntent(
            reference = deposit.providerSwapId ?: deposit.depositAddress,
            depositAddress = deposit.depositAddress,
            amountIn = deposit.sendAmount,
            tokenIn = tokenIn,
            memo = deposit.memo,
            secondsRemaining = deposit.secondsRemaining,
        )
    }

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
    }
}
