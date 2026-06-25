package io.horizontalsystems.swapapp.swap.execution

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Generates (and caches) a QR [ImageBitmap] for [content] using zxing. Black modules on a white
 * background so it scans regardless of the app theme.
 */
@Composable
fun rememberQrBitmap(content: String, sizePx: Int = 512): ImageBitmap? {
    return remember(content, sizePx) {
        runCatching { encodeQr(content, sizePx) }.getOrNull()
    }
}

private fun encodeQr(content: String, size: Int): ImageBitmap {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1,
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    return bitmap.asImageBitmap()
}
