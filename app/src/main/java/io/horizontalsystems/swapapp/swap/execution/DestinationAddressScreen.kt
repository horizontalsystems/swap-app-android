package io.horizontalsystems.swapapp.swap.execution

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.horizontalsystems.swapapp.R
import io.horizontalsystems.swapapp.components.HSScaffold
import io.horizontalsystems.swapapp.compose.ComposeAppTheme
import io.horizontalsystems.swapapp.compose.components.AddressInputState
import io.horizontalsystems.swapapp.compose.components.ButtonPrimaryYellow
import io.horizontalsystems.swapapp.compose.components.CoinImage
import io.horizontalsystems.swapapp.compose.components.FormsInputAddress
import io.horizontalsystems.swapapp.compose.components.HsDivider
import io.horizontalsystems.swapapp.compose.components.TextPreprocessor
import io.horizontalsystems.swapapp.compose.components.VSpacer
import io.horizontalsystems.swapapp.swap.SwapToken
import io.horizontalsystems.swapapp.swap.execution.address.check.AddressCheckManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Asks the user for an address on [token]'s chain (recipient or refund). [description] explains
 * what address is expected, the bottom button hints the required chain while the input is empty,
 * and paste/scan actions plus per-chain validation guard the input before it's handed back via
 * [onConfirm].
 */
