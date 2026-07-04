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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.horizontalsystems.swapapp.R
import io.horizontalsystems.swapapp.components.HSScaffold
import io.horizontalsystems.swapapp.components.cell.CellMiddleInfo
import io.horizontalsystems.swapapp.components.cell.CellMiddleInfoTextIcon
import io.horizontalsystems.swapapp.components.cell.CellPrimary
import io.horizontalsystems.swapapp.components.cell.CellRightInfo
import io.horizontalsystems.swapapp.components.cell.CellRightInfoTextIcon
import io.horizontalsystems.swapapp.components.cell.CellRightNavigation
import io.horizontalsystems.swapapp.components.cell.CellSecondary
import io.horizontalsystems.swapapp.components.cell.hs
import io.horizontalsystems.swapapp.compose.ComposeAppTheme
import io.horizontalsystems.swapapp.compose.components.HsDivider
import io.horizontalsystems.swapapp.compose.components.HsImageCircle
import io.horizontalsystems.swapapp.compose.components.VSpacer
import io.horizontalsystems.swapapp.compose.components.subheadSB_grey
import io.horizontalsystems.swapapp.swap.execution.SwapLeg
import io.horizontalsystems.swapapp.swap.execution.SwapStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Detail view for a single recorded swap: the paid → received pair, route/date details, and a live
 * transaction-status tracker (Depositing → Swap → Send) driven by [SwapInfoViewModel]. Ported 1:1
 * from walletkit's `multiswap.history.SwapInfoPage`.
 */
@Composable
fun SwapInfoScreen(
    uiState: SwapInfoUiState,
    onBack: () -> Unit,
) {
    val record = uiState.record
    val leah = ComposeAppTheme.colors.leah

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
                .verticalScroll(rememberScrollState()),
        ) {
            VSpacer(12.dp)

            // Token pair card
            Box(
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(ComposeAppTheme.colors.lawrence),
                ) {
                    CellPrimary(
                        left = {
                            HsImageCircle(
                                modifier = Modifier.size(32.dp),
                                url = record.tokenIn.logoUrl,
                                placeholder = R.drawable.coin_placeholder,
                            )
                        },
                        middle = {
                            CellMiddleInfo(
                                title = record.tokenIn.ticker.hs,
                                subtitle = record.tokenIn.network.hs,
                            )
                        },
                        right = {
                            CellRightInfo(
                                titleSubheadSb = record.amountIn.hs,
                                subtitle = record.fiatIn?.hs,
                            )
                        },
                    )
                    CellPrimary(
                        left = {
                            HsImageCircle(
                                modifier = Modifier.size(32.dp),
                                url = record.tokenOut.logoUrl,
                                placeholder = R.drawable.coin_placeholder,
                            )
                        },
                        middle = {
                            CellMiddleInfo(
                                title = record.tokenOut.ticker.hs,
                                subtitle = record.tokenOut.network.hs,
                            )
                        },
                        right = {
                            CellRightInfo(
                                titleSubheadSb = (record.amountOut ?: "---").hs,
                                subtitle = record.fiatOut?.hs,
                            )
                        },
                    )
                }
                HsDivider(
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Icon(
                    modifier = Modifier
                        .size(20.dp)
                        .background(ComposeAppTheme.colors.lawrence),
                    painter = painterResource(R.drawable.ic_arrow_down_20),
                    tint = ComposeAppTheme.colors.grey,
                    contentDescription = null,
                )
            }

            VSpacer(16.dp)

            // Details card
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ComposeAppTheme.colors.lawrence)
                    .padding(vertical = 8.dp),
            ) {
                // Provider
                CellSecondary(
                    middle = {
                        CellMiddleInfoTextIcon(text = "Route".hs)
                    },
                    right = {
                        CellRightInfoTextIcon(text = record.providerTitle.hs(color = leah))
                    },
                )
                // Date
                CellSecondary(
                    middle = {
                        CellMiddleInfoTextIcon(text = "Date".hs)
                    },
                    right = {
                        CellRightInfoTextIcon(
                            text = SimpleDateFormat("MMM d, yyyy, HH:mm", Locale.getDefault())
                                .format(Date(record.createdAt))
                                .hs(color = leah)
                        )
                    },
                )
            }

            VSpacer(24.dp)

            subheadSB_grey(
                text = "Transaction Status",
                modifier = Modifier.padding(horizontal = 32.dp),
            )

            VSpacer(12.dp)

            SwapStatusSteps(
                status = uiState.status,
                legs = uiState.legs,
            )

            VSpacer(32.dp)
        }
    }
}

