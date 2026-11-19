// QR gerado dentro da app (zxing vem no APK): sem internet, o convidado só tem
// de apontar a câmara para o telemóvel do anfitrião.
package com.sueca.cafe.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/** Grelha de módulos do QR, sem zona silenciosa. */
private fun modulos(texto: String): Pair<Int, BooleanArray>? = runCatching {
    val m = Encoder.encode(texto, ErrorCorrectionLevel.M, mapOf<EncodeHintType, Any>()).matrix
    val n = m.width
    val b = BooleanArray(n * n)
    for (y in 0 until n) for (x in 0 until n) b[y * n + x] = m.get(x, y).toInt() == 1
    n to b
}.getOrNull()

@Composable
fun QrCafe(texto: String, tamanho: Dp, modifier: Modifier = Modifier) {
    val grelha = remember(texto) { modulos(texto) }
    Canvas(modifier = modifier.size(tamanho)) {
        drawRect(Cafe.Cream)
        val g = grelha ?: return@Canvas
        val (n, b) = g
        val passo = size.minDimension / (n + 8f)   // 4 módulos de zona silenciosa
        val x0 = passo * 4f
        for (y in 0 until n) {
            for (x in 0 until n) {
                if (b[y * n + x]) {
                    drawRoundRect(
                        Cafe.Espresso,
                        Offset(x0 + x * passo, x0 + y * passo),
                        Size(passo + 0.2f, passo + 0.2f),
                        CornerRadius(passo * 0.22f),
                    )
                }
            }
        }
    }
}
