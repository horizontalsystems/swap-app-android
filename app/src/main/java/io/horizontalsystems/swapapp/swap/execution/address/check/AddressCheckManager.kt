package io.horizontalsystems.swapapp.swap.execution.address.check

import io.horizontalsystems.swapapp.BuildConfig
import io.horizontalsystems.swapapp.swap.SwapToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** The kinds of address screening the swap app runs, ported from the wallet's `AddressCheckType`. */
enum class AddressCheckType {
    Blacklist,
    Sanction;

    /**
     * User-facing reason shown when a check flags an address, used to block confirmation. The
     * Blacklist wording stays source-neutral — the verdict can come from the token contract's
     * on-chain freeze list or from HashDit's risk score, so it must not claim "the contract".
     */
    fun message(token: SwapToken): String = when (this) {
        Blacklist -> "This address is blacklisted and can't receive ${token.ticker}."
        Sanction -> "This address appears on a sanctions list and can't be used."
    }
}

/**
 * Runs the available address checks for a token, ported from the reference wallet's
 * `AddressCheckManager`. HashDit and Chainalysis are configured from BuildConfig and stay inert until
 * their API keys are supplied; the on-chain contract blacklist checks always run for supported tokens.
 *
 * A check that throws (RPC/network failure) is treated as *inconclusive* and does not block the user —
 * only a definitive "not clear" verdict does. This keeps a flaky RPC from making swaps impossible.
 */
class AddressCheckManager(
    hashDitBaseUrl: String = BuildConfig.HASHDIT_BASE_URL,
    hashDitApiKey: String = BuildConfig.HASHDIT_API_KEY,
    chainalysisBaseUrl: String = BuildConfig.CHAINALYSIS_BASE_URL,
    chainalysisApiKey: String = BuildConfig.CHAINALYSIS_API_KEY,
) {
    private val checkers: Map<AddressCheckType, AddressChecker> = mapOf(
        AddressCheckType.Blacklist to BlacklistAddressChecker(
            HashDitAddressValidator(hashDitBaseUrl, hashDitApiKey)
        ),
        AddressCheckType.Sanction to SanctionAddressChecker(
            ChainalysisAddressValidator(chainalysisBaseUrl, chainalysisApiKey)
        ),
    )

    fun availableCheckTypes(token: SwapToken): List<AddressCheckType> =
        checkers.mapNotNull { (type, checker) -> if (checker.supports(token)) type else null }

    /**
     * Runs every applicable check and returns the first that flags [address] as not clear, or null if
     * all pass (or are inconclusive). Network/RPC errors and timeouts are swallowed as inconclusive,
     * but coroutine cancellation is rethrown — otherwise a cancelled check (user backed out of the
     * screen) would complete as "clear" and the caller's confirm continuation would still run.
     */
    suspend fun firstDetectedIssue(address: String, token: SwapToken): AddressCheckType? {
        for (type in availableCheckTypes(token)) {
            val clear = try {
                // Bound each check so slow endpoints can't pin the UI in "Checking…" for the full
                // 15s-connect + 15s-read OkHttp timeouts per request; a timeout is inconclusive.
                withTimeoutOrNull(CHECK_TIMEOUT_MS) {
                    checkers[type]?.isClear(address, token)
                } ?: true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                true // inconclusive — don't block on a failed check
            }
            if (!clear) return type
        }
        return null
    }

    companion object {
        private const val CHECK_TIMEOUT_MS = 10_000L
    }
}