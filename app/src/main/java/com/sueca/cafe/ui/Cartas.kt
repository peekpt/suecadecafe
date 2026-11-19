// O baralho do café: os rótulos vão em texto por cima, o corpo — figuras,
// vinchas, madeira e copito — é tudo Canvas em paths, sem assets nem emojis.
package com.sueca.cafe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sueca.cafe.RANK_INFO
import com.sueca.cafe.SUIT_INFO
import com.sueca.cafe.rankOf
import com.sueca.cafe.suitOf
import kotlin.math.hypot
import kotlin.math.min

private const val BASE = 58f        // a largura de referência de todas as escalas
private const val RAZAO = 7f / 5f   // 5:7, sempre

// O grayscale(.6) do CSS: cada cor vai 60% da sua luminância, porque a
// camada do 1.7 já não aceita filtro de cor no graphicsLayer.
private fun dessat(c: Color): Color {
    val l = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
    return Color(
        l + (c.red - l) * 0.4f,
        l + (c.green - l) * 0.4f,
        l + (c.blue - l) * 0.4f,
        c.alpha,
    )
}

private fun tom(c: Color, mutada: Boolean) = if (mutada) dessat(c) else c

@Composable
fun Carta(
    card: String,
    width: Dp,
    faceUp: Boolean = true,
    trump: Boolean = false,
    dimmed: Boolean = false,
    showPoints: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val e = Escala(width)
    val face = if (!faceUp) Cafe.Coffee else if (trump) Cafe.TrumpFace else Cafe.CardFace
    var caixa = modifier
        .size(e.largura, e.altura)
        .cartao(
            raio = e.raio,
            cor = if (dimmed) dessat(face) else face,
            pincel = if (faceUp) null else Cafe.Wood,
            espessura = e.traço,
            sombra = e.sombra,
        )
    if (onClick != null) caixa = caixa.premivel { onClick() }
    if (dimmed) caixa = caixa.graphicsLayer { alpha = 0.42f }

    Box(caixa) {
        if (faceUp) DentroFace(e, suitOf(card), rankOf(card), trump, onClick != null, showPoints, dimmed)
        else DentroDorso(dimmed)
    }
}

@Composable
fun DorsoCarta(width: Dp, modifier: Modifier = Modifier) {
    val e = Escala(width)
    Box(
        modifier = modifier
            .size(e.largura, e.altura)
            .cartao(
                raio = e.raio,
                cor = Cafe.Coffee,
                pincel = Cafe.Wood,
                espessura = e.traço,
                sombra = e.sombra,
            ),
    ) {
        DentroDorso()
    }
}

