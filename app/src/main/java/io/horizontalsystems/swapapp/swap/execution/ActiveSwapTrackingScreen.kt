package io.horizontalsystems.swapapp.swap.execution

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import io.horizontalsystems.swapapp.R
import io.horizontalsystems.swapapp.components.HSScaffold
import io.horizontalsystems.swapapp.compose.ComposeAppTheme
import io.horizontalsystems.swapapp.compose.TranslatableString
import io.horizontalsystems.swapapp.compose.components.ButtonPrimaryYellow
import io.horizontalsystems.swapapp.compose.components.CoinImage
import io.horizontalsystems.swapapp.compose.components.HSpacer
import io.horizontalsystems.swapapp.compose.components.MenuItem
import io.horizontalsystems.swapapp.compose.components.VSpacer
import io.horizontalsystems.swapapp.compose.manropeFont
import java.math.BigDecimal

/**
 * Shows the generated deposit details (QR + address + exact amount) and a vertical tracker that
 * follows the swap through its stages as [SwapExecutionViewModel] polls `POST /v2/track`.
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
        title = "",
        onBack = onBack,
        // "Done" lives in the top bar; the primary action pinned to the bottom is opening a wallet.
        menuItems = listOf(
            MenuItem(title = TranslatableString.PlainString("Done"), onClick = onDone),
        ),
        bottomBar = { if (ready) BottomAction(uiState = uiState, onDone = onDone) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            when {
                uiState.error != null -> Centered {
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

                uiState.creatingIntent || uiState.depositAddress == null -> Centered {
                    VSpacer(64.dp)
                    CircularProgressIndicator(color = ComposeAppTheme.colors.jacob)
                    VSpacer(16.dp)
                    Text(
                        text = "Creating your swap…",
                        style = ComposeAppTheme.typography.subhead,
                        color = ComposeAppTheme.colors.grey,
                    )
                }

                else -> SwapDetails(
                    uiState = uiState,
                    onCopyAddress = { uiState.depositAddress?.let(copyToClipboard) },
                    onCopyAttachment = { uiState.attachmentValue?.let(copyToClipboard) },
                )
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = { content() },
    )
}

@Composable
private fun SwapDetails(
    uiState: ActiveSwapUiState,
    onCopyAddress: () -> Unit,
    onCopyAttachment: () -> Unit,
) {
    val depositAddress = uiState.depositAddress ?: return

    VSpacer(8.dp)

    // "Send exactly" + the exact amount, headlined so the send value can't be missed.
    Text(
        text = "Send exactly",
        style = ComposeAppTheme.typography.headline1,
        color = ComposeAppTheme.colors.grey,
    )
    VSpacer(8.dp)
    Row(modifier = Modifier.fillMaxWidth()) {
        // The number auto-shrinks to fit the space left by the token code, so long amounts
        // (e.g. 0.036016087) never push the code off-screen.
        BasicText(
            text = formatAmount(uiState.amountIn),
            modifier = Modifier
                .weight(1f, fill = false)
                .alignByBaseline(),
            style = TextStyle(
                fontFamily = manropeFont,
                fontWeight = FontWeight.Bold,
                color = ComposeAppTheme.colors.leah,
            ),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 22.sp, maxFontSize = 52.sp, stepSize = 2.sp),
        )
        HSpacer(10.dp)
        Text(
            text = uiState.tokenInCode,
            style = TextStyle(fontFamily = manropeFont, fontWeight = FontWeight.SemiBold, fontSize = 30.sp),
            color = ComposeAppTheme.colors.grey,
            modifier = Modifier.alignByBaseline(),
        )
    }

    VSpacer(20.dp)
    RouteRow(uiState)

    VSpacer(24.dp)

    // QR encodes the BIP21 payment URI (address + exact amount) when available, so a wallet scan
    // pre-fills everything; otherwise the bare address.
    val qrContent = uiState.paymentUri ?: depositAddress
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // White card that wraps the QR tightly; the caption sits below it on the page background.
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .padding(18.dp)
        ) {
            QrCode(content = qrContent, modifier = Modifier.size(190.dp))
        }
        VSpacer(12.dp)
        Text(
            text = "Scan to pay from your wallet",
            style = ComposeAppTheme.typography.subhead,
            color = ComposeAppTheme.colors.grey,
        )
    }

    VSpacer(20.dp)

    // Deposit address card: label + truncated address on the left, a "Copy" pill on the right.
    DepositAddressCard(address = depositAddress, onCopy = onCopyAddress)

    // Attachment (destination tag / memo) — required for some chains; the send is lost without it.
    uiState.attachmentValue?.let { attachment ->
        val label = uiState.attachmentLabel ?: "Memo"
        VSpacer(12.dp)
        CopyableField(
            label = "${label.uppercase()} — REQUIRED",
            value = attachment,
            onCopy = onCopyAttachment,
            labelColor = ComposeAppTheme.colors.lucian,
            footnote = "You must include this ${label.lowercase()} in the transaction or the funds will be lost.",
        )
    }

    VSpacer(28.dp)

    // Live status.
    when {
        uiState.failed -> {
            Text(
                text = if (uiState.status == SwapStatus.Refunded) {
                    "Swap failed — your funds were refunded."
                } else {
                    "Swap failed."
                },
                style = ComposeAppTheme.typography.headline2,
                color = ComposeAppTheme.colors.lucian,
            )
        }

        else -> {
            // Vertical status tracker, driven by the live POST /v2/track status.
            Column(modifier = Modifier.fillMaxWidth()) {
                trackerStages.forEachIndexed { index, stage ->
                    TrackerStep(
                        stage = stage,
                        state = stepState(index, uiState.status),
                        isLast = index == trackerStages.lastIndex,
                    )
                }
            }

            if (uiState.status == SwapStatus.ActionRequired) {
                VSpacer(12.dp)
                Text(
                    text = actionRequiredMessage(uiState.pauseReason),
                    style = ComposeAppTheme.typography.subhead,
                    color = ComposeAppTheme.colors.lucian,
                )
            }
        }
    }

    VSpacer(24.dp)
}

/** From-token chip → dashed connector labelled with the provider → to-token chip. */
@Composable
private fun RouteRow(uiState: ActiveSwapUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TokenChip(logo = uiState.tokenInLogo, code = uiState.tokenInCode, network = uiState.tokenInNetwork)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = uiState.providerTitle.uppercase(),
                style = ComposeAppTheme.typography.captionSB.copy(letterSpacing = 0.8.sp),
                color = ComposeAppTheme.colors.jacob,
                textAlign = TextAlign.Center,
            )
            VSpacer(6.dp)
            DashedArrow(modifier = Modifier.fillMaxWidth())
        }
        TokenChip(logo = uiState.tokenOutLogo, code = uiState.tokenOutCode, network = uiState.tokenOutNetwork)
    }
}

