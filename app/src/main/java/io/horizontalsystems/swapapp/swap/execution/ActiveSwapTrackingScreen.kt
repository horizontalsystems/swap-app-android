package io.horizontalsystems.swapapp.swap.execution

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import io.horizontalsystems.swapapp.R
import io.horizontalsystems.swapapp.components.HSScaffold
import io.horizontalsystems.swapapp.compose.ComposeAppTheme
import io.horizontalsystems.swapapp.compose.components.ButtonPrimaryYellow
import io.horizontalsystems.swapapp.compose.components.ButtonSecondary
import io.horizontalsystems.swapapp.compose.components.CoinImage
import io.horizontalsystems.swapapp.compose.components.HSpacer
import io.horizontalsystems.swapapp.compose.components.VSpacer
import io.horizontalsystems.swapapp.swap.formatCoinAmount
import io.horizontalsystems.swapapp.swap.formatFiat
import kotlinx.coroutines.delay
import java.math.BigDecimal

/**
 * Deposit instructions for a committed swap: which token is swapping to which (a spinner over the
 * sell-token icon while the deposit hasn't arrived), the order expiry countdown when the backend
 * set one, and the numbered steps to make the swap happen (copy address / copy amount / track by
 * link). Status keeps polling `POST /v2/track` via [SwapExecutionViewModel] underneath.
 */
