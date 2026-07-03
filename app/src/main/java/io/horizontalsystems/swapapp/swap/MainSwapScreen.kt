package io.horizontalsystems.swapapp.swap

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.horizontalsystems.swapapp.R
import io.horizontalsystems.swapapp.components.HSScaffold
import io.horizontalsystems.swapapp.compose.ComposeAppTheme
import io.horizontalsystems.swapapp.compose.TranslatableString
import io.horizontalsystems.swapapp.compose.components.Badge
import io.horizontalsystems.swapapp.compose.components.MenuItem
import io.horizontalsystems.swapapp.compose.components.ButtonPrimaryYellow
import io.horizontalsystems.swapapp.compose.components.CoinImage
import io.horizontalsystems.swapapp.compose.components.HSpacer
import io.horizontalsystems.swapapp.compose.components.HsDivider
import io.horizontalsystems.swapapp.compose.components.VSpacer
import java.math.BigDecimal
import java.math.RoundingMode

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
    onOpenHistory: () -> Unit,
    onProceed: (
        amountIn: BigDecimal,
        tokenIn: SwapToken,
        tokenOut: SwapToken,
        selectedProvider: SwapProvider,
        amountOut: BigDecimal?,
        fiatIn: BigDecimal?,
        fiatOut: BigDecimal?,
    ) -> Unit,
) {
    val viewModel = viewModel<MainSwapViewModel>()
    val uiState = viewModel.uiState
    var selecting by remember { mutableStateOf<SelectTarget?>(null) }
    var showProviders by remember { mutableStateOf(false) }

    // System back gesture/button closes an open overlay instead of falling through to the activity.
    BackHandler(enabled = selecting != null || showProviders) {
        selecting = null
        showProviders = false
    }

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

        showProviders -> SwapSelectRouteScreen(
            quotes = uiState.quotes,
            selectedQuote = uiState.quote,
            priceOut = uiState.priceOut,
            onSelectQuote = {
                viewModel.onSelectQuote(it)
                showProviders = false
            },
            onClose = { showProviders = false },
        )

        else -> SwapForm(
            uiState = uiState,
            onClose = onClose,
            onOpenHistory = onOpenHistory,
            onEnterAmount = viewModel::onEnterAmount,
            onSwitchPairs = viewModel::onSwitchPairs,
            onClickTokenIn = { selecting = SelectTarget.In },
            onClickTokenOut = { selecting = SelectTarget.Out },
            onClickProviders = { showProviders = true },
            onClickNext = {
                val s = uiState
                if (s.canProceed) {
                    onProceed(
                        s.amountIn!!, s.tokenIn!!, s.tokenOut!!, s.selectedProvider!!,
                        s.amountOut, s.fiatIn, s.fiatOut,
                    )
                }
            },
        )
    }
}

