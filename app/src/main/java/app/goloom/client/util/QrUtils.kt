package app.goloom.client.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Кодирует строку в QR-битмап. Возвращает квадратный bitmap размера [size]px.
 * Использует ZXing с ECC-уровнем M (good balance между плотностью и устойчивостью).
 *
 * Лучше звать с IO-диспетчера — внутри нет ввода-вывода, но кодирование
 * больших payload'ов на UI-потоке заметно лагает.
 */
object QrUtils {
    private val FG = Color.WHITE
    private val BG = android.graphics.Color.TRANSPARENT

    fun encode(text: String, size: Int = 512): Bitmap? = try {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val w = matrix.width; val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                pixels[row + x] = if (matrix.get(x, y)) FG else BG
            }
        }
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    } catch (t: Throwable) {
        null
    }
}