@Composable
fun ActiveSwapTrackingScreen(
    uiState: ActiveSwapUiState,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onRetry: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copyToClipboard = { value: String ->
        clipboard.setText(AnnotatedString(value))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
    val ready = !uiState.creatingIntent && uiState.error == null && uiState.depositAddress != null

    HSScaffold(
        title = if (ready) "Deposit Instruction" else "Deposit Address",
        onBack = onBack,
        bottomBar = {
            if (ready) {
                Box(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)
                ) {
                    ButtonPrimaryYellow(
                        modifier = Modifier.fillMaxWidth(),
                        title = "Go to Main",
                        onClick = onDone,
                    )
                }
            }
        },
    ) {
        when {
            uiState.error != null -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                VSpacer(48.dp)
                Text(
                    text = uiState.error,
                    style = ComposeAppTheme.typography.subhead,
                    color = ComposeAppTheme.colors.lucian,
                    textAlign = TextAlign.Center,
                )
                VSpacer(16.dp)
                ButtonPrimaryYellow(title = "Try again", onClick = onRetry)
            }

            !ready -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ComposeAppTheme.colors.jacob)
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                DepositInstructions(
                    uiState = uiState,
                    onCopy = copyToClipboard,
                    onOpenLink = { url ->
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "No app found to open the link", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DepositInstructions(
    uiState: ActiveSwapUiState,
    onCopy: (String) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val depositAddress = uiState.depositAddress ?: return

    VSpacer(12.dp)
    SwapHeader(uiState)

    statusBanner(uiState)?.let { (message, color) ->
        VSpacer(16.dp)
        Text(text = message, style = ComposeAppTheme.typography.subhead, color = color)
    }

    if (uiState.expiresAtMillis != null && !uiState.status.isTerminal) {
        VSpacer(20.dp)
        ExpirationCard(expiresAtMillis = uiState.expiresAtMillis)
    }

    var step = 1

    VSpacer(20.dp)
    StepCard(
        number = step++,
        text = "Copy the address and paste it on another wallet from which you are going to send funds",
    ) {
        var showQr by remember { mutableStateOf(false) }
        StepPill(
            text = middleTruncate(depositAddress, edge = 6),
            icon = R.drawable.copy_20,
            onClick = { onCopy(depositAddress) },
        )
        StepPill(icon = R.drawable.ic_qr_scan_20, onClick = { showQr = true })
        if (showQr) {
            QrDialog(
                content = uiState.paymentUri ?: depositAddress,
                caption = middleTruncate(depositAddress),
                onDismiss = { showQr = false },
            )
        }
    }

    // Destination tag / memo — required by some chains; the deposit is lost without it.
    uiState.attachmentValue?.let { attachment ->
        val label = (uiState.attachmentLabel ?: "Memo").lowercase()
        VSpacer(12.dp)
        StepCard(
            number = step++,
            text = "Copy the $label and include it in the transaction, otherwise the funds will be lost",
        ) {
            StepPill(text = attachment, icon = R.drawable.copy_20, onClick = { onCopy(attachment) })
        }
    }

    val exactAmount = formatAmount(uiState.amountIn)
    VSpacer(12.dp)
    StepCard(number = step++, text = "Copy the amount you need to send") {
        StepPill(text = exactAmount, icon = R.drawable.copy_20, onClick = { onCopy(exactAmount) })
    }

    uiState.trackUrl?.let { trackUrl ->
        VSpacer(12.dp)
        StepCard(number = step, text = "Send and track the transaction progress via the link") {
            StepPill(text = "Link", icon = R.drawable.copy_20, onClick = { onCopy(trackUrl) })
            StepPill(text = "Go to Link", icon = R.drawable.ic_globe_20, onClick = { onOpenLink(trackUrl) })
        }
    }

    VSpacer(24.dp)
}

/** Terminal/paused states still deserve a word now that the step tracker is gone. */
@Composable
private fun statusBanner(uiState: ActiveSwapUiState): Pair<String, Color>? = when {
    uiState.completed -> "Completed — your funds are on the way." to ComposeAppTheme.colors.remus
    uiState.status == SwapStatus.Refunded ->
        "Swap failed — your funds were refunded." to ComposeAppTheme.colors.lucian
    uiState.status == SwapStatus.Failed -> "Swap failed." to ComposeAppTheme.colors.lucian
    uiState.status == SwapStatus.ActionRequired ->
        actionRequiredMessage(uiState.pauseReason) to ComposeAppTheme.colors.lucian
    else -> null
}

/** From-token → to-token. A spinner circles the (dimmed) sell token while its deposit is awaited. */
@Composable
private fun SwapHeader(uiState: ActiveSwapUiState) {
    // Until the deposit is seen on-chain the left icon is dimmed with a spinner around it.
    val awaitingDeposit = !uiState.status.isTerminal && uiState.status.stageIndex == 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            CoinImage(
                url = uiState.tokenInLogo,
                modifier = Modifier
                    .size(40.dp)
                    .alpha(if (awaitingDeposit) 0.35f else 1f),
            )
            if (awaitingDeposit) {
                CircularProgressIndicator(
                    modifier = Modifier.size(44.dp),
                    color = ComposeAppTheme.colors.jacob,
                    strokeWidth = 2.dp,
                )
            }
        }
        HSpacer(12.dp)
        Column {
            Text(
                text = uiState.tokenInCode,
                style = ComposeAppTheme.typography.headline2,
                color = ComposeAppTheme.colors.leah,
            )
            Text(
                text = uiState.tokenInNetwork,
                style = ComposeAppTheme.typography.subhead,
                color = ComposeAppTheme.colors.grey,
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.arrow_m_left_24),
                contentDescription = null,
                tint = ComposeAppTheme.colors.grey,
                // The only arrow asset points left; mirror it to point at the buy token.
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer(scaleX = -1f),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val amountOut = uiState.amountOut
            Text(
                text = if (amountOut != null) {
                    "${formatCoinAmount(amountOut, maxDecimals = 8)} ${uiState.tokenOutCode}"
                } else {
                    uiState.tokenOutCode
                },
                style = ComposeAppTheme.typography.headline2,
                color = ComposeAppTheme.colors.leah,
            )
            uiState.fiatOut?.let {
                Text(
                    text = formatFiat(it),
                    style = ComposeAppTheme.typography.subhead,
                    color = ComposeAppTheme.colors.grey,
                )
            }
        }
        HSpacer(12.dp)
        CoinImage(url = uiState.tokenOutLogo, modifier = Modifier.size(40.dp))
    }
}

/** Orange-bordered warning card with a live "Expire in" countdown. */
@Composable
private fun ExpirationCard(expiresAtMillis: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expiresAtMillis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val remainingMillis = expiresAtMillis - now
    val expired = remainingMillis <= 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, ComposeAppTheme.colors.jacob), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_attention_20),
                contentDescription = null,
                tint = ComposeAppTheme.colors.jacob,
                modifier = Modifier.size(20.dp),
            )
            HSpacer(8.dp)
            Text(
                text = "Attention!",
                style = ComposeAppTheme.typography.headline2,
                color = ComposeAppTheme.colors.jacob,
            )
        }
        VSpacer(8.dp)
        Text(
            text = "If the transaction is not received during this time, the deposit will not be made.",
            style = ComposeAppTheme.typography.subhead,
            color = ComposeAppTheme.colors.leah,
        )
        VSpacer(12.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Expire in:",
                style = ComposeAppTheme.typography.subhead,
                color = ComposeAppTheme.colors.grey,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.clock_24),
                contentDescription = null,
                tint = if (expired) ComposeAppTheme.colors.lucian else ComposeAppTheme.colors.leah,
                modifier = Modifier.size(20.dp),
            )
            HSpacer(6.dp)
            Text(
                text = if (expired) "Expired" else formatRemaining(remainingMillis),
                style = ComposeAppTheme.typography.headline1,
                color = if (expired) ComposeAppTheme.colors.lucian else ComposeAppTheme.colors.leah,
            )
        }
    }
}