@Composable
private fun SwapForm(
    uiState: MainSwapUiState,
    onClose: () -> Unit,
    onOpenHistory: () -> Unit,
    onEnterAmount: (BigDecimal?) -> Unit,
    onSwitchPairs: () -> Unit,
    onClickTokenIn: () -> Unit,
    onClickTokenOut: () -> Unit,
    onClickProviders: () -> Unit,
    onClickNext: () -> Unit,
) {
    HSScaffold(
        title = "Swap",
        menuItems = listOf(
            MenuItem(
                title = TranslatableString.PlainString("Swap History"),
                icon = R.drawable.clock_24,
                tint = ComposeAppTheme.colors.grey,
                onClick = onOpenHistory,
            ),
        ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Two token rows separated by a divider, with the switch button centered on it.
            Box(contentAlignment = Alignment.Center) {
                Column {
                    // 'You pay' — token amount and fiat amount, both editable.
                    SwapTokenRow(token = uiState.tokenIn, onClickToken = onClickTokenIn) {
                        PayAmount(
                            amountIn = uiState.amountIn,
                            priceIn = uiState.priceIn,
                            decimals = uiState.tokenIn?.decimals ?: 8,
                            onEnterAmount = onEnterAmount,
                        )
                    }

                    HsDivider()

                    // 'You get' — read-only, populated by the fetched quote.
                    SwapTokenRow(token = uiState.tokenOut, onClickToken = onClickTokenOut) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = formatCoinAmount(uiState.amountOut, uiState.tokenOut?.decimals ?: 8),
                                style = ComposeAppTheme.typography.headline1,
                                color = if (uiState.amountOut != null) ComposeAppTheme.colors.leah else ComposeAppTheme.colors.grey,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // Always rendered (with a "$0" fallback, like the reference's
                            // SwapCoinInputTo) so the row doesn't grow when the price arrives.
                            Text(
                                text = uiState.fiatOut?.let { formatFiat(it) } ?: "$0",
                                style = ComposeAppTheme.typography.body,
                                color = ComposeAppTheme.colors.grey,
                            )
                        }
                    }
                }

                SwitchPairsButton(onClick = onSwitchPairs)
            }

            // White bottom sheet: the route cell at the top, the primary button pinned at the
            // bottom edge; everything between stays clean white space.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(ComposeAppTheme.colors.lawrence)
            ) {
                // Route cell — shows the winning quote's rate and opens the routes list.
                if (uiState.quotes.isNotEmpty()) {
                    // Price direction resets when the pair changes, mirroring the reference SwapPage.
                    var showRegularPrice by remember(uiState.tokenIn, uiState.tokenOut) {
                        mutableStateOf(true)
                    }
                    RouteSelectorRow(
                        rate = rateText(uiState.quote, showRegularPrice),
                        onClickRoute = onClickProviders,
                        onClickPrice = { showRegularPrice = !showRegularPrice },
                    )
                }
                uiState.error?.let {
                    Text(
                        text = errorMessage(it),
                        style = ComposeAppTheme.typography.subhead,
                        color = ComposeAppTheme.colors.lucian,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                ButtonPrimaryYellow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    title = when {
                        uiState.quoting -> "Quoting"
                        uiState.tokenIn == null || uiState.tokenOut == null -> "Select Tokens"
                        uiState.amountIn == null || uiState.amountIn.signum() <= 0 -> "Enter Amount"
                        else -> "Next"
                    },
                    enabled = uiState.canProceed,
                    loadingIndicator = uiState.quoting,
                    onClick = onClickNext,
                )
            }
        }
    }
}

/**
 * A flat token row: tappable coin selector (image + ticker + network badge + caret) on the left,
 * and the amount ([amountContent]) on the right. Sizes, styles and paddings mirror the reference
 * wallet's `SwapCoinInput*`/`CoinSelector`/`Selector` in `multiswap.SwapPage`.
 */
@Composable
private fun SwapTokenRow(
    token: SwapToken?,
    onClickToken: () -> Unit,
    amountContent: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.clickable(
                interactionSource = null,
                indication = null,
                onClick = onClickToken,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (token != null) {
                CoinImage(url = token.logoUrl, modifier = Modifier.size(32.dp))
            } else {
                Image(
                    painter = painterResource(R.drawable.coin_placeholder),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
            }
            HSpacer(16.dp)
            if (token != null) {
                Column {
                    Text(
                        text = token.ticker,
                        style = ComposeAppTheme.typography.headline2,
                        color = ComposeAppTheme.colors.leah,
                    )
                    VSpacer(5.dp)
                    Badge(text = networkName(token))
                }
            } else {
                Text(
                    text = "Select",
                    style = ComposeAppTheme.typography.headline2,
                    color = ComposeAppTheme.colors.jacob,
                )
            }
            HSpacer(8.dp)
            Icon(
                painter = painterResource(R.drawable.arrow_s_down_20),
                contentDescription = null,
                tint = ComposeAppTheme.colors.leah,
            )
        }
        HSpacer(8.dp)
        Box(modifier = Modifier.weight(1f)) {
            amountContent()
        }
    }
}

/**
 * The editable 'You pay' amount: a token-denominated field on top and a USD field below, each
 * driving the other — ported from Unstoppable's `AmountInput`/`FiatAmountInput`. We have no
 * `FiatService`, so the coin↔fiat conversion happens here via [priceIn]; [onEnterAmount] always
 * receives the token amount, so the quote engine is unaffected. The fiat field accepts input only
 * when a price is known.
 */
