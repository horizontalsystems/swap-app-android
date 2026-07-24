package io.horizontalsystems.swapapp.swap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.horizontalsystems.swapapp.R
import io.horizontalsystems.swapapp.components.BoxBordered
import io.horizontalsystems.swapapp.components.HSScaffold
import io.horizontalsystems.swapapp.components.cell.CellPrimary
import io.horizontalsystems.swapapp.components.menu.MenuGroup
import io.horizontalsystems.swapapp.components.menu.MenuItemX
import io.horizontalsystems.swapapp.compose.ComposeAppTheme
import io.horizontalsystems.swapapp.compose.components.HSpacer
import io.horizontalsystems.swapapp.compose.components.HsDivider
import io.horizontalsystems.swapapp.compose.components.VSpacer
import io.horizontalsystems.swapapp.settings.DebugSettings
import java.math.BigDecimal

/**
 * Full-screen route picker, matching the reference wallet's
 * `walletkit.modules.multiswap.SwapSelectProviderScreen` layout: a sort dropdown in a tabs-section
 * bar, then one bottom-bordered [CellPrimary] per route — output amount + fiat (with deviation) on
 * the left, safety rating + estimated time on the right. The selection is handed back via
 * [onSelectQuote]; MainSwapViewModel applies it to SwapQuoteService, which makes the chosen quote
 * the primary one.
 */
@Composable
fun SwapSelectRouteScreen(
    quotes: List<SwapProviderQuote>,
    selectedQuote: SwapProviderQuote?,
    priceOut: BigDecimal?,
    onSelectQuote: (SwapProviderQuote) -> Unit,
    onClose: () -> Unit,
) {
    var sortType by rememberSaveable { mutableStateOf(RouteSortType.BestRate) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showScoreInfo by remember { mutableStateOf(false) }
    val showProviderNames = DebugSettings.showRouteProviderNames
    // Recomputed from the quotes passed in on every open, so the list always reflects the current
    // quote set — no retained ViewModel snapshot that could go stale vs the swap screen.
    val items = remember(quotes, sortType, priceOut, showProviderNames) {
        routeViewItems(quotes, sortType, priceOut, showProviderNames)
    }

    HSScaffold(title = "Routes", onBack = onClose) {
        Column(
            Modifier
                .fillMaxSize()
                .background(ComposeAppTheme.colors.lawrence)
        ) {
            // The reference's TabsSectionButtons bar hosting the sort dropdown on the left.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                SortDropdownButton(
                    title = sortType.title,
                    onClick = { showSortDialog = true },
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (items.isNotEmpty()) {
                    HsDivider()
                }

                items.forEach { item ->
                    val isSelected = item.quote.provider.id == selectedQuote?.provider?.id
                    BoxBordered(bottom = true) {
                        RouteCell(
                            item = item,
                            isSelected = isSelected,
                            onClick = { onSelectQuote(item.quote) },
                            onClickRating = { showScoreInfo = true },
                        )
                    }
                }
                VSpacer(32.dp)
            }
        }
    }

    if (showSortDialog) {
        MenuGroup(
            title = "Sort by",
            items = RouteSortType.entries.map { MenuItemX(it.title, it == sortType, it) },
            onDismissRequest = { showSortDialog = false },
            onSelectItem = { sortType = it },
        )
    }

    if (showScoreInfo) {
        RouteScoreInfoSheet(onDismiss = { showScoreInfo = false })
    }
}

@Composable
private fun RouteCell(
    item: RouteViewItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onClickRating: () -> Unit,
) {
    CellPrimary(
        left = {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(
                    if (isSelected) R.drawable.selector_checked_20 else R.drawable.selector_unchecked_20
                ),
                contentDescription = null,
                tint = if (isSelected) ComposeAppTheme.colors.jacob else ComposeAppTheme.colors.andy,
            )
        },
        middle = {
            Row(
                horizontalArrangement = spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = spacedBy(3.dp),
                ) {
                    // Debug-only: reveal which provider backs this route.
                    item.providerName?.let {
                        Text(
                            text = it,
                            style = ComposeAppTheme.typography.caption,
                            color = ComposeAppTheme.colors.grey,
                        )
                    }
                    Text(
                        text = item.amountOut,
                        style = ComposeAppTheme.typography.subhead,
                        color = ComposeAppTheme.colors.leah,
                    )
                    Row(
                        horizontalArrangement = spacedBy(4.dp),
                    ) {
                        item.fiatOut?.let {
                            Text(
                                text = it,
                                style = ComposeAppTheme.typography.subhead,
                                color = ComposeAppTheme.colors.grey,
                            )
                        }
                        item.diffPercent?.let {
                            Text(
                                text = it,
                                style = ComposeAppTheme.typography.subhead,
                                color = ComposeAppTheme.colors.jacob,
                            )
                        }
                    }
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = spacedBy(3.dp),
                ) {
                    item.rating?.let { rating ->
                        RatingBadge(
                            rating = rating,
                            modifier = Modifier.clickable(
                                interactionSource = null,
                                indication = null,
                                onClick = onClickRating,
                            ),
                        )
                    }
                    Text(
                        text = item.estimatedTime,
                        style = ComposeAppTheme.typography.subheadSB,
                        color = ComposeAppTheme.colors.grey,
                    )
                }
            }
        },
        onClick = onClick,
    )
}

/** Secondary/solid dropdown pill matching the reference's `HSDropdownButton`. */
@Composable
private fun SortDropdownButton(
    title: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ComposeAppTheme.colors.blade)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 10.dp),
    ) {
        Text(
            text = title,
            style = ComposeAppTheme.typography.captionSB,
            color = ComposeAppTheme.colors.leah,
        )
        HSpacer(2.dp)
        Icon(
            modifier = Modifier.size(20.dp),
            painter = painterResource(R.drawable.arrow_s_down_24),
            contentDescription = null,
            tint = ComposeAppTheme.colors.leah,
        )
    }
}