/** Ticking countdown: `2h 03m 45s` above an hour, `3m 45s` below it, `45s` in the last minute. */
private fun formatRemaining(millis: Long): String {
    val totalSeconds = (millis + 999) / 1_000 // round up so it never reads 0s while time is left
    val hours = totalSeconds / 3_600
    val minutes = totalSeconds % 3_600 / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${"%02d".format(minutes)}m ${"%02d".format(seconds)}s"
        minutes > 0 -> "${minutes}m ${"%02d".format(seconds)}s"
        else -> "${seconds}s"
    }
}

/** Numbered instruction card: orange number badge + text, action pills beneath the text. */
@Composable
private fun StepCard(
    number: Int,
    text: String,
    actions: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ComposeAppTheme.colors.lawrence)
            .padding(16.dp)
    ) {
        Row {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(ComposeAppTheme.colors.jacob),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = number.toString(),
                    style = ComposeAppTheme.typography.subheadSB,
                    color = ComposeAppTheme.colors.white,
                )
            }
            HSpacer(12.dp)
            Text(
                text = text,
                style = ComposeAppTheme.typography.subhead,
                color = ComposeAppTheme.colors.leah,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 4.dp),
            )
        }
        VSpacer(12.dp)
        Row(
            modifier = Modifier.padding(start = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            actions()
        }
    }
}

/** Grey pill button: an icon with (optionally) a label, used for the step actions. */
@Composable
private fun StepPill(
    onClick: () -> Unit,
    icon: Int,
    text: String? = null,
) {
    ButtonSecondary(onClick = onClick, shape = RoundedCornerShape(16.dp)) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = ComposeAppTheme.colors.leah,
            modifier = Modifier.size(16.dp),
        )
        text?.let {
            HSpacer(6.dp)
            Text(it, maxLines = 1)
        }
    }
}

/** The deposit QR (BIP21 payment URI when available) on a white card for scan contrast. */
@Composable
private fun QrDialog(content: String, caption: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            QrCode(content = content, modifier = Modifier.size(220.dp))
            VSpacer(16.dp)
            Text(
                text = caption,
                style = ComposeAppTheme.typography.subhead.copy(fontFamily = FontFamily.Monospace),
                color = ComposeAppTheme.colors.dark,
            )
        }
    }
}

private fun actionRequiredMessage(pauseReason: String?): String {
    val detail = when (pauseReason?.lowercase()) {
        "aml" -> "An AML check blocked the deposit."
        "kyc_required" -> "The provider requires KYC before withdrawal."
        "frozen" -> "The provider froze the order pending review."
        "overdue_with_funds" -> "The order window was missed after the deposit arrived."
        "provider_error" -> "The provider reported an error after the deposit."
        "manual_review" -> "The provider needs to review this order."
        else -> "The swap is paused and needs attention."
    }
    return "$detail Please contact the provider to resolve it."
}

private fun formatAmount(value: BigDecimal): String =
    value.stripTrailingZeros().toPlainString()

/** First/last [edge] chars with an ellipsis, e.g. `TB6nu9QF…YzdhWHWC`. */
private fun middleTruncate(value: String, edge: Int = 8): String =
    if (value.length <= edge * 2 + 1) value else "${value.take(edge)}…${value.takeLast(edge)}"