// A pilha dos adversários: só interessa quantas cartas cada um ainda tem.
@Composable
fun PilhaCartas(n: Int, width: Dp, modifier: Modifier = Modifier) {
    val e = Escala(width)
    if (n <= 0) {
        Box(
            modifier = modifier.size(e.largura, e.altura).drawWithCache {
                val passo = size.width * 0.055f
                val tracejado = PathEffect.dashPathEffect(
                    floatArrayOf(size.width * 0.08f, size.width * 0.055f),
                )
                onDrawBehind {
                    drawRect(color = Cafe.Cream.copy(alpha = 0.35f))
                    drawRoundRect(
                        color = Cafe.Espresso.copy(alpha = 0.38f),
                        size = Size(size.width - passo, size.height - passo),
                        cornerRadius = CornerRadius(size.width * 11f / BASE, size.width * 11f / BASE),
                        style = Stroke(width = size.width * 0.03f, pathEffect = tracejado),
                    )
                }
            },
        ) {
            BasicText(
                text = "0",
                style = letra(width * 0.34f, cor = Cafe.Espresso.copy(alpha = 0.45f), display = true),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        return
    }

    val montao = n.coerceAtMost(4)
    val carta = width * 0.60f
    val passo = width * 0.13f
    val sobra = e.largura - carta - passo * (montao - 1) - 3.dp * (carta / BASE.dp)
    Box(modifier.size(e.largura, e.altura)) {
        for (i in 0 until montao) {
            DorsoCarta(
                carta,
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = sobra / 2f + passo * i, y = -passo * (montao - 1 - i) * 0.4f),
            )
        }
        BasicText(
            text = n.toString(),
            style = letra(width * 0.36f, cor = Cafe.Paper, display = true),
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

// ── face ────────────────────────────────────────────────────────────────

@Composable
private fun DentroFace(
    e: Escala,
    naipe: String,
    valor: String,
    trump: Boolean,
    tocavel: Boolean,
    showPoints: Boolean,
    mutada: Boolean,
) {
    val info = SUIT_INFO.getValue(naipe)
    val rango = RANK_INFO.getValue(valor)
    val cor = tom(if (info.dark) Cafe.Espresso else Cafe.Red, mutada)
    val âmbar = tom(Cafe.Amber, mutada)
    val fig = FIGURAS[valor]

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().drawWithCache {
            onDrawBehind {
                val w = size.width
                val h = size.height
                val cantos = CornerRadius(w * 11f / BASE, w * 11f / BASE)
                if (tocavel) drawRoundRect(color = âmbar.copy(alpha = 0.13f), cornerRadius = cantos)
                if (fig != null) figura(fig, cor, mutada)
                if (trump || tocavel) {
                    val beira = w * 0.032f
                    drawRoundRect(
                        color = âmbar.copy(alpha = if (trump) 1f else 0.75f),
                        topLeft = Offset(beira, beira),
                        size = Size(w - beira * 2f, h - beira * 2f),
                        cornerRadius = CornerRadius(w * 0.15f, w * 0.15f),
                        style = Stroke(width = w * 2f / BASE, cap = StrokeCap.Round),
                    )
                }
            }
        })

        Row(
            modifier = Modifier.matchParentSize().padding(
                top = e.altura * 0.030f, start = e.largura * 0.105f, end = e.largura * 0.085f,
            ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            BasicText(text = rango.label, style = letra(e.largura * 0.30f, cor = cor))
            BasicText(text = info.glyph, style = letra(e.largura * 0.27f, cor = cor))
        }

        if (fig == null) {
            val ás = valor == "A"
            BasicText(
                text = info.glyph,
                style = letra(e.largura * if (ás) 0.60f else 0.50f, cor = cor),
                modifier = Modifier.align(Alignment.Center).offset(y = e.altura * 0.085f),
            )
            // O mini-índice só nas cartas de pipa: nas figuras o canto leva a figura.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.BottomStart)
                    .padding(start = e.largura * 0.075f, bottom = e.altura * 0.028f)
                    .rotate(180f),
            ) {
                BasicText(text = rango.label, style = letra(e.largura * 0.21f, cor = cor))
                BasicText(text = info.glyph, style = letra(e.largura * 0.19f, cor = cor))
            }
        }

        if (showPoints && rango.value > 0) {
            val forma = RoundedCornerShape(e.raio * 0.55f)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = e.largura * 0.055f, bottom = e.altura * 0.032f)
                    .background(âmbar, forma)
                    .border(e.traço * 0.7f, Cafe.Espresso, forma)
                    .padding(horizontal = e.largura * 0.032f, vertical = e.altura * 0.006f),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = rango.value.toString(),
                    style = letra(e.largura * 0.20f, display = true, cor = Cafe.Espresso),
                )
            }
        }
    }
}

// ── dorso ───────────────────────────────────────────────────────────────

@Composable
private fun DentroDorso(mutada: Boolean = false) {
    Box(Modifier.fillMaxSize().drawWithCache {
        val passo = size.width * 0.055f
        onDrawBehind {
            drawRect(color = tom(Cafe.Coffee, mutada))
            riscas(passo, mutada)
            copito(mutada)
        }
    })
}

// O repeating-linear-gradient(45deg) da app web, com o recorte a cargo do clip.
private fun DrawScope.riscas(passo: Float, mutada: Boolean = false) {
    val span = hypot(size.width, size.height)
    val clara = tom(Cafe.WoodMid, mutada)
    val escura = tom(Cafe.Coffee, mutada)
    val tela = drawContext.canvas
    tela.save()
    tela.translate(size.width / 2f, size.height / 2f)
    tela.rotate(-45f)
    var i = 0
    while (passo * (i + 0.5f) - span / 2f < span / 2f) {
        drawLine(
            color = if (i % 2 == 0) clara else escura,
            start = Offset(-span, passo * (i + 0.5f) - span / 2f),
            end = Offset(span, passo * (i + 0.5f) - span / 2f),
            strokeWidth = passo,
        )
        i++
    }
    tela.restore()
}

private fun DrawScope.copito(mutada: Boolean = false) {
    val w = size.width
    val h = size.height
    val k = w * 0.46f / 48f
    val tinta = tom(Cafe.Espresso, mutada)
    val tela = drawContext.canvas
    tela.save()
    tela.translate((w - 48f * k) / 2f, (h - 48f * k) / 2f)
    tela.scale(k)
    val traço = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(COPIRO.vapor, tinta.copy(alpha = 0.75f), style = Stroke(width = 2.6f, cap = StrokeCap.Round))
    drawPath(COPIRO.pega, tinta, style = traço)
    drawPath(COPIRO.corpo, tom(Cafe.Amber, mutada))
    drawPath(COPIRO.corpo, tinta, style = traço)
    drawPath(COPIRO.pires, tinta, style = traço)
    tela.restore()
}

