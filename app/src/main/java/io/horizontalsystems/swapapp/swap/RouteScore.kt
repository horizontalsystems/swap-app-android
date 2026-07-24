package io.horizontalsystems.swapapp.swap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.horizontalsystems.swapapp.R
import io.horizontalsystems.swapapp.compose.ComposeAppTheme
import io.horizontalsystems.swapapp.compose.components.ButtonPrimary
import io.horizontalsystems.swapapp.compose.components.ButtonPrimaryDefaults
import io.horizontalsystems.swapapp.compose.components.HSpacer
import io.horizontalsystems.swapapp.compose.components.HsDivider
import io.horizontalsystems.swapapp.compose.components.VSpacer
import kotlinx.coroutines.launch

/** Colored label + 20dp icon for a route's safety rating — ported from the reference `RiskScore`. */
@Composable
fun RatingBadge(
    rating: RouteRating,
    modifier: Modifier = Modifier,
) {
    val color = ratingColor(rating)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rating.label,
            style = ComposeAppTheme.typography.subheadSB,
            color = color,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
        HSpacer(4.dp)
        Icon(
            painter = painterResource(ratingIcon(rating)),
            modifier = Modifier.size(20.dp),
            tint = color,
            contentDescription = null,
        )
    }
}

/**
 * The "Provider Score" row shown on the main swap screen and reused as a tappable target: a grey
 * label + info icon on the left (opens [RouteScoreInfoSheet] via [onClick]) and the selected route's
 * [RatingBadge] on the right. Mirrors the reference `SwapPage.ProviderCellInfo`'s risk-level cell.
 */
@Composable
fun ProviderScoreRow(
    rating: RouteRating,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Provider Score",
            style = ComposeAppTheme.typography.subhead,
            color = ComposeAppTheme.colors.grey,
        )
        HSpacer(6.dp)
        Icon(
            painter = painterResource(R.drawable.info_20),
            modifier = Modifier.size(20.dp),
            tint = ComposeAppTheme.colors.grey,
            contentDescription = null,
        )
        Spacer(modifier = Modifier.weight(1f))
        RatingBadge(rating)
    }
}

/**
 * Bottom sheet explaining what the route scores mean, ported from the reference
 * `RiskLevelInfoSheet`: a title, a short intro, then a bordered card with one row per
 * [RouteRating] (colored label + icon + description), and a "Close" button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteScoreInfoSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ComposeAppTheme.colors.lawrence,
    ) {
        Text(
            text = "Route Score",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            style = ComposeAppTheme.typography.headline1,
            color = ComposeAppTheme.colors.leah,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "These scores show how each route handles swaps and transaction checks.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 12.dp),
            style = ComposeAppTheme.typography.subhead,
            color = ComposeAppTheme.colors.grey,
        )
        VSpacer(8.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, ComposeAppTheme.colors.blade, RoundedCornerShape(16.dp))
                .background(ComposeAppTheme.colors.lawrence),
        ) {
            RouteRating.entries.forEachIndexed { index, rating ->
                if (index > 0) HsDivider()
                RouteScoreCell(rating)
            }
        }
        VSpacer(24.dp)
        ButtonPrimary(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            onClick = { close() },
            buttonColors = ButtonPrimaryDefaults.textButtonColors(
                backgroundColor = ComposeAppTheme.colors.leah,
                contentColor = ComposeAppTheme.colors.lawrence,
                disabledBackgroundColor = ComposeAppTheme.colors.blade,
                disabledContentColor = ComposeAppTheme.colors.andy,
            ),
            content = {
                Text(
                    text = "Close",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        VSpacer(32.dp)
    }
}

@Composable
private fun RouteScoreCell(rating: RouteRating) {
    val color = ratingColor(rating)
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(ratingIcon(rating)),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
            HSpacer(8.dp)
            Text(
                text = rating.label,
                style = ComposeAppTheme.typography.subheadSB,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        VSpacer(4.dp)
        Text(
            text = ratingDescription(rating),
            style = ComposeAppTheme.typography.subhead,
            color = ComposeAppTheme.colors.grey,
        )
    }
}

@Composable
private fun ratingColor(rating: RouteRating): Color = when (rating) {
    RouteRating.Excellent -> ComposeAppTheme.colors.remus
    RouteRating.Good -> ComposeAppTheme.colors.issykBlue
    RouteRating.Fair -> ComposeAppTheme.colors.jacob
}

private fun ratingIcon(rating: RouteRating): Int = when (rating) {
    RouteRating.Excellent -> R.drawable.star_filled_24
    RouteRating.Good -> R.drawable.shield_check_filled_24
    RouteRating.Fair -> R.drawable.thumbsup_24
}

private fun ratingDescription(rating: RouteRating): String = when (rating) {
    RouteRating.Excellent ->
        "Direct on-chain execution. No checks or freezes. Automatic refund if the swap fails."
    RouteRating.Good ->
        "Transactions are checked automatically before completion. If an issue is found, the swap is rejected and funds are refunded."
    RouteRating.Fair ->
        "Some transactions may need extra verification. If an issue is found, funds are usually refunded automatically."
}