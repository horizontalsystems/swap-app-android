package io.horizontalsystems.swapapp.swap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.horizontalsystems.swapapp.components.HSScaffold
import io.horizontalsystems.swapapp.components.cell.CellMiddleInfo
import io.horizontalsystems.swapapp.components.cell.CellPrimary
import io.horizontalsystems.swapapp.components.cell.CellRightInfo
import io.horizontalsystems.swapapp.components.cell.hs
import io.horizontalsystems.swapapp.compose.ComposeAppTheme
import io.horizontalsystems.swapapp.compose.components.CoinImage
import io.horizontalsystems.swapapp.compose.components.HsDivider
import io.horizontalsystems.swapapp.compose.components.VSpacer

/**
 * Token selector screen. With an empty query it shows a context-aware "Popular tokens" horizontal
 * row and a "Top tokens" list (every remaining token); typing shows a flat search result list.
 * Both are built from the swap API token list ([SwapTokenRepository]). Selecting a row returns the
 * chosen [SwapToken].
 *
 * @param otherSelectedToken token already chosen on the other side of the swap (excluded, and used
 *                           as the context that orders the popular list).
 * @param onClose            invoked when the back/close action is tapped.
 * @param onTokenSelected    invoked with the picked [SwapToken].
 */
@Composable
fun SwapSelectCoinScreen(
    title: String = "Select token",
    otherSelectedToken: SwapToken? = null,
    onClose: () -> Unit,
    onTokenSelected: (SwapToken) -> Unit,
) {
    val viewModel = viewModel<SwapSelectCoinViewModel>(
        factory = SwapSelectCoinViewModel.Factory(otherSelectedToken)
    )
    val uiState = viewModel.uiState

    HSScaffold(
        title = title,
        onBack = onClose,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchBar(
                query = uiState.query,
                onQueryChange = viewModel::setQuery,
            )

            val empty = if (uiState.searching) {
                uiState.searchResults.isEmpty()
            } else {
                uiState.popular.isEmpty() && uiState.topTokens.isEmpty()
            }

            when {
                uiState.loading && empty -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = ComposeAppTheme.colors.grey,
                            strokeWidth = 2.dp,
                        )
                    }
                }

                empty -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (uiState.error) "Couldn't load tokens" else "No tokens found",
                            style = ComposeAppTheme.typography.subhead,
                            color = ComposeAppTheme.colors.grey,
                        )
                    }
                }

                uiState.searching -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.searchResults, key = { it.token.identifier }) { item ->
                            TokenCell(item = item, onClick = { onTokenSelected(item.token) })
                            HsDivider()
                        }
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (uiState.popular.isNotEmpty()) {
                            item { SectionHeader("Popular tokens") }
                            item {
                                PopularTokensRow(
                                    items = uiState.popular,
                                    onClick = { onTokenSelected(it.token) },
                                )
                            }
                        }
                        if (uiState.topTokens.isNotEmpty()) {
                            item { SectionHeader("Top tokens") }
                            items(uiState.topTokens, key = { it.token.identifier }) { item ->
                                TokenCell(item = item, onClick = { onTokenSelected(item.token) })
                                HsDivider()
                            }
                        }
                        item { VSpacer(32.dp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        style = ComposeAppTheme.typography.subheadSB,
        color = ComposeAppTheme.colors.grey,
    )
}

@Composable
private fun PopularTokensRow(
    items: List<TokenViewItem>,
    onClick: (TokenViewItem) -> Unit,
) {
    HsDivider()
    LazyRow(
        modifier = Modifier.height(62.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(items, key = { it.token.identifier }) { item ->
            PopularTokenChip(item = item, onClick = { onClick(item) })
        }
    }
    HsDivider()
}

@Composable
private fun PopularTokenChip(item: TokenViewItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(ComposeAppTheme.colors.blade)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoinImage(
            url = item.logoUrl,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = item.code,
            modifier = Modifier.padding(start = 8.dp),
            style = ComposeAppTheme.typography.subhead,
            color = ComposeAppTheme.colors.leah,
        )
    }
}

@Composable
private fun TokenCell(item: TokenViewItem, onClick: () -> Unit) {
    CellPrimary(
        onClick = onClick,
        left = {
            CoinImage(
                url = item.logoUrl,
                modifier = Modifier.size(32.dp),
            )
        },
        middle = {
            CellMiddleInfo(
                title = item.code.hs,
                subtitle = item.name.hs,
            )
        },
        right = {
            CellRightInfo(
                subtitle = item.blockchain.hs,
            )
        },
    )
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeAppTheme.colors.lawrence)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = ComposeAppTheme.typography.body.copy(color = ComposeAppTheme.colors.leah),
            cursorBrush = SolidColor(ComposeAppTheme.colors.jacob),
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    Text(
                        text = "Search by name or code",
                        style = ComposeAppTheme.typography.body,
                        color = ComposeAppTheme.colors.grey,
                    )
                }
                innerTextField()
            },
        )
    }
}