// ── figuras e copito em paths ───────────────────────────────────────────

// Uma camada: caminho, enchimento (null = só contorno) e grossura do vinco.
private class Camada(val trazo: Path, val cheio: Color?, val contorno: Float, val doNaipe: Boolean = false)

private class Figura(val camadas: List<Camada>, val olhos: List<Offset>, val raio: Float)

private val FIGURAS: Map<String, Figura> by lazy {
    mapOf("R" to rei(), "D" to dama(), "V" to valete())
}

// Rei: coroa de cinco bicos, ombros a encher o pé da carta.
private fun rei() = Figura(
    camadas = listOf(
        Camada(traço { move(11f, 31f); curva(9.6f, 19.4f, 15f, 14.4f, 22f, 14.4f); curva(29f, 14.4f, 34.4f, 19.4f, 33f, 31f); fecha() }, Cafe.Coffee, 1.7f),
        Camada(traço { move(8.5f, 19f); linha(11.6f, 8.4f); linha(16.8f, 13.6f); linha(22f, 5.5f); linha(27.2f, 13.6f); linha(32.4f, 8.4f); linha(35.5f, 19f); fecha() }, Cafe.Amber, 2f),
        Camada(traço { ovalo(13f, 19f, 31f, 37f) }, Cafe.Skin, 2f),
        Camada(traço { move(4.5f, 58f); curva(6.1f, 45.6f, 12.9f, 40.4f, 22f, 40.4f); curva(31.1f, 40.4f, 37.9f, 45.6f, 39.5f, 58f); fecha() }, null, 2f, doNaipe = true),
        Camada(traço { move(18f, 32.4f); curva(19.7f, 34.3f, 24.3f, 34.3f, 26f, 32.4f) }, null, 1.8f),
        Camada(traço { move(13.6f, 45.4f); curva(16.6f, 42.6f, 27.4f, 42.6f, 30.4f, 45.4f) }, null, 1.8f),
    ),
    olhos = listOf(Offset(18.6f, 26.8f), Offset(25.4f, 26.8f)),
    raio = 1.25f,
)

// Dama: cabelo a emoldurar a cara e coroa ondulada por cima.
private fun dama() = Figura(
    camadas = listOf(
        Camada(traço { move(9.5f, 36f); curva(6.9f, 22f, 11.9f, 12.6f, 22f, 12.6f); curva(32.1f, 12.6f, 37.1f, 22f, 34.5f, 36f); curva(33.1f, 27.6f, 30.1f, 23.6f, 22f, 23.6f); curva(13.9f, 23.6f, 10.9f, 27.6f, 9.5f, 36f); fecha() }, Cafe.Coffee, 1.7f),
        Camada(traço { move(12.6f, 16.8f); curva(14f, 13.2f, 16f, 11.2f, 17.2f, 13.4f); curva(18.4f, 15.6f, 19.6f, 10.4f, 22f, 10.4f); curva(24.4f, 10.4f, 25.6f, 15.6f, 26.8f, 13.4f); curva(28f, 11.2f, 30f, 13.2f, 31.4f, 16.8f); fecha() }, Cafe.Amber, 2f),
        Camada(traço { ovalo(13.4f, 19.4f, 30.6f, 36.6f) }, Cafe.Skin, 2f),
        Camada(traço { move(5.5f, 58f); curva(6.9f, 46.4f, 13.5f, 41.6f, 22f, 41.6f); curva(30.5f, 41.6f, 37.1f, 46.4f, 38.5f, 58f); fecha() }, null, 2f, doNaipe = true),
        Camada(traço { move(19f, 32f); curva(20.5f, 33.6f, 23.5f, 33.6f, 25f, 32f) }, null, 1.8f),
        Camada(traço { move(15.4f, 44.6f); curva(17.6f, 48f, 26.4f, 48f, 28.6f, 44.6f) }, null, 1.8f),
    ),
    olhos = listOf(Offset(18.8f, 27f), Offset(25.2f, 27f)),
    raio = 1.2f,
)