@Composable
fun AddressInputScreen(
    token: SwapToken,
    title: String,
    description: String,
    onBack: () -> Unit,
    onConfirm: (address: String) -> Unit,
    initial: String? = null,
) {
    val context = LocalContext.current
    val history = remember { AddressHistoryStore(context) }
    // EVM tokens share one history bucket — a 0x address entered for any EVM token is valid here too.
    val addressScope = remember(token) { SwapAddressValidator.addressScope(token) }
    val recentAddresses = remember(token) { history.recent(addressScope) }
    // Seed with a previously entered address (e.g. after backing out to edit the amount) so it
    // doesn't have to be re-entered; re-seed if the passed-in value changes.
    var address by remember(initial) { mutableStateOf(initial.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    // Async blacklist/sanction screening runs after the synchronous format check passes.
    val checkManager = remember { AddressCheckManager() }
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    // The (trimmed) address that passed the full validation + screening, so Confirm can proceed
    // without re-checking. Any edit makes the on-screen address diverge from it, forcing a re-check.
    var validatedAddress by remember { mutableStateOf<String?>(null) }
    var checkJob by remember { mutableStateOf<Job?>(null) }

    /**
     * Validates [raw] (synchronous format check, then async blacklist/sanction screening) while the
     * bottom button shows a "Validating…" spinner. Runs eagerly when an address arrives whole —
     * paste, QR scan, a recent-address tap — and from the bottom button for typed input;
     * [confirmAfter] proceeds straight to [onConfirm] once the check passes. Any newer input
     * cancels a running check.
     */
    fun runValidation(raw: String, confirmAfter: Boolean = false) {
        checkJob?.cancel()
        error = null
        val trimmed = raw.trim()
        val validationError = SwapAddressValidator.validate(trimmed, token)
        if (validationError != null) {
            checking = false
            error = validationError
            return
        }
        checking = true
        checkJob = scope.launch {
            // A blacklisted/sanctioned address is blocked like an invalid one; a failed check is
            // inconclusive (null) and lets the swap proceed.
            val issue = checkManager.firstDetectedIssue(trimmed, token)
            checking = false
            if (issue != null) {
                error = issue.message(token)
            } else {
                validatedAddress = trimmed
                if (confirmAfter) {
                    history.add(addressScope, trimmed)
                    onConfirm(trimmed)
                }
            }
        }
    }

    HSScaffold(
        title = title,
        onBack = onBack,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                ButtonPrimaryYellow(
                    modifier = Modifier.fillMaxWidth(),
                    // While empty, the disabled button doubles as a hint for the required chain.
                    title = when {
                        checking -> "Validating address…"
                        address.isBlank() -> "Enter ${token.networkName} Address"
                        else -> "Next"
                    },
                    // Disabled while validating and while the current input is known-bad (invalid
                    // format or blacklisted); any edit clears [error] and re-enables it.
                    enabled = address.isNotBlank() && !checking && error == null,
                    loadingIndicator = checking,
                    onClick = {
                        val confirmed = address.trim()
                        if (confirmed == validatedAddress) {
                            // Already validated (eagerly, on paste/scan) — proceed straight away.
                            history.add(addressScope, confirmed)
                            onConfirm(confirmed)
                        } else {
                            // Typed or edited input — validate now and proceed on success.
                            runValidation(address, confirmAfter = true)
                        }
                    },
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = description,
                style = ComposeAppTheme.typography.subhead,
                color = ComposeAppTheme.colors.grey,
            )

            VSpacer(24.dp)

            val currentError = error
            FormsInputAddress(
                value = address,
                hint = "Address",
                state = when {
                    checking -> AddressInputState.Loading
                    currentError != null -> AddressInputState.Error(currentError)
                    address.isNotBlank() && address.trim() == validatedAddress -> AddressInputState.Success
                    else -> null
                },
                // The design keeps the field clean — errors show below it, progress on the button.
                showStateIcon = false,
                textPreprocessor = scannedAddressPreprocessor,
                scanPrompt = "Scan a ${token.networkName} address",
                onValueChange = {
                    // A manual edit supersedes any in-flight check of the old value.
                    checkJob?.cancel()
                    checking = false
                    address = it
                    error = null
                },
                // Paste / scan deliver a complete address — validate it eagerly.
                onWholeInput = {
                    address = it
                    runValidation(it)
                },
            )

            // Previously used addresses for this token — tap to reuse. The entry matching the
            // current input (typed, pasted, or tapped) is marked with a checkmark.
            if (recentAddresses.isNotEmpty()) {
                VSpacer(28.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ComposeAppTheme.colors.lawrence)
                ) {
                    Text(
                        text = "Recently Used",
                        style = ComposeAppTheme.typography.headline2,
                        color = ComposeAppTheme.colors.leah,
                        modifier = Modifier.padding(16.dp),
                    )
                    recentAddresses.forEach { recent ->
                        HsDivider()
                        RecentAddressRow(
                            recent = recent,
                            logoUrl = token.logoUrl,
                            selected = address.trim() == recent.address,
                            onClick = {
                                address = recent.address
                                runValidation(address)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One "Recently used" entry: token tile, truncated address and when it was last used; [selected]
 * (the entry matches the current input) is indicated with a checkmark.
 */
@Composable
private fun RecentAddressRow(
    recent: RecentAddress,
    logoUrl: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        CoinImage(
            url = logoUrl,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = shortenAddress(recent.address),
                style = ComposeAppTheme.typography.headline2,
                color = ComposeAppTheme.colors.leah,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            recent.usedAt?.let { usedAt ->
                Text(
                    text = "used ${relativeTime(usedAt)}",
                    style = ComposeAppTheme.typography.caption,
                    color = ComposeAppTheme.colors.grey,
                )
            }
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                painter = painterResource(R.drawable.selector_checked_20),
                contentDescription = "Selected",
                tint = ComposeAppTheme.colors.jacob,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Human-readable "… ago" for [timestamp] (e.g. "2 days ago", "5 minutes ago", "just now"). */
private fun relativeTime(timestamp: Long): CharSequence {
    val now = System.currentTimeMillis()
    if (now - timestamp < DateUtils.MINUTE_IN_MILLIS) return "just now"
    return DateUtils.getRelativeTimeSpanString(
        timestamp,
        now,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    )
}

/**
 * Extracts a plain wallet address from pasted or scanned input. Wallets often encode payment URIs
 * (BIP21 "bitcoin:bc1…?amount=…", EIP-681 "ethereum:0x…@1?value=…") rather than a bare address, so
 * we strip a leading "scheme:" and any "@chainId" / "?query" suffix.
 */
private val scannedAddressPreprocessor = object : TextPreprocessor {
    override fun process(text: String): String {
        var value = text.trim()
        val colon = value.indexOf(':')
        // A short leading token followed by ':' is a URI scheme (bitcoin:, ethereum:, …), not part
        // of an address — drop it. Bare "0x…" / bech32 / base58 addresses contain no colon.
        if (colon in 1..12) {
            value = value.substring(colon + 1)
        }
        return value.substringBefore('?').substringBefore('@').trim()
    }
}

/** Middle-truncates a long address (e.g. "0x1234abcd…9f8e7d6c") so suggestions stay distinguishable. */
private fun shortenAddress(address: String): String =
    if (address.length <= 20) address else address.take(10) + "…" + address.takeLast(8)
