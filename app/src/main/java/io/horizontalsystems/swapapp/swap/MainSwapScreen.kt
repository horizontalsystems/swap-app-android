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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.horizontalsystems.swapapp.components.HSScaffold
import io.horizontalsystems.swapapp.compose.ComposeAppTheme
import io.horizontalsystems.swapapp.compose.components.ButtonPrimaryYellow
import io.horizontalsystems.swapapp.compose.components.CoinImage
import io.horizontalsystems.swapapp.compose.components.HSpacer
import io.horizontalsystems.swapapp.compose.components.VSpacer
import java.math.BigDecimal

private enum class SelectTarget { In, Out }

/**
 * The main swap screen: 'You Pay' / 'You Get' dual inputs backed by [SwapQuoteService] quotes.
 * Tapping a token opens the [SwapSelectCoinScreen] built in Step 2. The primary button is a plain
 * 'Next' that, once the amount and a quote are valid, hands off via [onProceed] — no balance
 * checks, no max button, no transaction signing.
 *
 * @param onProceed receives the validated amount, both tokens, and the winning provider.
 */
@Composable
fun MainSwapScreen(
    onClose: () -> Unit,
    onProceed: (amountIn: BigDecimal, tokenIn: SwapToken, tokenOut: SwapToken, selectedProvider: SwapProvider) -> Unit,
) {
    val viewModel = viewModel<MainSwapViewModel>()
    val uiState = viewModel.uiState
    var selecting by remember { mutableStateOf<SelectTarget?>(null) }
    var showProviders by remember { mutableStateOf(false) }

    when {
        selecting == SelectTarget.In -> SwapSelectCoinScreen(
            title = "You pay",
            otherSelectedToken = uiState.tokenOut,
            onClose = { selecting = null },
            onTokenSelected = {
                viewModel.onSelectTokenIn(it)
                selecting = null
            },
        )

        selecting == SelectTarget.Out -> SwapSelectCoinScreen(
            title = "You get",
            otherSelectedToken = uiState.tokenIn,
            onClose = { selecting = null },
            onTokenSelected = {
                viewModel.onSelectTokenOut(it)
                selecting = null
            },
        )

        showProviders -> SwapSelectProviderScreen(
            quotes = uiState.quotes,
            selectedQuote = uiState.quote,
            onSelectQuote = {
                viewModel.onSelectQuote(it)
                showProviders = false
            },
            onClose = { showProviders = false },
        )

        else -> SwapForm(
            uiState = uiState,
            onClose = onClose,
            onEnterAmount = viewModel::onEnterAmount,
            onSwitchPairs = viewModel::onSwitchPairs,
            onClickTokenIn = { selecting = SelectTarget.In },
            onClickTokenOut = { selecting = SelectTarget.Out },
            onClickProviders = { showProviders = true },
            onClickNext = {
                val s = uiState
                if (s.canProceed) {
                    onProceed(s.amountIn!!, s.tokenIn!!, s.tokenOut!!, s.selectedProvider!!)
                }
            },
        )
    }
}

@Composable
private fun SwapForm(
    uiState: MainSwapUiState,
    onClose: () -> Unit,
    onEnterAmount: (BigDecimal?) -> Unit,
    onSwitchPairs: () -> Unit,
    onClickTokenIn: () -> Unit,
    onClickTokenOut: () -> Unit,
    onClickProviders: () -> Unit,
    onClickNext: () -> Unit,
) {
    HSScaffold(title = "Swap", onBack = onClose) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // You Pay — editable amount
            var amountText by remember {
                mutableStateOf(uiState.amountIn?.stripTrailingZeros()?.toPlainString() ?: "")
            }
            // Keep the field in sync when the amount changes outside the field (e.g. switch pairs).
            LaunchedEffect(uiState.amountIn) {
                val external = uiState.amountIn
                if (external != amountText.toBigDecimalOrNull()) {
                    amountText = external?.stripTrailingZeros()?.toPlainString() ?: ""
                }
            }

            SwapCard(
                label = "You Pay",
                token = uiState.tokenIn,
                onClickToken = onClickTokenIn,
            ) {
                BasicTextField(
                    value = amountText,
                    onValueChange = { input ->
                        val filtered = input.toDecimalInput()
                        amountText = filtered
                        onEnterAmount(filtered.toBigDecimalOrNull())
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = ComposeAppTheme.typography.headline1.copy(color = ComposeAppTheme.colors.leah),
                    cursorBrush = SolidColor(ComposeAppTheme.colors.jacob),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    decorationBox = { inner ->
                        if (amountText.isEmpty()) {
                            Text(
                                "0",
                                style = ComposeAppTheme.typography.headline1,
                                color = ComposeAppTheme.colors.grey,
                            )
                        }
                        inner()
                    },
                )
            }

            VSpacer(8.dp)
            SwitchPairsButton(onClick = onSwitchPairs)
            VSpacer(8.dp)

            // You Get — read-only, populated by the fetched quote
            SwapCard(
                label = "You Get",
                token = uiState.tokenOut,
                onClickToken = onClickTokenOut,
            ) {
                Text(
                    text = uiState.amountOut?.stripTrailingZeros()?.toPlainString() ?: "0",
                    style = ComposeAppTheme.typography.headline1,
                    color = if (uiState.amountOut != null) ComposeAppTheme.colors.leah else ComposeAppTheme.colors.grey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            VSpacer(16.dp)
            StatusText(uiState)

            // Provider selector — shows the winning quote and opens the full provider list.
            if (uiState.quotes.isNotEmpty()) {
                VSpacer(16.dp)
                ProviderSelectorRow(
                    providerTitle = uiState.quote?.provider?.title ?: "Select provider",
                    quoteCount = uiState.quotes.size,
                    onClick = onClickProviders,
                )
            }

            VSpacer(16.dp)
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = "Next",
                enabled = uiState.canProceed,
                loadingIndicator = uiState.quoting,
                onClick = onClickNext,
            )
        }
    }
}