@Composable
private fun TokenChip(logo: String?, code: String, network: String) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .border(BorderStroke(1.dp, ComposeAppTheme.colors.blade), CircleShape)
            .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoinImage(url = logo, modifier = Modifier.size(32.dp))
        HSpacer(8.dp)
        Column {
            Text(code, style = ComposeAppTheme.typography.headline2, color = ComposeAppTheme.colors.leah)
            Text(network, style = ComposeAppTheme.typography.caption, color = ComposeAppTheme.colors.grey)
        }
    }
}

@Composable
private fun DashedArrow(modifier: Modifier = Modifier) {
    val color = ComposeAppTheme.colors.blade
    Canvas(modifier = modifier.height(10.dp)) {
        val y = size.height / 2f
        val end = size.width
        val stroke = 2.dp.toPx()
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(end - 6f, y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        )
        val head = 5.dp.toPx()
        drawLine(color, Offset(end - head, y - head), Offset(end, y), stroke, StrokeCap.Round)
        drawLine(color, Offset(end - head, y + head), Offset(end, y), stroke, StrokeCap.Round)
    }
}

/** Deposit address in a compact card with a middle-truncated value and a "Copy" pill. */
@Composable
private fun DepositAddressCard(address: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ComposeAppTheme.colors.blade)
            .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "DEPOSIT ADDRESS",
                style = ComposeAppTheme.typography.captionSB.copy(letterSpacing = 1.sp),
                color = ComposeAppTheme.colors.grey,
            )
            VSpacer(6.dp)
            Text(
                text = middleTruncate(address),
                style = ComposeAppTheme.typography.body.copy(fontFamily = FontFamily.Monospace),
                color = ComposeAppTheme.colors.leah,
            )
        }
        HSpacer(12.dp)
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(ComposeAppTheme.colors.lawrence)
                .clickable(onClick = onCopy)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.copy_20),
                contentDescription = null,
                tint = ComposeAppTheme.colors.leah,
                modifier = Modifier.size(18.dp),
            )
            HSpacer(8.dp)
            Text("Copy", style = ComposeAppTheme.typography.headline2, color = ComposeAppTheme.colors.leah)
        }
    }
}

