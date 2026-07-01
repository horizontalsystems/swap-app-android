package io.horizontalsystems.swapapp.swap.execution

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.horizontalsystems.swapapp.R
import io.horizontalsystems.swapapp.components.HSScaffold
import io.horizontalsystems.swapapp.compose.ComposeAppTheme
import io.horizontalsystems.swapapp.compose.components.ButtonPrimaryYellow
import io.horizontalsystems.swapapp.compose.components.CoinImage
import io.horizontalsystems.swapapp.compose.components.HsDivider
import io.horizontalsystems.swapapp.compose.components.HsIconButton
import io.horizontalsystems.swapapp.compose.components.VSpacer
import io.horizontalsystems.swapapp.swap.SwapToken
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * Asks the user for an address on [token]'s chain (recipient or refund). A token/network pill makes
 * the required chain explicit, [hint] explains what address is expected, and paste/scan actions plus
 * per-chain validation guard the input before it's handed back via [onConfirm].
 */
@Composable
fun AddressInputScreen(
    token: SwapToken,
    title: String,
    heading: String,
    description: String,
    hint: String,
    onBack: () -> Unit,
    onConfirm: (address: String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val history = remember { AddressHistoryStore(context) }
    // EVM tokens share one history bucket — a 0x address entered for any EVM token is valid here too.
    val addressScope = remember(token) { SwapAddressValidator.addressScope(token) }
    val recentAddresses = remember(token) { history.recent(addressScope) }
    var address by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    // Launches the zxing scanner; on a successful scan we extract the plain address from whatever
    // the QR encodes (a bare address, or a BIP21/EIP-681 URI like "bitcoin:…" / "ethereum:…").
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let {
            address = parseScannedAddress(it)
            error = null
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
                    title = "Confirm",
                    enabled = address.isNotBlank(),
                    onClick = {
                        val validationError = SwapAddressValidator.validate(address, token)
                        if (validationError == null) {
                            val confirmed = address.trim()
                            history.add(addressScope, confirmed)
                            onConfirm(confirmed)
                        } else {
                            error = validationError
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
            // Token/network pill — the same ticker (e.g. USDC) exists on many chains, so spell out
            // which network this address must be on.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(ComposeAppTheme.colors.blade)
                    .padding(start = 8.dp, top = 8.dp, end = 16.dp, bottom = 8.dp)
            ) {
                CoinImage(
                    url = token.logoUrl,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${token.ticker} · ${token.networkName}",
                    style = ComposeAppTheme.typography.headline2,
                    color = ComposeAppTheme.colors.leah,
                )
            }

            VSpacer(24.dp)

            Text(
                text = heading,
                style = ComposeAppTheme.typography.title3,
                color = ComposeAppTheme.colors.leah,
            )
            VSpacer(8.dp)
            Text(
                text = description,
                style = ComposeAppTheme.typography.subhead,
                color = ComposeAppTheme.colors.grey,
            )

            VSpacer(24.dp)

            // Address input with paste + scan actions.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ComposeAppTheme.colors.lawrence)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = address,
                        onValueChange = {
                            address = it
                            error = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = ComposeAppTheme.typography.subhead.copy(
                            color = ComposeAppTheme.colors.leah
                        ),
                        cursorBrush = SolidColor(ComposeAppTheme.colors.jacob),
                        decorationBox = { inner ->
                            if (address.isEmpty()) {
                                Text(
                                    text = "Paste address…",
                                    style = ComposeAppTheme.typography.subhead,
                                    color = ComposeAppTheme.colors.grey,
                                )
                            }
                            inner()
                        },
                    )
                }
                Spacer(Modifier.width(8.dp))
                InputActionButton(
                    icon = R.drawable.paste_24,
                    contentDescription = "Paste",
                    onClick = {
                        clipboard.getText()?.text?.let {
                            address = it.trim()
                            error = null
                        }
                    },
                )
                Spacer(Modifier.width(8.dp))
                InputActionButton(
                    icon = R.drawable.qr_scan_24,
                    contentDescription = "Scan QR code",
                    onClick = {
                        scanLauncher.launch(
                            ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt("Scan a ${token.networkName} address")
                                setBeepEnabled(false)
                                setOrientationLocked(false)
                            }
                        )
                    },
                )
            }

            error?.let {
                VSpacer(8.dp)
                Text(
                    text = it,
                    style = ComposeAppTheme.typography.subhead,
                    color = ComposeAppTheme.colors.lucian,
                )
            }

            VSpacer(12.dp)

            // Hint clarifying what kind of address is expected on this chain.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.info_20),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.grey,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = hint,
                    style = ComposeAppTheme.typography.subhead,
                    color = ComposeAppTheme.colors.grey,
                )
            }

            // Previously used addresses for this token — tap to reuse.
            if (recentAddresses.isNotEmpty()) {
                VSpacer(28.dp)
                Text(
                    text = "RECENTLY USED",
                    style = ComposeAppTheme.typography.captionSB,
                    letterSpacing = 0.8.sp,
                    color = ComposeAppTheme.colors.grey,
                )
                VSpacer(10.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, ComposeAppTheme.colors.blade, RoundedCornerShape(16.dp))
                ) {
                    recentAddresses.forEachIndexed { index, recent ->
                        if (index > 0) HsDivider()
                        RecentAddressRow(
                            recent = recent,
                            logoUrl = token.logoUrl,
                            onClick = {
                                address = recent.address
                                error = null
                            },
                        )
                    }
                }
            }
        }
    }
}

