package io.horizontalsystems.swapapp.swap.execution

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/**
 * A modern, styled QR code for [content]: data modules are drawn as rounded dots and the three
 * corner finder patterns as rounded "eyes". Rendered as scalable vector graphics on a Compose
 * [Canvas] (no bitmap), so it stays crisp at any size.
 *
 * Encoding reuses zxing — the same library already shipping in the app — via its low-level
 * [Encoder], which yields the exact module grid (1 cell per module) we draw ourselves. Colors
 * default to black-on-white for maximum scan contrast regardless of app theme.
 */
@Composable
fun QrCode(
    content: String,
    modifier: Modifier = Modifier,
    darkColor: Color = Color.Black,
    lightColor: Color = Color.White,
    // Quiet zone (blank border) in modules. The QR spec recommends 4; we keep a slightly tighter
    // margin since the surrounding card already adds whitespace.
    quietZoneModules: Int = 3,
) {
    val matrix = remember(content) { runCatching { encodeQrMatrix(content) }.getOrNull() }

    Canvas(modifier = modifier) {
        drawRect(color = lightColor)
        matrix?.let { drawQr(it, darkColor, quietZoneModules) }
    }
}

private fun DrawScope.drawQr(matrix: QrMatrix, color: Color, quietZoneModules: Int) {
    val moduleCount = matrix.size
    val totalModules = moduleCount + quietZoneModules * 2
    val cell = size.minDimension / totalModules
    // Center the (square) code within the (possibly larger) canvas.
    val origin = Offset(
        x = (size.width - cell * totalModules) / 2f + quietZoneModules * cell,
        y = (size.height - cell * totalModules) / 2f + quietZoneModules * cell,
    )

    fun topLeftOf(col: Int, row: Int) = Offset(origin.x + col * cell, origin.y + row * cell)

    // Data modules as dots, skipping the three finder patterns (drawn as styled eyes below).
    val dotRadius = cell * 0.46f
    for (row in 0 until moduleCount) {
        for (col in 0 until moduleCount) {
            if (!matrix[col, row] || matrix.isFinder(col, row)) continue
            val tl = topLeftOf(col, row)
            drawCircle(color = color, radius = dotRadius, center = Offset(tl.x + cell / 2f, tl.y + cell / 2f))
        }
    }

    // Finder "eyes": a rounded 7x7 ring with a rounded 3x3 pupil.
    for ((fx, fy) in matrix.finderOrigins()) {
        val outer = topLeftOf(fx, fy)
        drawRoundRect(
            color = color,
            topLeft = Offset(outer.x + cell / 2f, outer.y + cell / 2f),
            size = Size(cell * 6f, cell * 6f),
            cornerRadius = CornerRadius(cell * 2f, cell * 2f),
            style = Stroke(width = cell),
        )
        val pupil = topLeftOf(fx + 2, fy + 2)
        drawRoundRect(
            color = color,
            topLeft = pupil,
            size = Size(cell * 3f, cell * 3f),
            cornerRadius = CornerRadius(cell, cell),
        )
    }
}

/** Square boolean module grid, indexed `[col, row]`. */
private class QrMatrix(val size: Int, private val cells: BooleanArray) {
    operator fun get(col: Int, row: Int) = cells[row * size + col]

    /** Top-left module of each finder pattern (top-left, top-right, bottom-left). */
    fun finderOrigins() = listOf(0 to 0, size - 7 to 0, 0 to size - 7)

    /** True when (col, row) lies inside any 7x7 finder pattern. */
    fun isFinder(col: Int, row: Int) = finderOrigins().any { (fx, fy) ->
        col in fx until fx + 7 && row in fy until fy + 7
    }
}

private fun encodeQrMatrix(content: String): QrMatrix {
    val hints = mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")
    val byteMatrix = Encoder.encode(content, ErrorCorrectionLevel.M, hints).matrix
    val size = byteMatrix.width // square
    val cells = BooleanArray(size * size) { i ->
        byteMatrix.get(i % size, i / size).toInt() == 1
    }
    return QrMatrix(size, cells)
}
