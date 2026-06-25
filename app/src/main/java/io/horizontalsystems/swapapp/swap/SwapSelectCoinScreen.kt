package io.horizontalsystems.swapapp.swap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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

/**
 * Token selector screen. Renders a search bar and the swap API token list ([SwapTokenRepository])
 * built from the Step 1 cell components. Selecting a row returns the chosen [SwapToken].
 *
 * @param otherSelectedToken token already chosen on the other side of the swap (excluded).
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

            when {
                uiState.loading && uiState.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = ComposeAppTheme.colors.grey,
                            strokeWidth = 2.dp,
                        )
                    }
                }

                uiState.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (uiState.error) "Couldn't load tokens" else "No tokens found",
                            style = ComposeAppTheme.typography.subhead,
                            color = ComposeAppTheme.colors.grey,
                        )
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.items, key = { it.token.identifier }) { item ->
                            TokenCell(item = item, onClick = { onTokenSelected(item.token) })
                            HsDivider()
                        }
                    }
                }
            }
        }
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