/** One "Recently used" entry: token tile, truncated address, when it was last used, and a chevron. */
@Composable
private fun RecentAddressRow(
    recent: RecentAddress,
    logoUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ComposeAppTheme.colors.blade),
        ) {
            CoinImage(
                url = logoUrl,
                // Desaturated so this suggestion reads as secondary, not a primary action.
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                modifier = Modifier
                    .size(24.dp)
                    .alpha(0.6f),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = shortenAddress(recent.address),
                style = ComposeAppTheme.typography.headline2,
                color = ComposeAppTheme.colors.grey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            recent.usedAt?.let { usedAt ->
                Text(
                    text = "Used ${relativeTime(usedAt)}",
                    style = ComposeAppTheme.typography.caption,
                    color = ComposeAppTheme.colors.grey,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            painter = painterResource(R.drawable.chevron_right_20),
            contentDescription = null,
            tint = ComposeAppTheme.colors.grey,
            modifier = Modifier.size(20.dp),
        )
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

/** A rounded icon button used for the paste / scan actions inside the address field. */
@Composable
private fun InputActionButton(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    HsIconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ComposeAppTheme.colors.blade.copy(alpha = 0.5f)),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = ComposeAppTheme.colors.grey,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Extracts a plain wallet address from scanned QR contents. Wallets often encode payment URIs
 * (BIP21 "bitcoin:bc1…?amount=…", EIP-681 "ethereum:0x…@1?value=…") rather than a bare address, so
 * we strip a leading "scheme:" and any "@chainId" / "?query" suffix.
 */
private fun parseScannedAddress(raw: String): String {
    var value = raw.trim()
    val colon = value.indexOf(':')
    // A short leading token followed by ':' is a URI scheme (bitcoin:, ethereum:, …), not part of
    // an address — drop it. Bare "0x…" / bech32 / base58 addresses contain no colon.
    if (colon in 1..12) {
        value = value.substring(colon + 1)
    }
    return value.substringBefore('?').substringBefore('@').trim()
}

/** Middle-truncates a long address (e.g. "0x1234abcd…9f8e7d6c") so suggestions stay distinguishable. */
private fun shortenAddress(address: String): String =
    if (address.length <= 20) address else address.take(10) + "…" + address.takeLast(8)
