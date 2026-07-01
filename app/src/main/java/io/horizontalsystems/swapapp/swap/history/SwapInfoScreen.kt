package io.horizontalsystems.swapapp.swap.history

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.horizontalsystems.swapapp.R
import io.horizontalsystems.swapapp.components.HSScaffold
import io.horizontalsystems.swapapp.compose.ComposeAppTheme
import io.horizontalsystems.swapapp.compose.components.CoinImage
import io.horizontalsystems.swapapp.compose.components.HSpacer
import io.horizontalsystems.swapapp.compose.components.VSpacer
import io.horizontalsystems.swapapp.swap.execution.SwapLeg
import io.horizontalsystems.swapapp.swap.execution.SwapStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The three visible stages of a swap, shown in the "Transaction Status" tracker. */
private val TRANSACTION_STEPS = listOf("Depositing", "Swap", "Send")

/**
 * Detail view for a single recorded swap: the paid → received pair, when it happened, and a live
 * transaction-status tracker (Depositing → Swap → Send) driven by [SwapInfoViewModel].
 */
@Composable
fun SwapInfoScreen(
    uiState: SwapInfoUiState,
    onBack: () -> Unit,
) {
    val record = uiState.record

    HSScaffold(title = "Swap Info", onBack = onBack) {
        if (record == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "This swap is no longer available.",
                    style = ComposeAppTheme.typography.subhead,
                    color = ComposeAppTheme.colors.grey,
                    textAlign = TextAlign.Center,
                )
            }
            return@HSScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SwapPairCard(record)
            VSpacer(16.dp)
            DateCard(record.createdAt)
            VSpacer(24.dp)

            Text(
                text = "Transaction Status",
                style = ComposeAppTheme.typography.subhead,
                color = ComposeAppTheme.colors.grey,
                modifier = Modifier.padding(start = 16.dp),
            )
            VSpacer(8.dp)
            TransactionStatusCard(status = uiState.status, legs = uiState.legs)
            VSpacer(24.dp)
        }
    }
}

@Composable
private fun SwapPairCard(record: SwapRecord) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ComposeAppTheme.colors.lawrence),
    ) {
        TokenRow(
            token = record.tokenIn,
            amount = record.amountIn,
            fiat = record.fiatIn,
        )
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.material.Divider(
                thickness = 0.5.dp,
                color = ComposeAppTheme.colors.blade,
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(ComposeAppTheme.colors.lawrence),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_down_24),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.grey,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        TokenRow(
            token = record.tokenOut,
            amount = record.amountOut ?: "—",
            fiat = record.fiatOut,
        )
    }
}

@Composable
private fun TokenRow(token: RecordToken, amount: String, fiat: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoinImage(url = token.logoUrl, modifier = Modifier.size(32.dp))
        HSpacer(12.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = token.ticker,
                style = ComposeAppTheme.typography.headline2,
                color = ComposeAppTheme.colors.leah,
            )
            Text(
                text = token.network,
                style = ComposeAppTheme.typography.subhead,
                color = ComposeAppTheme.colors.grey,
            )
        }
        HSpacer(12.dp)
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = amount,
                style = ComposeAppTheme.typography.headline2,
                color = ComposeAppTheme.colors.leah,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = fiat ?: "—",
                style = ComposeAppTheme.typography.subhead,
                color = ComposeAppTheme.colors.grey,
            )
        }
    }
}

@Composable
private fun DateCard(createdAt: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ComposeAppTheme.colors.lawrence)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Date",
            style = ComposeAppTheme.typography.subhead,
            color = ComposeAppTheme.colors.grey,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = SimpleDateFormat("MMM dd, yyyy, HH:mm", Locale.getDefault()).format(Date(createdAt)),
            style = ComposeAppTheme.typography.headline2,
            color = ComposeAppTheme.colors.leah,
        )
    }
}

private enum class StepState { Done, Active, Pending }

