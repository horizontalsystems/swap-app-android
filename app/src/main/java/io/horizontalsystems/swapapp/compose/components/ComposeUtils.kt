package io.horizontalsystems.swapapp.compose.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    HsImage(
        url = url,
        alternativeUrl = alternativeUrl,
        placeholder = placeholder,
        modifier = modifier.clip(CircleShape),
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