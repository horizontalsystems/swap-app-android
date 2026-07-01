package io.horizontalsystems.swapapp.compose.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.rememberAsyncImagePainter
import io.horizontalsystems.swapapp.R
import io.horizontalsystems.swapapp.compose.ComposeAppTheme
import java.math.BigDecimal

@Composable
fun diffColor(value: BigDecimal?): Color {
    val diff = value ?: BigDecimal.ZERO
    return when {
        diff.signum() == 0 -> ComposeAppTheme.colors.grey
        diff.signum() >= 0 -> ComposeAppTheme.colors.remus
        else -> ComposeAppTheme.colors.lucian
    }
}

/**
 * Circular coin/token image loaded from a remote URL with a placeholder fallback.
 * Decoupled from the wallet's Coin/Token models — pass the image URL directly.
 */
@Composable
fun CoinImage(
    url: String?,
    modifier: Modifier = Modifier,
    alternativeUrl: String? = null,
    placeholder: Int? = null,
    colorFilter: ColorFilter? = null,
) = HsImageCircle(
    modifier = modifier,
    url = url,
    alternativeUrl = alternativeUrl,
    placeholder = placeholder,
    colorFilter = colorFilter,
)

@Composable
fun HsImageCircle(
    modifier: Modifier = Modifier,
    url: String?,
    alternativeUrl: String? = null,
    placeholder: Int? = null,
    colorFilter: ColorFilter? = null
) {
    // Back the icon with a filled, bordered circle so logos with a transparent background still read
    // as a structured coin chip (and full-bleed logos simply cover the fill).
    HsImage(
        url = url,
        alternativeUrl = alternativeUrl,
        placeholder = placeholder,
        modifier = modifier
            .clip(CircleShape)
            .background(ComposeAppTheme.colors.white)
            .border(0.5.dp, ComposeAppTheme.colors.blade, CircleShape),
        colorFilter = colorFilter
    )
}

@Composable
fun HsImage(
    url: String?,
    alternativeUrl: String? = null,
    placeholder: Int? = null,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null
) {
    val fallback = placeholder ?: R.drawable.coin_placeholder

    when {
        url != null -> Image(
            painter = rememberAsyncImagePainter(
                model = url,
                placeholder = painterResource(fallback),
                error = alternativeUrl?.let {
                    rememberAsyncImagePainter(
                        model = it,
                        placeholder = painterResource(fallback),
                        error = painterResource(fallback)
                    )
                } ?: painterResource(fallback),
            ),
            contentDescription = null,
            modifier = modifier,
            colorFilter = colorFilter,
            contentScale = ContentScale.FillBounds
        )

        else -> Image(
            painter = painterResource(fallback),
            contentDescription = null,
            modifier = modifier,
            colorFilter = colorFilter
        )
    }
}