@Composable
private fun SwapCard(
    label: String,
    token: SwapToken?,
    onClickToken: () -> Unit,
    amountContent: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ComposeAppTheme.colors.lawrence)
            .padding(16.dp)
    ) {
        Text(
            text = label,
            style = ComposeAppTheme.typography.subhead,
            color = ComposeAppTheme.colors.grey,
        )
        VSpacer(8.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                amountContent()
            }
            HSpacer(12.dp)
            TokenSelectorButton(token = token, onClick = onClickToken)
        }
    }
}

@Composable
private fun TokenSelectorButton(token: SwapToken?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ComposeAppTheme.colors.tyler)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (token != null) {
            CoinImage(
                url = token.logoUrl,
                modifier = Modifier.size(24.dp),
            )
            HSpacer(8.dp)
            Text(
                text = token.ticker,
                style = ComposeAppTheme.typography.headline2,
                color = ComposeAppTheme.colors.leah,
            )
        } else {
            Text(
                text = "Select",
                style = ComposeAppTheme.typography.headline2,
                color = ComposeAppTheme.colors.jacob,
            )
        }
        HSpacer(4.dp)
        Text(text = "▾", color = ComposeAppTheme.colors.grey)
    }
}

@Composable
private fun SwitchPairsButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(ComposeAppTheme.colors.lawrence)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "⇅", style = ComposeAppTheme.typography.headline2, color = ComposeAppTheme.colors.jacob)
    }
}

@Composable
private fun ProviderSelectorRow(
    providerTitle: String,
    quoteCount: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ComposeAppTheme.colors.lawrence)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Provider",
                style = ComposeAppTheme.typography.subhead,
                color = ComposeAppTheme.colors.grey,
            )
            Text(
                text = providerTitle,
                style = ComposeAppTheme.typography.headline2,
                color = ComposeAppTheme.colors.leah,
            )
        }
        Text(
            text = "$quoteCount quotes",
            style = ComposeAppTheme.typography.subhead,
            color = ComposeAppTheme.colors.grey,
        )
        HSpacer(4.dp)
        Text(text = "›", style = ComposeAppTheme.typography.headline2, color = ComposeAppTheme.colors.grey)
    }
}

@Composable
private fun StatusText(uiState: MainSwapUiState) {
    val (text, color) = when {
        uiState.quoting -> "Fetching best quote…" to ComposeAppTheme.colors.grey
        uiState.error != null -> errorMessage(uiState.error) to ComposeAppTheme.colors.lucian
        uiState.tokenIn == null || uiState.tokenOut == null -> "Select tokens to swap" to ComposeAppTheme.colors.grey
        uiState.amountIn == null -> "Enter an amount" to ComposeAppTheme.colors.grey
        uiState.quote != null -> "via ${uiState.quote.provider.title}" to ComposeAppTheme.colors.grey
        else -> "" to ComposeAppTheme.colors.grey
    }
    if (text.isNotEmpty()) {
        Text(text = text, style = ComposeAppTheme.typography.subhead, color = color)
    }
}

/** User-facing message for a quote error — never the raw "HTTP 404" / host-resolution text. */
private fun errorMessage(error: Throwable): String = when (error) {
    is NoSupportedSwapProvider -> "No provider supports this pair"
    is SwapRouteNotFound -> "No route found for this pair"
    is java.io.IOException -> "Network error. Check your connection."
    else -> "Couldn't fetch a quote"
}

/** Keep only digits and a single decimal separator, normalised to '.'. */
private fun String.toDecimalInput(): String {
    val normalized = replace(',', '.')
    val sb = StringBuilder()
    var dotSeen = false
    for (ch in normalized) {
        when {
            ch.isDigit() -> sb.append(ch)
            ch == '.' && !dotSeen -> {
                dotSeen = true
                sb.append(ch)
            }
        }
    }
    return sb.toString()
}
