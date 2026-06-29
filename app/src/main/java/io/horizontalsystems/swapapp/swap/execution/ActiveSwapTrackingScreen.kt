package io.horizontalsystems.swapapp.swap.execution

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import io.horizontalsystems.swapapp.components.HSScaffold
import io.horizontalsystems.swapapp.compose.ComposeAppTheme
import io.horizontalsystems.swapapp.compose.components.ButtonPrimaryYellow
import io.horizontalsystems.swapapp.compose.components.VSpacer
import java.math.BigDecimal

/**
 * Shows the generated deposit details (QR + address + exact amount) and a vertical tracker that
 * follows the swap through its stages as [SwapExecutionViewModel] polls the backend.
 */
@Composable
fun ActiveSwapTrackingScreen(
    uiState: ActiveSwapUiState,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onRetry: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current

    HSScaffold(title = "Swap in progress", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                uiState.error != null -> {
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

                uiState.creatingIntent || uiState.depositAddress == null -> {
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
                    onCopyAddress = { uiState.depositAddress?.let { clipboard.setText(AnnotatedString(it)) } },
                    onCopyMemo = { uiState.memo?.let { clipboard.setText(AnnotatedString(it)) } },
                    onDone = onDone,
                )
            }
        }
    }
}

@Composable
private fun SwapDetails(
    uiState: ActiveSwapUiState,
    onCopyAddress: () -> Unit,
    onCopyMemo: () -> Unit,
    onDone: () -> Unit,
) {
    val depositAddress = uiState.depositAddress ?: return

    Text(
        text = "Send exactly",
        style = ComposeAppTheme.typography.subhead,
        color = ComposeAppTheme.colors.grey,
    )
    VSpacer(4.dp)
    Text(
        text = "${formatAmount(uiState.amountIn)} ${uiState.tokenInCode}",
        style = ComposeAppTheme.typography.title3,
        color = ComposeAppTheme.colors.leah,
    )
    VSpacer(4.dp)
    Text(
        text = "via ${uiState.providerTitle} → ${uiState.tokenOutCode}",
        style = ComposeAppTheme.typography.subhead,
        color = ComposeAppTheme.colors.grey,
    )

    VSpacer(16.dp)

    // QR encodes the BIP21 payment URI (address + exact amount) when available, so a wallet scan
    // pre-fills everything; otherwise the bare address.
    val qrContent = uiState.paymentUri ?: depositAddress
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(12.dp)
    ) {
        QrCode(
            content = qrContent,
            modifier = Modifier.size(200.dp),
        )
    }

    VSpacer(16.dp)

    // Deeplink — opens a wallet app pre-filled with the address + exact amount.
    uiState.deeplink?.let { deeplink ->
        val context = LocalContext.current
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
        VSpacer(12.dp)
    }

    // Deposit address + copy
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ComposeAppTheme.colors.lawrence)
            .clickable(onClick = onCopyAddress)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Deposit address",
                style = ComposeAppTheme.typography.subhead,
                color = ComposeAppTheme.colors.grey,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Copy",
                style = ComposeAppTheme.typography.subheadSB,
                color = ComposeAppTheme.colors.jacob,
            )
        }
        VSpacer(4.dp)
        Text(
            text = depositAddress,
            style = ComposeAppTheme.typography.subhead,
            color = ComposeAppTheme.colors.leah,
        )
    }

    // Memo — required for THORChain deposits; the send will be lost without it.
    uiState.memo?.let { memo ->
        VSpacer(12.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ComposeAppTheme.colors.lawrence)
                .clickable(onClick = onCopyMemo)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Memo — required",
                    style = ComposeAppTheme.typography.subhead,
                    color = ComposeAppTheme.colors.lucian,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Copy",
                    style = ComposeAppTheme.typography.subheadSB,
                    color = ComposeAppTheme.colors.jacob,
                )
            }
            VSpacer(4.dp)
            Text(
                text = memo,
                style = ComposeAppTheme.typography.subhead,
                color = ComposeAppTheme.colors.leah,
            )
            VSpacer(4.dp)
            Text(
                text = "You must include this memo in the transaction or the funds will be lost.",
                style = ComposeAppTheme.typography.caption,
                color = ComposeAppTheme.colors.grey,
            )
        }
    }

    VSpacer(24.dp)

    // Vertical status tracker
    Column(modifier = Modifier.fillMaxWidth()) {
        val statuses = SwapStatus.ordered
        statuses.forEachIndexed { index, status ->
            TrackerStep(
                label = status.label,
                state = stepState(status, uiState.status),
                isLast = index == statuses.lastIndex,
            )
        }
    }

    if (uiState.completed) {
        VSpacer(24.dp)
        ButtonPrimaryYellow(
            modifier = Modifier.fillMaxWidth(),
            title = "Done",
            onClick = onDone,
        )
    }
}

private enum class StepState { Done, Active, Pending }

private fun stepState(step: SwapStatus, current: SwapStatus): StepState = when {
    step.order < current.order -> StepState.Done
    step.order > current.order -> StepState.Pending
    current == SwapStatus.Completed -> StepState.Done // last stage reached
    else -> StepState.Active
}

@Composable
private fun TrackerStep(
    label: String,
    state: StepState,
    isLast: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // Indicator column with a connector line down to the next step.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StepIndicator(state)
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(28.dp)
                        .background(
                            if (state == StepState.Done) ComposeAppTheme.colors.remus
                            else ComposeAppTheme.colors.blade
                        )
                )
            }
        }
        VSpacerWidth()
        Text(
            text = label,
            style = ComposeAppTheme.typography.headline2,
            color = when (state) {
                StepState.Done -> ComposeAppTheme.colors.leah
                StepState.Active -> ComposeAppTheme.colors.jacob
                StepState.Pending -> ComposeAppTheme.colors.grey
            },
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

@Composable
private fun StepIndicator(state: StepState) {
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            StepState.Done -> Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(ComposeAppTheme.colors.remus),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", color = ComposeAppTheme.colors.white, style = ComposeAppTheme.typography.captionSB)
            }

            StepState.Active -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = ComposeAppTheme.colors.jacob,
                strokeWidth = 2.dp,
            )

            StepState.Pending -> Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(ComposeAppTheme.colors.blade)
            )
        }
    }
}

@Composable
private fun VSpacerWidth() {
    Box(modifier = Modifier.size(width = 12.dp, height = 1.dp))
}

private fun formatAmount(value: BigDecimal): String =
    value.stripTrailingZeros().toPlainString()