/** Card with an uppercase section header, a tappable "Copy" action, and the value in an inset box. */
@Composable
private fun CopyableField(
    label: String,
    value: String,
    onCopy: () -> Unit,
    labelColor: Color = ComposeAppTheme.colors.grey,
    footnote: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ComposeAppTheme.colors.lawrence)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = ComposeAppTheme.typography.captionSB.copy(letterSpacing = 1.sp),
                color = labelColor,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Copy",
                style = ComposeAppTheme.typography.subheadSB,
                color = ComposeAppTheme.colors.jacob,
                modifier = Modifier.clickable(onClick = onCopy),
            )
        }
        VSpacer(12.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ComposeAppTheme.colors.blade)
                .clickable(onClick = onCopy)
                .padding(14.dp)
        ) {
            Text(
                text = value,
                style = ComposeAppTheme.typography.subhead,
                color = ComposeAppTheme.colors.leah,
            )
        }
        footnote?.let {
            VSpacer(8.dp)
            Text(
                text = it,
                style = ComposeAppTheme.typography.caption,
                color = ComposeAppTheme.colors.grey,
            )
        }
    }
}

/** Pinned bottom action: open a wallet app while awaiting the deposit, else a plain "Done". */
@Composable
private fun BottomAction(uiState: ActiveSwapUiState, onDone: () -> Unit) {
    val context = LocalContext.current
    val deeplink = uiState.deeplink
    Box(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)
    ) {
        if (deeplink != null && !uiState.status.isTerminal) {
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = "Open in wallet app",
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, deeplink.toUri()))
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, "No app found to open the link", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        } else {
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = "Done",
                onClick = onDone,
            )
        }
    }
}

private data class TrackerStage(val label: String, val activeSubtitle: String)

/** Ordered tracker rows, aligned index-for-index with [SwapStatus.stages]. */
private val trackerStages = listOf(
    TrackerStage("Awaiting deposit", "Send the exact amount above"),
    TrackerStage("Confirming deposit", "Waiting for network confirmations"),
    TrackerStage("Exchanging", "Swapping to your destination token"),
    TrackerStage("Completed", "Your funds are on the way"),
)

private enum class StepState { Done, Active, Pending }

/** Step [index] state given the current [status]'s position in [SwapStatus.stages]. */
private fun stepState(index: Int, status: SwapStatus): StepState {
    val current = status.stageIndex
    return when {
        index < current -> StepState.Done
        index > current -> StepState.Pending
        status == SwapStatus.Completed -> StepState.Done // last stage reached
        else -> StepState.Active
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

@Composable
private fun TrackerStep(
    stage: TrackerStage,
    state: StepState,
    isLast: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Indicator column with a connector line down to the next step.
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StepIndicator(state)
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(
                            if (state == StepState.Done) ComposeAppTheme.colors.jacob
                            else ComposeAppTheme.colors.blade
                        )
                )
            }
        }
        HSpacer(14.dp)
        Column(
            modifier = Modifier.padding(bottom = if (isLast) 0.dp else 22.dp)
        ) {
            Text(
                text = stage.label,
                style = ComposeAppTheme.typography.headline2,
                color = when (state) {
                    StepState.Done -> ComposeAppTheme.colors.leah
                    StepState.Active -> ComposeAppTheme.colors.leah
                    StepState.Pending -> ComposeAppTheme.colors.grey
                },
            )
            if (state == StepState.Active) {
                VSpacer(2.dp)
                Text(
                    text = stage.activeSubtitle,
                    style = ComposeAppTheme.typography.caption,
                    color = ComposeAppTheme.colors.grey,
                )
            }
        }
    }
}

@Composable
private fun StepIndicator(state: StepState) {
    Box(
        modifier = Modifier.size(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            StepState.Done -> Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(ComposeAppTheme.colors.jacob),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", color = ComposeAppTheme.colors.white, style = ComposeAppTheme.typography.captionSB)
            }

            // Solid dot inside a filled circle with a soft halo — the "you are here" marker.
            StepState.Active -> {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ComposeAppTheme.colors.jacob.copy(alpha = 0.2f))
                )
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(ComposeAppTheme.colors.jacob),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(ComposeAppTheme.colors.white)
                    )
                }
            }

            StepState.Pending -> Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(BorderStroke(1.5.dp, ComposeAppTheme.colors.blade), CircleShape)
            )
        }
    }
}

private fun formatAmount(value: BigDecimal): String =
    value.stripTrailingZeros().toPlainString()

/** First/last 8 chars with an ellipsis, e.g. `TB6nu9QF…YzdhWHWC`. */
private fun middleTruncate(value: String, edge: Int = 8): String =
    if (value.length <= edge * 2 + 1) value else "${value.take(edge)}…${value.takeLast(edge)}"
