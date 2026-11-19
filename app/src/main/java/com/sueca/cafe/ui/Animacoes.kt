// As animações da mesa: a carta que sai da cadeira e pousa na vaza, a mão que
// entra escalonada, o halo de quem joga e os grãos/confetis de festa.
package com.sueca.cafe.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private val Suavidade = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)

// O "bounce" do CSS (cubic-bezier .34,1.56,.64,1) rebenta com o resolvedor de
// curvas do Compose perto do fim da animação; a mola faz o mesmo sem isso.
private val Salto = spring<Float>(dampingRatio = 0.42f, stiffness = 900f, visibilityThreshold = 0.001f)
private val Entrada = spring<Float>(dampingRatio = 0.58f, stiffness = 620f, visibilityThreshold = 0.001f)

/** Para onde olha uma posição relativa (0 ao meu pé, 1 direita, 2 defronte, 3 esquerda). */
private fun vetorDaPos(pos: Int, alcance: Float) = when (pos) {
    0 -> Offset(0f, alcance)
    1 -> Offset(alcance, 0f)
    2 -> Offset(0f, -alcance)
    else -> Offset(-alcance, 0f)
}

/** A carta voa da cadeira até à casa da vaza; se for trunfo, cai em zoom. */
@Composable
fun VoaDaCadeira(
    pos: Int,
    chave: Any,
    trunfo: Boolean = false,
    alcance: Dp = 120.dp,
    content: @Composable () -> Unit,
) {
    val t = remember(chave) { Animatable(0f) }
    LaunchedEffect(chave) {
        t.animateTo(1f, if (trunfo) Salto else tween(420, easing = Suavidade))
    }
    val resto = 1f - t.value
    val vetor = vetorDaPos(pos, with(LocalDensity.current) { alcance.toPx() })
    Box(
        Modifier.graphicsLayer {
            translationX = vetor.x * resto
            translationY = vetor.y * resto
            alpha = if (trunfo) 0.35f + 0.65f * t.value else 0.9f + 0.1f * t.value
            val escala = if (trunfo) 2.4f - 1.4f * t.value else 0.9f + 0.1f * t.value
            scaleX = escala
            scaleY = escala
            rotationZ = -5f * resto
        },
    ) { content() }
}

/** A última vaza a ser recolhida: encolhe e vai ter com a cadeira de quem ganhou. */
@Composable
fun RecolheVaza(progresso: Float, pos: Int, content: @Composable () -> Unit) {
    val vetor = vetorDaPos(pos, with(LocalDensity.current) { 90.dp.toPx() })
    Box(
        Modifier.graphicsLayer {
            translationX = vetor.x * progresso
            translationY = vetor.y * progresso
            alpha = 1f - progresso
            val e = 1f - 0.7f * progresso
            scaleX = e
            scaleY = e
            rotationZ = (pos - 1.5f) * 4f * progresso
        },
    ) { content() }
}

/** Entrada da mão: cada carta sobe com um atraso escalonado. */
@Composable
fun EntraNaMao(indice: Int, chave: Any, content: @Composable () -> Unit) {
    val t = remember(chave) { Animatable(0f) }
    LaunchedEffect(chave) {
        val espera = minOf(indice * 30L, 300L)
        if (espera > 0) delay(espera)
        t.animateTo(1f, Entrada)
    }
    Box(
        Modifier.graphicsLayer {
            alpha = t.value
            translationY = 14.dp.toPx() * (1f - t.value)
            val e = 0.9f + 0.1f * t.value
            scaleX = e
            scaleY = e
            rotationZ = 6f * (1f - t.value)
        },
    ) { content() }
}

/** Halo âmbar a pulsar à volta da cadeira de quem joga. */
@Composable
fun HaloVez(): Float {
    val transicao = rememberInfiniteTransition(label = "halo")
    val v by transicao.animateFloat(
        0.45f, 0.95f,
        infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "halo_alpha",
    )
    return v
}

