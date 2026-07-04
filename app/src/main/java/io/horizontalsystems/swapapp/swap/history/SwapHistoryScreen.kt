package io.horizontalsystems.swapapp.swap.history

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.horizontalsystems.swapapp.R
import io.horizontalsystems.swapapp.components.HSScaffold
import io.horizontalsystems.swapapp.components.cell.CellMiddleInfo
import io.horizontalsystems.swapapp.components.cell.CellPrimary
import io.horizontalsystems.swapapp.components.cell.hs
import io.horizontalsystems.swapapp.compose.ComposeAppTheme
import io.horizontalsystems.swapapp.compose.components.HeaderStick
import io.horizontalsystems.swapapp.compose.components.HsDivider
import io.horizontalsystems.swapapp.compose.components.HsImageCircle
import io.horizontalsystems.swapapp.compose.components.VSpacer
import io.horizontalsystems.swapapp.compose.components.body_grey
import io.horizontalsystems.swapapp.compose.components.captionSB_grey
import io.horizontalsystems.swapapp.compose.components.subheadSB_leah
import io.horizontalsystems.swapapp.swap.execution.SwapStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Lists the user's past swaps (from [SwapHistoryStore]) newest-first, grouped by day — a 1:1 port
 * of the wallet's `multiswap.history.SwapHistoryPage`. Each row shows the paid → received amounts
 * with a status icon between them; tapping opens the [SwapInfoScreen].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwapHistoryScreen(
    store: SwapHistoryStore,
    onBack: () -> Unit,
    onOpen: (SwapRecord) -> Unit,
) {
    val records by store.records.collectAsState()

    HSScaffold(title = "Swap History", onBack = onBack) {
        if (records.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                body_grey(
                    text = "You don't have any pending or past swaps yet.",
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            // Records are already newest-first; bucket consecutively into day groups keeping that order.
            val groups = LinkedHashMap<String, MutableList<SwapRecord>>()
            records.forEach { groups.getOrPut(formatDate(Date(it.createdAt))) { mutableListOf() }.add(it) }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ComposeAppTheme.colors.lawrence),
            ) {
                groups.forEach { (dateHeader, swaps) ->
                    stickyHeader {
                        HeaderStick(
                            borderBottom = true,
                            text = dateHeader.uppercase(),
                            color = ComposeAppTheme.colors.lawrence,
                        )
                    }
                    items(swaps, key = { it.uuid }) { record ->
                        SwapHistoryCell(
                            record = record,
                            onClick = { onOpen(record) },
                        )
                    }
                }
                item { VSpacer(32.dp) }
            }
        }
    }
}

@Composable
private fun SwapHistoryCell(record: SwapRecord, onClick: () -> Unit) {
    val status = record.swapStatus
    Column {
        CellPrimary(
            left = {
                SwapCoinIcon(
                    imageUrl = record.tokenIn.logoUrl,
                    showSpinner = status.isDepositing,
                )
            },
            middle = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        CellMiddleInfo(
                            subtitle = "${record.amountIn} ${record.tokenIn.ticker}".hs(ComposeAppTheme.colors.leah),
                            description = record.fiatIn?.hs,
                        )
                    }
                    val (statusIcon, statusTint) = statusIconAndTint(status)
                    Icon(
                        modifier = Modifier.size(20.dp),
                        painter = painterResource(statusIcon),
                        tint = statusTint,
                        contentDescription = null,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End,
                    ) {
                        subheadSB_leah(
                            text = record.amountOut?.let { "$it ${record.tokenOut.ticker}" } ?: "---",
                            textAlign = TextAlign.End,
                        )

                        record.fiatOut?.let {
                            captionSB_grey(
                                text = it,
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            },
            right = {
                SwapCoinIcon(
                    imageUrl = record.tokenOut.logoUrl,
                    showSpinner = status == SwapStatus.Swapping,
                )
            },
            onClick = onClick,
        )
        HsDivider()
    }
}

@Composable
private fun SwapCoinIcon(imageUrl: String?, showSpinner: Boolean) {
    val leah = ComposeAppTheme.colors.leah
    val andy = ComposeAppTheme.colors.andy

    val rotate by if (showSpinner) {
        rememberInfiniteTransition(label = "spinner").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = LinearEasing)
            ),
            label = "rotate",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Box(
        modifier = Modifier
            .size(32.dp)
            .drawBehind {
                if (showSpinner) {
                    inset(-2.dp.toPx()) {
                        drawArc(
                            color = andy,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                        )
                        rotate(degrees = rotate) {
                            drawArc(
                                color = leah,
                                startAngle = 0f,
                                sweepAngle = -120f,
                                useCenter = false,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                            )
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        HsImageCircle(
            modifier = Modifier.size(32.dp),
            url = imageUrl,
            placeholder = R.drawable.coin_placeholder,
        )
    }
}

/** The deposit phase — the wallet's `Depositing`: waiting for or confirming the user's send. */
private val SwapStatus.isDepositing: Boolean
    get() = this == SwapStatus.NotStarted || this == SwapStatus.Pending || this == SwapStatus.Unknown

@Composable
private fun statusIconAndTint(status: SwapStatus): Pair<Int, Color> = when (status) {
    SwapStatus.NotStarted,
    SwapStatus.Pending,
    SwapStatus.Swapping,
    SwapStatus.Unknown -> Pair(R.drawable.arrow_m_right_24, ComposeAppTheme.colors.grey)

    SwapStatus.Completed -> Pair(R.drawable.ic_done_filled_20, ComposeAppTheme.colors.remus)
    SwapStatus.Refunded -> Pair(R.drawable.ic_arrow_return_20, ComposeAppTheme.colors.grey)
    SwapStatus.Failed,
    SwapStatus.ActionRequired -> Pair(R.drawable.ic_warning_filled_20, ComposeAppTheme.colors.lucian)
}

/** "Today" / "Yesterday" / "MMMM d" (same year) / "MMMM d, yyyy" for the day-group header. */
private fun formatDate(date: Date): String {
    val calendar = Calendar.getInstance()
    calendar.time = date

    val today = Calendar.getInstance()
    if (calendar[Calendar.YEAR] == today[Calendar.YEAR] &&
        calendar[Calendar.DAY_OF_YEAR] == today[Calendar.DAY_OF_YEAR]
    ) {
        return "Today"
    }

    val yesterday = Calendar.getInstance()
    yesterday.add(Calendar.DAY_OF_MONTH, -1)
    if (calendar[Calendar.YEAR] == yesterday[Calendar.YEAR] &&
        calendar[Calendar.DAY_OF_YEAR] == yesterday[Calendar.DAY_OF_YEAR]
    ) {
        return "Yesterday"
    }

    val pattern = if (calendar[Calendar.YEAR] == today[Calendar.YEAR]) "MMMM d" else "MMMM d, yyyy"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(date)
}