@Composable
private fun SwapStatusSteps(status: SwapStatus, legs: List<SwapLeg>) {
    val context = LocalContext.current
    val normalSteps = listOf("Depositing", "Swap", "Send")
    val refundedSteps = listOf("Depositing", "Swap", "Refunded")

    val steps: List<String>
    val activeIndex: Int
    val failedIndex: Int?

    when (status) {
        SwapStatus.Refunded -> {
            steps = refundedSteps
            activeIndex = steps.size
            failedIndex = null
        }

        SwapStatus.Failed,
        SwapStatus.ActionRequired -> {
            steps = normalSteps.take(2)
            activeIndex = 1
            failedIndex = 1
        }

        SwapStatus.NotStarted,
        SwapStatus.Pending,
        SwapStatus.Unknown -> {
            steps = normalSteps
            activeIndex = 0
            failedIndex = null
        }

        SwapStatus.Swapping -> {
            steps = normalSteps
            activeIndex = 1
            failedIndex = null
        }

        SwapStatus.Completed -> {
            steps = normalSteps
            activeIndex = steps.size
            failedIndex = null
        }
    }

    val green = ComposeAppTheme.colors.remus
    val blade = ComposeAppTheme.colors.blade

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ComposeAppTheme.colors.lawrence)
            .padding(vertical = 8.dp)
    ) {
        steps.forEachIndexed { index, label ->
            val isFailed = failedIndex == index
            val isDone = activeIndex > index
            val isActive = activeIndex == index && !isFailed
            val isFirst = index == 0
            val isLast = index == steps.lastIndex
            val stepUrl: String? = when (index) {
                0 -> legs.getOrNull(0)?.let { explorerUrl(it.chainId, it.hash) }
                1 if steps.size > 2 -> legs.getOrNull(1)?.let { explorerUrl(it.chainId, it.hash) }
                2 if steps.size == 3 -> legs.getOrNull(2)?.let { explorerUrl(it.chainId, it.hash) }
                else -> null
            }
            val showView = stepUrl != null && (isDone || isActive || isFailed)
            val isPrevDone = index > 0 && activeIndex >= index
            val topConnectorColor = if (isPrevDone) green else blade
            val bottomConnectorColor = if (isDone) green else blade

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .then(if (showView) Modifier.clickable {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, stepUrl.toUri()))
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "No app found to open the link", Toast.LENGTH_SHORT).show()
                        }
                    } else Modifier)
                    .padding(end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: connector lines + step indicator
                Column(
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .weight(1f)
                            .background(if (isFirst) Color.Transparent else topConnectorColor)
                    )
                    StepIndicator(isActive = isActive, isDone = isDone, isFailed = isFailed)
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .weight(1f)
                            .background(if (isLast) Color.Transparent else bottomConnectorColor)
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    CellMiddleInfoTextIcon(
                        text = label.hs(
                            color = if (isActive || isDone || isFailed) ComposeAppTheme.colors.leah else null
                        )
                    )
                }

                if (showView) {
                    Box(
                        modifier = Modifier.widthIn(max = 200.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        CellRightNavigation(subtitle = "View".hs)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(isActive: Boolean, isDone: Boolean, isFailed: Boolean = false) {
    when {
        isActive -> Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(R.drawable.ic_circle_placeholder_20),
                tint = ComposeAppTheme.colors.blade,
                contentDescription = null,
            )
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = ComposeAppTheme.colors.leah,
                backgroundColor = Color.Transparent,
                strokeWidth = 2.dp,
            )
        }

        isFailed -> Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(R.drawable.ic_circle_placeholder_20),
                tint = ComposeAppTheme.colors.blade,
                contentDescription = null,
            )
            Icon(
                modifier = Modifier.size(11.dp),
                painter = painterResource(R.drawable.ic_failed_cross),
                tint = ComposeAppTheme.colors.lucian,
                contentDescription = null,
            )
        }

        isDone -> Icon(
            modifier = Modifier.size(20.dp),
            painter = painterResource(R.drawable.ic_check_filled_20_no_padding),
            tint = ComposeAppTheme.colors.remus,
            contentDescription = null,
        )

        else -> Icon(
            modifier = Modifier.size(20.dp),
            painter = painterResource(R.drawable.ic_circle_placeholder_20),
            tint = ComposeAppTheme.colors.blade,
            contentDescription = null,
        )
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
