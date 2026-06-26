package io.horizontalsystems.swapapp.swap

import androidx.compose.foundation.Image
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
import androidx.compose.material.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.horizontalsystems.swapapp.R
import io.horizontalsystems.swapapp.components.HSScaffold
import io.horizontalsystems.swapapp.compose.ComposeAppTheme
import io.horizontalsystems.swapapp.compose.components.Badge
import io.horizontalsystems.swapapp.compose.components.ButtonPrimaryYellow
import io.horizontalsystems.swapapp.compose.components.CoinImage
import io.horizontalsystems.swapapp.compose.components.HSpacer
import io.horizontalsystems.swapapp.compose.components.HsDivider
import io.horizontalsystems.swapapp.compose.components.VSpacer
import java.math.BigDecimal
import java.text.DecimalFormat

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
    HSScaffold(title = "Swap") {
        Column(modifier = Modifier.fillMaxSize()) {
            // 'You pay' amount — editable; kept in sync when it changes outside the field.
            var amountText by remember {
                mutableStateOf(uiState.amountIn?.stripTrailingZeros()?.toPlainString() ?: "")
            }
            LaunchedEffect(uiState.amountIn) {
                val external = uiState.amountIn
                if (external != amountText.toBigDecimalOrNull()) {
                    amountText = external?.stripTrailingZeros()?.toPlainString() ?: ""
                }
            }

            // Two token rows separated by a divider, with the switch button centered on it.
            Box(contentAlignment = Alignment.Center) {
                Column {
                    SwapTokenRow(token = uiState.tokenIn, fiat = uiState.fiatIn, onClickToken = onClickTokenIn) {
                        BasicTextField(
                            value = amountText,
                            onValueChange = { input ->
                                val filtered = input.toDecimalInput()
                                amountText = filtered
                                onEnterAmount(filtered.toBigDecimalOrNull())
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = ComposeAppTheme.typography.headline1.copy(
                                color = ComposeAppTheme.colors.leah,
                                textAlign = TextAlign.End,
                            ),
                            cursorBrush = SolidColor(ComposeAppTheme.colors.jacob),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            decorationBox = { inner ->
                                if (amountText.isEmpty()) {
                                    Text(
                                        text = "0",
                                        modifier = Modifier.fillMaxWidth(),
                                        style = ComposeAppTheme.typography.headline1,
                                        color = ComposeAppTheme.colors.grey,
                                        textAlign = TextAlign.End,
                                    )
                                }
                                inner()
                            },
                        )
                    }

                    HsDivider()

                    // 'You get' amount — read-only, populated by the fetched quote.
                    SwapTokenRow(token = uiState.tokenOut, fiat = uiState.fiatOut, onClickToken = onClickTokenOut) {
                        Text(
                            text = uiState.amountOut?.stripTrailingZeros()?.toPlainString() ?: "0",
                            style = ComposeAppTheme.typography.headline1,
                            color = if (uiState.amountOut != null) ComposeAppTheme.colors.leah else ComposeAppTheme.colors.grey,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                SwitchPairsButton(onClick = onSwitchPairs)
            }

            Column(modifier = Modifier.padding(16.dp)) {
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
}

/**
 * A flat token row: tappable coin image + ticker (with dropdown caret) + network badge on the left,
 * and the amount ([amountContent]) right-aligned. Mirrors the design's `CellPrimary` layout.
 */
@Composable
private fun SwapTokenRow(
    token: SwapToken?,
    fiat: BigDecimal?,
    onClickToken: () -> Unit,
    amountContent: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.clickable(onClick = onClickToken),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (token != null) {
                CoinImage(url = token.logoUrl, modifier = Modifier.size(40.dp))
            } else {
                Image(
                    painter = painterResource(R.drawable.coin_placeholder),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = token?.ticker ?: "Select",
                        style = ComposeAppTheme.typography.headline1,
                        color = if (token != null) ComposeAppTheme.colors.leah else ComposeAppTheme.colors.jacob,
                    )
                    Text(text = "▾", color = ComposeAppTheme.colors.grey)
                }
                if (token != null) {
                    Badge(text = networkName(token))
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            amountContent()
            if (fiat != null) {
                Text(
                    text = formatFiat(fiat),
                    style = ComposeAppTheme.typography.subhead,
                    color = ComposeAppTheme.colors.grey,
                )
            }
        }
    }
}

/** USD value formatting matching swap-bot: no decimals ≥ $1,000, two decimals ≥ $1, else compact. */
private fun formatFiat(value: BigDecimal): String {
    val abs = value.abs()
    val format = when {
        abs.signum() == 0 -> return "$0"
        abs >= BigDecimal(1000) -> DecimalFormat("#,##0")
        abs >= BigDecimal.ONE -> DecimalFormat("#,##0.00")
        else -> DecimalFormat("0.####")
    }
    return "$" + format.format(value)
}

/** Circular switch button, with a background-coloured ring so it cleanly straddles the divider. */
@Composable
private fun SwitchPairsButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(ComposeAppTheme.colors.tyler)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(ComposeAppTheme.colors.blade)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_down_24),
                contentDescription = "Switch tokens",
                tint = ComposeAppTheme.colors.leah,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Human-readable network badge text from the token's chain id (e.g. "optimism" -> "Optimism"). */
private fun networkName(token: SwapToken): String =
    token.chain.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

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