enum class TipoParticula { Graos, Confetes, Vapor }

/**
 * Camada de partículas de cada vez que `disparo` muda. As trajetórias saem do
 * índice da partícula, por isso não há listas mutáveis para manter.
 */
@Composable
fun Particulas(
    disparo: Long,
    modifier: Modifier = Modifier,
    tipo: TipoParticula = TipoParticula.Graos,
) {
    if (disparo == 0L) return
    val t = remember(disparo) { Animatable(0f) }
    LaunchedEffect(disparo) {
        t.animateTo(1f, tween(if (tipo == TipoParticula.Confetes) 2200 else 900, easing = LinearEasing))
    }
    val p = t.value
    if (p <= 0f || p >= 1f) return
    val cores = listOf(Cafe.Coffee, Cafe.WoodMid, Cafe.Amber, Cafe.Red, Cafe.Matcha, Cafe.Blue)
    val quantidade = when (tipo) {
        TipoParticula.Graos -> 12
        TipoParticula.Confetes -> 60
        TipoParticula.Vapor -> 10
    }
    val chuva = tipo == TipoParticula.Confetes
    Canvas(modifier.fillMaxSize()) {
        val centro = if (chuva) Offset(size.width / 2f, -20f) else Offset(size.width / 2f, size.height / 2f)
        for (i in 0 until quantidade) {
            val semente = (i.toUInt() * 2654435761u).toInt().toFloat()
            val positiva = kotlin.math.abs(semente)
            val angulo = positiva % 628f / 100f
            val forca = (60f + positiva % 140f) * if (tipo == TipoParticula.Vapor) 0.5f else 1f
            val espalhamento = if (chuva) (positiva % 200f - 100f) * 2.2f else 0f
            val x = centro.x + espalhamento + if (chuva) 0f else cos(angulo) * forca * p
            val subida = if (tipo == TipoParticula.Vapor) -0.4f else 0.4f
            val peso = if (chuva) size.height * 1.4f else 120f
            val y = centro.y + sin(angulo) * forca * p * subida + peso * p * p * 0.5f
            val cor = cores[i % cores.size]
            when (tipo) {
                TipoParticula.Graos -> drawCircle(cor.copy(alpha = 1f - p), 4.5f, Offset(x, y))
                TipoParticula.Confetes -> drawRect(
                    color = if (i % 2 == 0) cor else Cafe.Paper,
                    topLeft = Offset(x, y),
                    size = Size(7f, 7f),
                )
                TipoParticula.Vapor -> drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Cafe.Cream.copy(alpha = (1f - p) * 0.85f), Color.Transparent),
                        center = Offset(x, y), radius = 12f,
                    ),
                    radius = 12f,
                    center = Offset(x, y),
                )
            }
        }
    }
}

/** Um copito desenhado à mão, para cabeçalhos e botões. */
@Composable
fun Copito(tamanho: Dp = 22.dp, cor: Color = Cafe.Espresso, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(tamanho)) {
        val w = size.width
        val h = size.height
        val corpo = Path().apply {
            moveTo(w * 0.18f, h * 0.42f)
            lineTo(w * 0.18f, h * 0.78f)
            quadraticBezierTo(w * 0.18f, h * 0.92f, w * 0.34f, h * 0.92f)
            lineTo(w * 0.58f, h * 0.92f)
            quadraticBezierTo(w * 0.74f, h * 0.92f, w * 0.74f, h * 0.78f)
            lineTo(w * 0.74f, h * 0.42f)
            close()
        }
        drawPath(corpo, cor)
        drawArc(
            color = cor,
            startAngle = -70f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(w * 0.70f, h * 0.50f),
            size = Size(w * 0.22f, h * 0.26f),
            style = Stroke(width = 2.dp.toPx()),
        )
        drawRect(cor, Offset(w * 0.10f, h * 0.93f), Size(w * 0.72f, h * 0.07f))
    }
}