@Composable
private fun PayAmount(
    amountIn: BigDecimal?,
    priceIn: BigDecimal?,
    decimals: Int,
    onEnterAmount: (BigDecimal?) -> Unit,
) {
    fun fiatFor(coin: BigDecimal?): BigDecimal? =
        if (coin != null && priceIn != null) {
            coin.multiply(priceIn).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
        } else {
            null
        }

    var coinAmount by remember { mutableStateOf(amountIn) }
    var fiatAmount by remember { mutableStateOf(fiatFor(amountIn)) }

    // Resync both fields when the coin amount changes outside this input (e.g. switching pairs).
    LaunchedEffect(amountIn) {
        if (amountIn?.stripTrailingZeros() != coinAmount?.stripTrailingZeros()) {
            coinAmount = amountIn
            fiatAmount = fiatFor(amountIn)
        }
    }
    // Populate the fiat field once a price arrives (without disturbing active typing).
    LaunchedEffect(priceIn) {
        fiatAmount = fiatFor(coinAmount)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        AmountInput(
            value = coinAmount,
            onValueChange = { coin ->
                coinAmount = coin
                fiatAmount = fiatFor(coin)
                onEnterAmount(coin)
            },
        )
        FiatAmountInput(
            value = fiatAmount,
            enabled = priceIn != null,
            onValueChange = { fiat ->
                fiatAmount = fiat
                val coin = if (fiat != null && priceIn != null && priceIn.signum() > 0) {
                    // Don't push the token's full 18-decimal quotient into the field — keep only
                    // the significant digits, matching how amounts are shown elsewhere in the app.
                    roundCoinAmount(fiat.divide(priceIn, decimals, RoundingMode.DOWN), decimals)
                } else {
                    null
                }
                coinAmount = coin
                onEnterAmount(coin)
            },
        )
    }
}

/**
 * Token-denominated amount field — a faithful port of Unstoppable's `AmountInput`: full-width,
 * right-aligned, with the cursor sent to the end on focus and a right-aligned "0" placeholder.
 */
@Composable
private fun AmountInput(
    value: BigDecimal?,
    onValueChange: (BigDecimal?) -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf(value) }
    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(text = amount?.toPlainString() ?: ""))
    }

    LaunchedEffect(value) {
        if (value?.stripTrailingZeros() != amount?.stripTrailingZeros()) {
            amount = value
            textFieldValue = TextFieldValue(text = amount?.toPlainString() ?: "")
        }
    }

    var setCursorToEndOnFocused by remember { mutableStateOf(false) }

    BasicTextField(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged {
                setCursorToEndOnFocused = it.isFocused
                if (!it.isFocused) {
                    textFieldValue = textFieldValue.copy(selection = TextRange.Zero)
                }
            },
        value = textFieldValue,
        onValueChange = { newValue ->
            try {
                val text = newValue.text
                amount = if (text.isBlank()) null else text.toBigDecimal()

                textFieldValue = if (!setCursorToEndOnFocused) {
                    newValue
                } else {
                    setCursorToEndOnFocused = false
                    newValue.copy(selection = TextRange(text.length))
                }
                onValueChange(amount)
            } catch (e: Exception) {
                // Reject keystrokes that don't form a valid number (e.g. a second dot).
            }
        },
        textStyle = ComposeAppTheme.typography.headline1.copy(
            color = ComposeAppTheme.colors.leah,
            textAlign = TextAlign.End,
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        cursorBrush = SolidColor(ComposeAppTheme.colors.leah),
        decorationBox = { innerTextField ->
            if (textFieldValue.text.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Text(
                        text = "0",
                        style = ComposeAppTheme.typography.headline1,
                        color = ComposeAppTheme.colors.grey,
                    )
                }
            }
            innerTextField()
        },
    )
}

/**
 * USD amount field — a faithful port of Unstoppable's `FiatAmountInput`: the "$" prefix (and the
 * empty-state "0") is rendered via a [VisualTransformation] so it always hugs the digits.
 */
