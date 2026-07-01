package io.horizontalsystems.swapapp.swap.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.horizontalsystems.swapapp.components.HSScaffold
import io.horizontalsystems.swapapp.compose.ComposeAppTheme
import io.horizontalsystems.swapapp.compose.components.CoinImage
import io.horizontalsystems.swapapp.compose.components.HSpacer
import io.horizontalsystems.swapapp.compose.components.HsDivider
import io.horizontalsystems.swapapp.compose.components.VSpacer
import io.horizontalsystems.swapapp.swap.execution.SwapStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Lists the user's past swaps (from [SwapHistoryStore]) newest-first, grouped by day. Each row shows
 * the paid → received amounts with a status glyph between them; tapping opens the [SwapInfoScreen].
 */
@Composable
fun SwapHistoryScreen(
    store: SwapHistoryStore,
    onBack: () -> Unit,
    onOpen: (SwapRecord) -> Unit,
) {
    val records by store.records.collectAsState()

    HSScaffold(title = "Swap History", onBack = onBack) {
        if (records.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No swaps yet.\nYour completed swaps will appear here.",
                    style = ComposeAppTheme.typography.subhead,
                    color = ComposeAppTheme.colors.grey,
                    textAlign = TextAlign.Center,
                )
            }
            return@HSScaffold
        }

        // Records are already newest-first; bucket consecutively into day groups keeping that order.
        val groups = LinkedHashMap<String, MutableList<SwapRecord>>()
        records.forEach { groups.getOrPut(dayLabel(it.createdAt)) { mutableListOf() }.add(it) }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            groups.forEach { (label, items) ->
                item(key = "header-$label") {
                    Text(
                        text = label,
                        style = ComposeAppTheme.typography.subhead,
                        color = ComposeAppTheme.colors.grey,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    // Top border for the group's first row.
                    HsDivider()
                }
                items(items, key = { it.uuid }) { record ->
                    HistoryRow(record = record, onClick = { onOpen(record) })
                    HsDivider()
                }
            }
            item { VSpacer(24.dp) }
        }
    }
}

@Composable
private fun HistoryRow(record: SwapRecord, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoinImage(url = record.tokenIn.logoUrl, modifier = Modifier.size(28.dp))
        HSpacer(10.dp)
        AmountColumn(
            amount = "${record.amountIn} ${record.tokenIn.ticker}",
            fiat = record.fiatIn,
            alignEnd = false,
            modifier = Modifier.weight(1f),
        )
        HSpacer(8.dp)
        StatusGlyph(record.swapStatus)
        HSpacer(8.dp)
        AmountColumn(
            amount = "${record.amountOut ?: "…"} ${record.tokenOut.ticker}",
            fiat = record.fiatOut,
            alignEnd = true,
            modifier = Modifier.weight(1f),
        )
        HSpacer(10.dp)
        CoinImage(url = record.tokenOut.logoUrl, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun AmountColumn(
    amount: String,
    fiat: String?,
    alignEnd: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = amount,
            style = ComposeAppTheme.typography.subheadSB,
            color = ComposeAppTheme.colors.leah,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = fiat ?: "—",
            style = ComposeAppTheme.typography.caption,
            color = ComposeAppTheme.colors.grey,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The between-amounts status marker: green ✓ (done), red ! (failed), ↩ (refunded), → (in progress). */
@Composable
private fun StatusGlyph(status: SwapStatus) {
    when (status) {
        SwapStatus.Completed -> FilledGlyph("✓", ComposeAppTheme.colors.remus)
        SwapStatus.Failed -> FilledGlyph("!", ComposeAppTheme.colors.lucian)
        SwapStatus.Refunded -> Text(
            text = "↩",
            style = ComposeAppTheme.typography.headline2,
            color = ComposeAppTheme.colors.grey,
        )
        else -> Text(
            text = "→",
            style = ComposeAppTheme.typography.headline2,
            color = ComposeAppTheme.colors.grey,
        )
    }
}

@Composable
private fun FilledGlyph(symbol: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = ComposeAppTheme.colors.white, style = ComposeAppTheme.typography.captionSB)
    }
}

/** "Today" / "Yesterday" / "MMM dd, yyyy" for the day-group header. */
private fun dayLabel(time: Long): String {
    val startOfToday = startOfDay(System.currentTimeMillis())
    val startOfRecord = startOfDay(time)
    return when (startOfToday - startOfRecord) {
        0L -> "Today"
        DAY_MS -> "Yesterday"
        else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(time))
    }
}

private fun startOfDay(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = time
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private const val DAY_MS = 24L * 60 * 60 * 1000
