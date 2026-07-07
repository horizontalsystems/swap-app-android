package io.horizontalsystems.swapapp.swap.history

import android.content.Context
import com.google.gson.Gson
import io.horizontalsystems.swapapp.swap.execution.SwapStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A token as it appeared in a swap, snapshotted so history renders without re-resolving the token universe. */
data class RecordToken(
    val ticker: String,
    val name: String,
    /** Human-readable network (e.g. "Optimism", "Ethereum"), from [io.horizontalsystems.swapapp.swap.SwapToken.networkName]. */
    val network: String,
    val logoUrl: String?,
    val chainId: String?,
)

/**
 * One committed swap, persisted so the user can revisit it after the live tracking screen is gone.
 * Amounts and fiat values are stored as strings (as they were shown at swap time); [status] is the
 * last known [SwapStatus] name, refreshed live whenever the swap is open. [uuid] is the `/v2/track`
 * handle used to re-track a still-in-progress swap.
 */
data class SwapRecord(
    val uuid: String,
    val createdAt: Long,
    val providerId: String,
    val providerTitle: String,
    val tokenIn: RecordToken,
    val tokenOut: RecordToken,
    val amountIn: String,
    val amountOut: String?,
    val fiatIn: String?,
    val fiatOut: String?,
    val status: String,
    // Deposit details snapshotted at creation (they only exist in the swap-creation response, not
    // in /v2/track), so a swap still awaiting its deposit can reopen the deposit-instructions
    // screen from history. All null on records saved before these fields existed.
    val destinationAddress: String? = null,
    val depositAddress: String? = null,
    val attachmentValue: String? = null,
    val attachmentLabel: String? = null,
    val paymentUri: String? = null,
    val deeplink: String? = null,
    val expiresAtMillis: Long? = null,
    val trackUrl: String? = null,
) {
    val swapStatus: SwapStatus
        get() = try {
            SwapStatus.valueOf(status)
        } catch (e: IllegalArgumentException) {
            SwapStatus.Unknown
        }

    /** Still awaiting the user's deposit on a not-yet-expired order, with stored deposit details —
     *  the state in which history can reopen the deposit-instructions screen. */
    val canResumeDeposit: Boolean
        get() = swapStatus == SwapStatus.NotStarted && depositAddress != null &&
            (expiresAtMillis == null || expiresAtMillis > System.currentTimeMillis())
}

/**
 * Persists the user's swap history in SharedPreferences (JSON), newest first and capped at [MAX].
 *
 * The record list is exposed as a process-wide [records] StateFlow so the history list, an open
 * swap-info screen, and the live tracking flow all see the same data and update in lock-step. All
 * instances share that flow and the same prefs file, so any [SwapHistoryStore] built from any
 * context reads and writes the one history.
 */
class SwapHistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        synchronized(lock) {
            if (!loaded) {
                _records.value = read()
                loaded = true
                // Orders that expired while the app was closed are dead — mark them right away.
                expireStaleDeposits()
            }
        }
    }

    val records: StateFlow<List<SwapRecord>> = _records.asStateFlow()

    fun get(uuid: String): SwapRecord? = _records.value.firstOrNull { it.uuid == uuid }

    /** Add (or replace, by uuid) a swap, moving it to the front. */
    fun record(record: SwapRecord) = mutate { list ->
        (listOf(record) + list.filterNot { it.uuid == record.uuid }).take(MAX)
    }

    /** Update the last-known status of a tracked swap; no-op if it isn't recorded. An [SwapStatus.Expired]
     *  record stays expired when `/v2/track` still reports `not_started` (the deposit never arrived);
     *  any real progress (the deposit landed after all) still upgrades it. */
    fun updateStatus(uuid: String, status: SwapStatus) = mutate { list ->
        list.map {
            when {
                it.uuid != uuid -> it
                it.swapStatus == SwapStatus.Expired && status == SwapStatus.NotStarted -> it
                else -> it.copy(status = status.name)
            }
        }
    }

    /** Mark swaps whose order expired before any deposit was seen as [SwapStatus.Expired]. */
    fun expireStaleDeposits(now: Long = System.currentTimeMillis()) = mutate { list ->
        list.map {
            if (it.swapStatus == SwapStatus.NotStarted && it.expiresAtMillis != null && it.expiresAtMillis <= now) {
                it.copy(status = SwapStatus.Expired.name)
            } else {
                it
            }
        }
    }

    private fun mutate(transform: (List<SwapRecord>) -> List<SwapRecord>) {
        synchronized(lock) {
            val updated = transform(_records.value)
            if (updated == _records.value) return
            _records.value = updated
            prefs.edit().putString(KEY, gson.toJson(updated)).apply()
        }
    }

    private fun read(): List<SwapRecord> = try {
        prefs.getString(KEY, null)
            ?.let { gson.fromJson(it, Array<SwapRecord>::class.java).toList() }
            ?: emptyList()
    } catch (e: Throwable) {
        emptyList()
    }

    companion object {
        private const val PREFS = "swap_history"
        private const val KEY = "records"
        private const val MAX = 100

        private val gson = Gson()
        private val lock = Any()
        private var loaded = false
        private val _records = MutableStateFlow<List<SwapRecord>>(emptyList())
    }
}