@Composable
private fun FiatAmountInput(
    value: BigDecimal?,
    enabled: Boolean,
    onValueChange: (BigDecimal?) -> Unit,
) {
    val symbol = "$"
    var text by remember(value) { mutableStateOf(value?.toPlainString() ?: "") }
    val displayTransformation = remember {
        VisualTransformation { original ->
            val prefixLen = symbol.length
            val isEmpty = original.text.isEmpty()
            val visual = AnnotatedString(symbol + original.text + if (isEmpty) "0" else "")
            TransformedText(
                text = visual,
                offsetMapping = object : OffsetMapping {
                    override fun originalToTransformed(offset: Int) =
                        if (isEmpty) prefixLen + 1 else offset + prefixLen

                    override fun transformedToOriginal(offset: Int) =
                        (offset - prefixLen).coerceIn(0, original.text.length)
                },
            )
        }
    }
    BasicTextField(
        modifier = Modifier.fillMaxWidth(),
        value = text,
        onValueChange = {
            try {
                val amount = if (it.isBlank()) null else it.toBigDecimal()
                text = it
                onValueChange(amount)
            } catch (e: Exception) {
                // Reject invalid number input.
            }
        },
        enabled = enabled,
        textStyle = ComposeAppTheme.typography.body.copy(
            color = ComposeAppTheme.colors.grey,
            textAlign = TextAlign.End,
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        cursorBrush = SolidColor(ComposeAppTheme.colors.leah),
        visualTransformation = displayTransformation,
    )
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

/**
 * The route cell on the white area, split like the reference's `ProviderCellInfo`: only the
 * "Route ▾" selector on the left opens the routes list, while tapping the price on the right
 * toggles its direction ("1 BTC = 2980 USDT" ↔ "1 USDT = 0.00033 BTC").
 */
@Composable
private fun RouteSelectorRow(
    rate: String?,
    onClickRoute: () -> Unit,
    onClickPrice: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left selector — the only part that opens the routes list (reference: LeftSelector).
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onClickRoute),
        ) {
            Text(
                text = "Route",
                style = ComposeAppTheme.typography.subhead,
                color = ComposeAppTheme.colors.leah,
            )
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(R.drawable.arrow_s_down_24),
                contentDescription = null,
                tint = ComposeAppTheme.colors.leah,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        // Price — its own ripple-free click target that flips the rate direction (reference:
        // CellRightControlsButtonText).
        rate?.let {
            Text(
                text = it,
                style = ComposeAppTheme.typography.subhead,
                color = ComposeAppTheme.colors.leah,
                modifier = Modifier.clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onClickPrice,
                ),
            )
        }
    }
}

/**
 * The selected quote's rate, ported from the reference `SwapPriceUIHelper`: "1 BTC = 2980 USDT"
 * when [regular], the inverted "1 USDT = 0.00033 BTC" otherwise; null without a complete quote.
 */
private fun rateText(quote: SwapProviderQuote?, regular: Boolean): String? {
    if (quote == null || quote.amountIn.signum() <= 0 || quote.amountOut.signum() <= 0) return null
    return if (regular) {
        val price = quote.amountOut
            .divide(quote.amountIn, quote.tokenOut.decimals, RoundingMode.HALF_EVEN)
        "1 ${quote.tokenIn.ticker} = ${formatCoinAmount(price, quote.tokenOut.decimals)} ${quote.tokenOut.ticker}"
    } else {
        val priceInv = quote.amountIn
            .divide(quote.amountOut, quote.tokenIn.decimals, RoundingMode.HALF_EVEN)
        "1 ${quote.tokenOut.ticker} = ${formatCoinAmount(priceInv, quote.tokenIn.decimals)} ${quote.tokenIn.ticker}"
    }
}

/** User-facing message for a quote error — never the raw "HTTP 404" / host-resolution text. */
private fun errorMessage(error: Throwable): String = when (error) {
    is NoSupportedSwapProvider -> "No provider supports this pair"
    is SwapRouteNotFound -> "No route found for this pair"
    is java.io.IOException -> "Network error. Check your connection."
    else -> "Couldn't fetch a quote"
}