// Valete: touca redonda, pena de lado e a mecha por cima da testa.
private fun valete() = Figura(
    camadas = listOf(
        Camada(traço { move(10.5f, 21f); curva(10.5f, 12.4f, 15.5f, 7.4f, 22f, 7.4f); curva(28.5f, 7.4f, 33.5f, 12.4f, 33.5f, 21f); fecha() }, Cafe.Amber, 2f),
        Camada(traço { move(31f, 12.6f); curva(34.6f, 9.2f, 38.4f, 9.6f, 39.4f, 13.4f); curva(36f, 15f, 32.8f, 14f, 31f, 12.6f); fecha() }, null, 1.8f),
        Camada(traço { ovalo(13.2f, 19.6f, 30.8f, 37.2f) }, Cafe.Skin, 2f),
        Camada(traço { move(13.4f, 24.6f); curva(12.8f, 21.2f, 14.8f, 19f, 16.8f, 18.2f); curva(20.8f, 16.6f, 26.2f, 17.2f, 30.2f, 19.8f); curva(28.8f, 22.8f, 25.8f, 24.4f, 21.8f, 24.4f); curva(17.4f, 24.4f, 14.4f, 25.2f, 13.4f, 24.6f); fecha() }, Cafe.Coffee, 1.6f),
        Camada(traço { move(5f, 58f); curva(6.4f, 46.6f, 13f, 42f, 22f, 42f); curva(31f, 42f, 37.6f, 46.6f, 39f, 58f); fecha() }, null, 2f, doNaipe = true),
        Camada(traço { move(19f, 32.6f); curva(20.5f, 34.2f, 23.5f, 34.2f, 25f, 32.6f) }, null, 1.8f),
        Camada(traço { move(14f, 43.6f); curva(16.6f, 47.4f, 26.4f, 47.4f, 30f, 43.6f) }, null, 1.8f),
    ),
    olhos = listOf(Offset(18.8f, 27.6f), Offset(25.2f, 27.6f)),
    raio = 1.2f,
)

private class Copo(val corpo: Path, val pega: Path, val pires: Path, val vapor: Path)

private val COPIRO by lazy {
    Copo(
        corpo = traço {
            move(8f, 18f); linha(36f, 18f); linha(36f, 28f)
            curva(36f, 34.63f, 30.63f, 40f, 24f, 40f); linha(20f, 40f)
            curva(13.37f, 40f, 8f, 34.63f, 8f, 28f); fecha()
        },
        pega = traço {
            move(36f, 20f); linha(40f, 20f)
            curva(42.76f, 20f, 45f, 22.24f, 45f, 25f)
            curva(45f, 27.76f, 42.76f, 30f, 40f, 30f); linha(36f, 30f)
        },
        pires = traço { move(6f, 44f); linha(42f, 44f) },
        vapor = traço {
            move(18f, 14f); curva(14f, 10f, 22f, 8f, 18f, 4f)
            move(28f, 14f); curva(24f, 10f, 32f, 8f, 28f, 4f)
        },
    )
}

private fun DrawScope.figura(fig: Figura, cor: Color, mutada: Boolean) {
    val w = size.width
    val h = size.height
    val topo = h * 0.21f
    val fundo = h - w * 0.05f
    val k = min(w * 0.88f / 44f, (fundo - topo) / 58f)
    val tinta = tom(Cafe.Espresso, mutada)
    val tela = drawContext.canvas
    tela.save()
    tela.translate((w - 44f * k) / 2f, fundo - 58f * k)
    tela.scale(k)
    for (c in fig.camadas) {
        val cheio = if (c.doNaipe) cor else c.cheio?.let { tom(it, mutada) }
        if (cheio != null) drawPath(c.trazo, cheio, alpha = if (c.doNaipe) 0.92f else 1f)
        drawPath(c.trazo, tinta, style = Stroke(width = c.contorno, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    for (o in fig.olhos) drawCircle(tinta, fig.raio, o)
    tela.restore()
}

// O tamanho da letra em px da carta, entregue em sp físicos para não crescer
// com a fonte do sistema e continuar a caber na carta.
@Composable
private fun letra(px: Dp, peso: FontWeight = FontWeight.ExtraBold, cor: Color = Cafe.Espresso, display: Boolean = false): TextStyle {
    val corpo = with(LocalDensity.current) { px.toPx().toSp() }
    return remember(corpo, peso, cor, display) {
        if (display) CafeType.hero(10).copy(fontSize = corpo, color = cor)
        else CafeType.text(10, peso, cor).copy(fontSize = corpo)
    }
}

private class Escala(val largura: Dp) {
    private val k = largura / BASE.dp
    val altura = largura * RAZAO
    val raio = 11.dp * k
    val traço = 2.5.dp * k
    val sombra = 3.dp * k
}

// Caneta nas unidades do desenho: 44 × 58 nas figuras, 48 × 48 no copito.
private class Caneta(val p: Path) {
    fun move(x: Float, y: Float) = apply { p.moveTo(x, y) }
    fun linha(x: Float, y: Float) = apply { p.lineTo(x, y) }
    fun curva(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) =
        apply { p.cubicTo(c1x, c1y, c2x, c2y, x, y) }
    fun ovalo(l: Float, t: Float, r: Float, b: Float) = apply { p.addOval(Rect(l, t, r, b)) }
    fun fecha() = apply { p.close() }
}

private inline fun traço(block: Caneta.() -> Unit): Path = Path().apply { block(Caneta(this)) }
