package io.horizontalsystems.swapapp.compose

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import io.horizontalsystems.swapapp.R

@Immutable
class Typography internal constructor(
    val title3: TextStyle,
    val headline1: TextStyle,
    val headline2: TextStyle,
    val body: TextStyle,
    val subhead: TextStyle,
    val subheadSB: TextStyle,
    val caption: TextStyle,
    val captionSB: TextStyle,
    val microSB: TextStyle,
) {


    constructor(
        defaultFontFamily: FontFamily = manropeFont,
        title3: TextStyle = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            letterSpacing = 0.sp,
        ),
        headline1: TextStyle = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            letterSpacing = 0.sp,
        ),
        headline2: TextStyle = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            letterSpacing = 0.sp,
        ),
        body: TextStyle = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            letterSpacing = 0.sp,
        ),
        subhead: TextStyle = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            letterSpacing = 0.sp,
        ),
        subheadSB: TextStyle = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            letterSpacing = 0.sp,
        ),
        caption: TextStyle = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            letterSpacing = 0.sp,
        ),
        captionSB: TextStyle = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 0.sp,
        ),
        microSB: TextStyle = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            letterSpacing = 0.sp,
        ),
    ) : this(
        title3 = title3.withFontFamily(defaultFontFamily),
        headline1 = headline1.withFontFamily(defaultFontFamily),
        headline2 = headline2.withFontFamily(defaultFontFamily),
        body = body.withFontFamily(defaultFontFamily),
        subhead = subhead.withFontFamily(defaultFontFamily),
        subheadSB = subheadSB.withFontFamily(defaultFontFamily),
        caption = caption.withFontFamily(defaultFontFamily),
        captionSB = captionSB.withFontFamily(defaultFontFamily),
        microSB = microSB.withFontFamily(defaultFontFamily),
    )

}

fun ColoredTextStyle(textStyle: TextStyle, color: Color, textAlign: TextAlign = TextAlign.Unspecified): TextStyle {
    return textStyle.copy(
        color = color,
        textAlign = textAlign
    )
}

private fun TextStyle.withFontFamily(default: FontFamily): TextStyle {
    return if (fontFamily != null) this else copy(fontFamily = default)
}

internal val LocalTypography = staticCompositionLocalOf { Typography() }

val manropeFont = FontFamily(
    Font(R.font.manrope_regular),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)