@Composable
private fun TransactionStatusCard(status: SwapStatus, legs: List<SwapLeg>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ComposeAppTheme.colors.lawrence)
            .padding(16.dp),
    ) {
        if (status == SwapStatus.Refunded || status == SwapStatus.Failed) {
            Text(
                text = if (status == SwapStatus.Refunded) "Swap failed — your funds were refunded."
                else "Swap failed.",
                style = ComposeAppTheme.typography.subhead,
                color = ComposeAppTheme.colors.lucian,
            )
            VSpacer(12.dp)
        }

        TRANSACTION_STEPS.forEachIndexed { index, label ->
            StatusStep(
                label = label,
                state = stepState(index, status),
                explorerUrl = legs.getOrNull(index)?.let { explorerUrl(it.chainId, it.hash) },
                isLast = index == TRANSACTION_STEPS.lastIndex,
            )
        }
    }
}

@Composable
private fun StatusStep(
    label: String,
    state: StepState,
    explorerUrl: String?,
    isLast: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
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
        HSpacer(12.dp)
        Text(
            text = label,
            style = ComposeAppTheme.typography.headline2,
            color = when (state) {
                StepState.Done -> ComposeAppTheme.colors.leah
                StepState.Active -> ComposeAppTheme.colors.jacob
                StepState.Pending -> ComposeAppTheme.colors.grey
            },
            modifier = Modifier
                .weight(1f)
                .padding(top = 1.dp),
        )
        if (explorerUrl != null) {
            val context = LocalContext.current
            Row(
                modifier = Modifier.clickable {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, explorerUrl.toUri()))
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, "No app found to open the link", Toast.LENGTH_SHORT).show()
                    }
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "View",
                    style = ComposeAppTheme.typography.subhead,
                    color = ComposeAppTheme.colors.grey,
                )
                Icon(
                    painter = painterResource(R.drawable.chevron_right_20),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.grey,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
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

/**
 * Step [index] (0 Depositing, 1 Swap, 2 Send) state for the current [status]. A terminal failure
 * leaves the deposit done and the rest pending (the banner above explains it).
 */
private fun stepState(index: Int, status: SwapStatus): StepState {
    // (fully-done count, active index or -1)
    val (done, active) = when (status) {
        SwapStatus.Completed -> 3 to -1
        SwapStatus.Swapping, SwapStatus.ActionRequired -> 1 to 1
        SwapStatus.Pending, SwapStatus.NotStarted, SwapStatus.Unknown -> 0 to 0
        SwapStatus.Refunded, SwapStatus.Failed -> 1 to -1
    }
    return when {
        index < done -> StepState.Done
        index == active -> StepState.Active
        else -> StepState.Pending
    }
}

/**
 * Best-effort block-explorer transaction URL from a leg's chain id + hash. Covers the common chains;
 * returns null (so no "View" link is shown) for anything unrecognised or without a hash.
 */
private fun explorerUrl(chainId: String?, hash: String?): String? {
    if (hash.isNullOrBlank()) return null
    val base = when (chainId?.lowercase()) {
        "bitcoin", "btc" -> "https://blockchair.com/bitcoin/transaction/"
        "bitcoincash", "bch" -> "https://blockchair.com/bitcoin-cash/transaction/"
        "litecoin", "ltc" -> "https://blockchair.com/litecoin/transaction/"
        "dogecoin", "doge" -> "https://blockchair.com/dogecoin/transaction/"
        "dash" -> "https://blockchair.com/dash/transaction/"
        "ethereum", "eth", "1" -> "https://etherscan.io/tx/"
        "bsc", "bnb", "56" -> "https://bscscan.com/tx/"
        "polygon", "matic", "137" -> "https://polygonscan.com/tx/"
        "avalanche", "avax", "43114" -> "https://snowtrace.io/tx/"
        "arbitrum", "42161" -> "https://arbiscan.io/tx/"
        "optimism", "10" -> "https://optimistic.etherscan.io/tx/"
        "base", "8453" -> "https://basescan.org/tx/"
        "tron", "trx" -> "https://tronscan.org/#/transaction/"
        else -> return null
    }
    return base + hash
}
