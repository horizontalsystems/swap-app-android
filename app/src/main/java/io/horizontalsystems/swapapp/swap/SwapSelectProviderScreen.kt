package io.horizontalsystems.swapapp.swap

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.horizontalsystems.swapapp.components.HSScaffold
import io.horizontalsystems.swapapp.components.cell.CellMiddleInfo
import io.horizontalsystems.swapapp.components.cell.CellPrimary
import io.horizontalsystems.swapapp.components.cell.CellRightInfo
import io.horizontalsystems.swapapp.components.cell.hs
import io.horizontalsystems.swapapp.compose.ComposeAppTheme
import io.horizontalsystems.swapapp.compose.components.HsDivider

/**
 * Full-screen provider picker. Lists the competing quotes (provider name, output amount, estimated
 * time, fee), lets the user sort by best price / best time and pick a preferred provider. The
 * selection is handed back via [onSelectQuote]; MainSwapViewModel applies it to SwapQuoteService,
 * which makes the chosen quote the primary one.
 */
@Composable
fun SwapSelectProviderScreen(
    quotes: List<SwapProviderQuote>,
    selectedQuote: SwapProviderQuote?,
    onSelectQuote: (SwapProviderQuote) -> Unit,
    onClose: () -> Unit,
) {
    var sortType by rememberSaveable { mutableStateOf(ProviderSortType.BestPrice) }
    // Recomputed from the quotes passed in on every open, so the list always reflects the current
    // quote set — no retained ViewModel snapshot that could go stale vs the swap screen.
    val items = remember(quotes, sortType) { providerQuoteViewItems(quotes, sortType) }

    HSScaffold(title = "Providers", onBack = onClose) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ComposeAppTheme.colors.lawrence)
        ) {
            SortSelector(
                selected = sortType,
                onSelect = { sortType = it },
            )
            HsDivider()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                items.forEach { item ->
                    val isSelected = item.quote.provider.id == selectedQuote?.provider?.id
                    ProviderRow(
                        item = item,
                        isSelected = isSelected,
                        onClick = { onSelectQuote(item.quote) },
                    )
                    HsDivider()
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(
    item: ProviderQuoteViewItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    CellPrimary(
        onClick = onClick,
        left = {
            SelectionDot(isSelected)
        },
        middle = {
            CellMiddleInfo(
                title = item.providerTitle.hs,
                subtitle = item.fee?.let { "Fee $it".hs },
                badge = badgeFor(item),
            )
        },
        right = {
            CellRightInfo(
                title = item.amountOut.hs,
                subtitle = item.estimatedTime.hs(
                    color = if (item.isBestTime) ComposeAppTheme.colors.remus else null
                ),
            )
        },
    )
}

@Composable
private fun badgeFor(item: ProviderQuoteViewItem) = when {
    item.isBestPrice -> "BEST".hs(color = ComposeAppTheme.colors.remus)
    else -> null
}

@Composable
private fun SelectionDot(isSelected: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) ComposeAppTheme.colors.jacob else ComposeAppTheme.colors.blade
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(ComposeAppTheme.colors.dark)
            )
        }
    }
}

@Composable
private fun SortSelector(
    selected: ProviderSortType,
    onSelect: (ProviderSortType) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProviderSortType.entries.forEach { type ->
            val active = type == selected
            Text(
                text = type.title,
                style = ComposeAppTheme.typography.subheadSB,
                color = if (active) ComposeAppTheme.colors.dark else ComposeAppTheme.colors.leah,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (active) ComposeAppTheme.colors.jacob else ComposeAppTheme.colors.tyler
                    )
                    .clickable { onSelect(type) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
