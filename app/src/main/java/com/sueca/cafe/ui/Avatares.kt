// Os bonecos das cadeiras. Na app web eram SVG do DiceBear gerados a partir do
// nome; aqui são traços vectores nossos, desenhados no Canvas, com o mesmo token
// "d<grupo>:<hipótese>" a viajar no fio. Nem rede, nem assets, nem imagens.
package com.sueca.cafe.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ------------------------------------------------------------------ o token ---

private val PadraoToken = Regex("d([0-4]):([0-7])")

/**
 * A hipótese (0..7) guardada no token; qualquer coisa mal formada dá 0.
 * Os grupos 1..4 já não se usam mas continuam a ler-se, para um token antigo
 * que chegue do servidor não deixar a cadeira sem boneco.
 */
fun avatarVariant(token: String?): Int {
    val m = token?.let { PadraoToken.matchEntire(it) } ?: return 0
    return m.groupValues[2].toIntOrNull() ?: 0
}

/** O token canónico, tal como o servidor o guarda, para uma hipótese 0..7. */
fun avatarToken(variant: Int): String = "d0:${variant.coerceIn(0, 7)}"

// -------------------------------------------------------------- a interface ---

/** Um boneco de `size`, sempre o mesmo para o mesmo nome + hipótese. */
@Composable
fun Avatar(token: String?, name: String, size: Dp, modifier: Modifier = Modifier) {
    val v = avatarVariant(token)
    val semente = remember(name, v) { "${nomeLimpo(name)}·$v" }
    // A cara constrói-se uma vez por (hipótese, semente): o que se repete a cada
    // frame é só percorrer a lista e escalar, nunca refazer Pathes.
    val boneco = remember(v, semente) { construir(v, semente) }
    Canvas(modifier = modifier.size(size)) {
        val k = min(this.size.width, this.size.height) / 100f
        scale(k) {
            drawPath(Placa, boneco.fundo)
            clipPath(Placa) {
                val tracos = boneco.tracos
                for (i in tracos.indices) pinta(tracos[i])
            }
            // o aro fecha a placa por cima do corte
            drawPath(Placa, Cafe.Espresso, style = Tinta(5.2f))
        }
    }
}

/** A fila dos oito bonecos para o jogador escolher, com o escolhido destacado. */
@Composable
fun EscolhaAvatar(
    name: String,
    variant: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val es = nomeLimpo(name)
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(8) { i ->
            val escolhido = i == variant
            val escala by animateFloatAsState(if (escolhido) 1.14f else 1f, tween(170), label = "salta")
            val descricao = "boneco ${i + 1}"
            Box(
                modifier = Modifier.weight(1f).aspectRatio(1f)
                    .graphicsLayer { scaleX = escala; scaleY = escala },
            ) {
                // o enchimento dá ar ao cartao quando ele cresce para fora
                Box(
                    modifier = Modifier.fillMaxSize().padding(3.dp)
                        .cartao(raio = 12.dp, cor = Cafe.Paper, espessura = 2.dp)
                        .semantics { contentDescription = descricao }
                        .premivel { onPick(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Avatar(token = avatarToken(i), name = es, size = 30.dp)
                    if (escolhido) {
                        Box(
                            Modifier.matchParentSize()
                                .border(2.5.dp, Cafe.Amber, RoundedCornerShape(12.dp)),
                        )
                        Box(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp)
                                .size(15.dp)
                                .border(1.5.dp, Cafe.Espresso, RoundedCornerShape(8.dp))
                                .background(Cafe.Amber, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = "${i + 1}", style = CafeType.hero(10), color = Cafe.Paper, maxLines = 1)
                        }
                    } else {
                        Text(
                            text = "${i + 1}",
                            style = CafeType.text(9, FontWeight.Bold, Cafe.Espresso.copy(alpha = 0.4f)),
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

// --------------------------------------------------- a cara, em unidades 0..100 ---

private class Pincelada(
    val path: Path,
    val enchimento: Color? = null,
    val linha: Color? = Cafe.Espresso,
    val grossura: Float = 3f,
)

private class Boneco(val fundo: Color, val tracos: List<Pincelada>)

/** Os pentes vão para três camadas, porque a ordem de desenho não é a mesma. */
private class Pentes {
    val tras = mutableListOf<Pincelada>()   // atrás da cabeça: cabelo comprido
    val meio = mutableListOf<Pincelada>()   // barbas e bigodes, antes dos olhos
    val cima = mutableListOf<Pincelada>()   // cabelos e chapéus, por cima de tudo
}

/** A cabeça como óvalo, para os pentes colarem à silhueta seja qual for o tamanho. */
private class Cabeca(val cx: Float, val cy: Float, val rx: Float, val ry: Float) {
    val rect = Rect(cx - rx, cy - ry, cx + rx, cy + ry)
    val topo get() = cy - ry

    /** Ângulo na abóbada: 0 = direita, 90 = queixo, 180 = esquerda, 270 = alto. */
    fun ponto(g: Float): Offset {
        val a = g.toDouble() * PI / 180.0
        return Offset(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat())
    }

    /** Só a calote, de lado a lado pelo alto, um pouco mais alta que o crânio. */
    fun calote(sobe: Float = 0f): Path {
        val r = Rect(cx - rx, cy - ry - sobe, cx + rx, cy - ry + sobe)
        return Path().apply { arcTo(r, 180f, 180f, forceMoveTo = true) }
    }

    /**
     * A faixa entre o crânio e uma elipse concêntrica mais gorda: toucas e
     * bonés assim ficam colados à cabeça em vez de flotarem no ar.
     */
    fun manto(gorda: Float, de: Float = 330f, ate: Float = 210f): Path = Path().apply {
        arcTo(
            Rect(cx - rx - gorda * 0.2f, cy - ry - gorda, cx + rx + gorda * 0.2f, cy + ry + gorda),
            de, ate - de, forceMoveTo = true,
        )
        arcTo(rect, ate, de - ate, forceMoveTo = false)
        close()
    }

    /** O rebordo do manto, exatamente sobre a linha do cabelo. */
    fun rebordo(de: Float = 330f, ate: Float = 210f): Path =
        Path().apply { arcTo(rect, de, ate - de, forceMoveTo = true) }
}

private val Placa = circulo(50f, 50f, 48f)

// Os tons novos são misturas da paleta da casa, para não haver mais uma lista de cores.
private val Peles = listOf(
    Cafe.Skin,
    lerp(Cafe.Skin, Cafe.Cream, 0.45f),
    lerp(Cafe.Skin, Cafe.Coffee, 0.26f),
    lerp(Cafe.Skin, Cafe.Coffee, 0.52f),
)
private val Cabelos = listOf(
    Cafe.Espresso,
    Cafe.Coffee,
    lerp(Cafe.Espresso, Cafe.Cream, 0.52f),           // grafite
    lerp(Cafe.Red, Cafe.Coffee, 0.42f),               // ruivo
)
private val Detalhes = listOf(Cafe.Amber, Cafe.Blue, Cafe.Matcha, Cafe.Red)
private val Palha = lerp(Cafe.Amber, Cafe.Cream, 0.42f)

private fun nomeLimpo(name: String): String = name.trim().ifEmpty { "cafe" }

private fun construir(v: Int, semente: String): Boneco {
    val rnd = Baralho(fnv(semente))
    val pele = rnd.sorteia(Peles)
    val cabelo = rnd.sorteia(Cabelos)
    val detalhe = rnd.sorteia(Detalhes)
    val c = Cabeca(50f, 57f, rnd.float(21.5f, 25f), rnd.float(26f, 29f))

    val pentes = Pentes()
    when (v) {
        0 -> pentes.calvoBarbudo(c, pele, cabelo, rnd)
        1 -> pentes.arrepiado(c, cabelo, rnd)
        2 -> pentes.touca(c, cabelo, detalhe, rnd)
        3 -> pentes.chapeuPalha(c, detalhe)
        4 -> pentes.coque(c, cabelo, detalhe)
        5 -> pentes.compridoOndulado(c, cabelo)
        6 -> pentes.bone(c, detalhe, rnd)
        else -> pentes.cartolaBigodes(c, cabelo, rnd)
    }

    val tracos = ArrayList<Pincelada>(28)
    tracos += orelhas(c, pele)
    tracos += pentes.tras
    tracos += roupa(detalhe, pele, rnd)
    tracos += cabeca(c, pele, rnd)
    tracos += pentes.meio
    tracos += rosto(c, pele, cabelo, rnd)
    tracos += pentes.cima
    return Boneco(Cafe.AvatarBg[v], tracos)
}

private fun orelhas(c: Cabeca, pele: Color): List<Pincelada> = listOf(
    Pincelada(ovalo(c.cx - c.rx, c.cy + 3f, 6.5f, 7.5f), pele),
    Pincelada(ovalo(c.cx + c.rx, c.cy + 3f, 6.5f, 7.5f), pele),
)

private fun roupa(detalhe: Color, pele: Color, rnd: Baralho): List<Pincelada> {
    val escuro = lerp(detalhe, Cafe.Espresso, 0.28f)
    val out = ArrayList<Pincelada>(5)
    out += Pincelada(
        Path().apply {
            moveTo(43f, 72f); lineTo(43f, 92f); lineTo(57f, 92f); lineTo(57f, 72f); close()
        },
        lerp(pele, Cafe.Coffee, 0.16f),
    )
    out += Pincelada(
        Path().apply {
            moveTo(12f, 106f)
            cubicTo(16f, 89f, 31f, 84f, 41f, 84f)
            cubicTo(45f, 91f, 55f, 91f, 59f, 84f)
            cubicTo(69f, 84f, 84f, 89f, 88f, 106f)
            close()
        },
        detalhe,
    )
    val cava = rnd.float(89f, 92f)
    out += Pincelada(
        Path().apply {
            moveTo(38.5f, 86f); quadraticTo(50f, cava, 61.5f, 86f); quadraticTo(50f, cava - 4.5f, 38.5f, 86f); close()
        },
        escuro, grossura = 2.2f,
    )
    // um botão de casaquinho, a única coisa do peito que cabe dentro da placa
    out += Pincelada(circulo(50f, cava + 3.5f, 1.7f), escuro, linha = null)
    return out
}

private fun cabeca(c: Cabeca, pele: Color, rnd: Baralho): List<Pincelada> {
    val out = ArrayList<Pincelada>(4)
    out += Pincelada(ovalo(c.cx, c.cy, c.rx, c.ry), pele)
    out += Pincelada(c.calote(1.5f), linha = lerp(pele, Cafe.Paper, 0.5f), grossura = 2.2f)
    if (rnd.ate(3) == 0) {
        out += Pincelada(sardas(c), linha = lerp(pele, Cafe.Coffee, 0.55f), grossura = 1.6f)
    }
    if (rnd.ate(2) == 0) {
        // a sombra da bochecha, para a cara não ficar um disco plano
        out += Pincelada(
            Path().apply {
                arcTo(
                    Rect(c.cx - c.rx + 3f, c.cy - c.ry + 3f, c.cx + c.rx - 3f, c.cy + c.ry - 3f),
                    55f, 60f, forceMoveTo = true,
                )
            },
            linha = lerp(pele, Cafe.Coffee, 0.22f), grossura = 2.4f,
        )
    }
    return out
}

private fun sardas(c: Cabeca): Path = Path().apply {
    moveTo(c.cx - 13f, c.cy + 6f); lineTo(c.cx - 12.2f, c.cy + 6.3f)
    moveTo(c.cx - 15.5f, c.cy + 9f); lineTo(c.cx - 14.7f, c.cy + 9.3f)
    moveTo(c.cx + 12.2f, c.cy + 6.3f); lineTo(c.cx + 13f, c.cy + 6f)
    moveTo(c.cx + 14.7f, c.cy + 9.3f); lineTo(c.cx + 15.5f, c.cy + 9f)
}

private fun rosto(c: Cabeca, pele: Color, cabelo: Color, rnd: Baralho): List<Pincelada> {
    val out = ArrayList<Pincelada>(14)
    val olho = rnd.float(3.1f, 4.5f)
    val inclina = rnd.float(-0.5f, 0.5f)
    val ladoOlho = rnd.float(-1.4f, 1.4f)
    val riso = rnd.float(4f, 9.5f)
    val bocaAberta = rnd.ate(2) == 0
    val olhosClaro = rnd.ate(5) == 0
    val olx = c.rx * 0.42f
    val eyeY = c.cy - 2f
    val iris = if (olhosClaro) lerp(Cafe.Espresso, Cafe.Blue, 0.65f) else Cafe.Espresso

    if (rnd.ate(3) == 0) {
        val blush = Cafe.Red.copy(alpha = 0.2f)
        out += Pincelada(ovalo(c.cx - olx - 5.5f, eyeY + 8f, 4.4f, 3f), blush, linha = null)
        out += Pincelada(ovalo(c.cx + olx + 5.5f, eyeY + 8f, 4.4f, 3f), blush, linha = null)
    }
    listOf(-1f, 1f).forEach { lado ->
        val ex = c.cx + lado * olx
        out += Pincelada(ovalo(ex, eyeY, olho * 1.2f, olho), Cafe.Paper, grossura = 2.1f)
        out += Pincelada(
            circulo(ex + ladoOlho, eyeY + olho * 0.1f, olho * 0.54f), enchimento = iris, linha = null,
        )
        out += Pincelada(
            circulo(ex - olho * 0.22f + ladoOlho, eyeY - olho * 0.32f, olho * 0.18f),
            enchimento = Cafe.Paper, linha = null,
        )
        val by = c.cy - c.ry * 0.5f
        out += Pincelada(
            Path().apply {
                moveTo(ex - olho * 1.7f, by + inclina * 3.5f)
                quadraticTo(ex, by - olho * 0.8f, ex + olho * 1.7f, by - inclina * 3.5f)
            },
            linha = lerp(Cafe.Espresso, cabelo, 0.25f), grossura = rnd.float(2.4f, 3.5f),
        )
    }
    val nariz = rnd.float(4.5f, 7f)
    out += Pincelada(
        Path().apply {
            moveTo(50f, c.cy + 1f)
            quadraticTo(50f + nariz, c.cy + nariz, 49.2f, c.cy + nariz + 2.4f)
        },
        linha = lerp(pele, Cafe.Coffee, 0.45f), grossura = 2.4f,
    )
    val boca = c.cy + 13f
    if (bocaAberta) {
        out += Pincelada(
            Path().apply {
                moveTo(41.5f, boca)
                quadraticTo(50f, boca + riso, 58.5f, boca)
                close()
            },
            lerp(Cafe.Espresso, Cafe.Red, 0.25f), grossura = 2.6f,
        )
        out += Pincelada(
            ovalo(50f, boca + riso * 0.42f, 4.2f, riso * 0.28f),
            lerp(Cafe.Red, Cafe.Paper, 0.45f), linha = null,
        )
    } else {
        out += Pincelada(
            Path().apply {
                moveTo(41.5f, boca)
                quadraticTo(50f, boca + riso, 58.5f, boca)
            },
            linha = Cafe.Espresso, grossura = 3f,
        )
    }
    return out
}

// --------------------------------------------------------- as oito silhuetas ---

/** 0 — calvo com barba. */
private fun Pentes.calvoBarbudo(c: Cabeca, pele: Color, cabelo: Color, rnd: Baralho) {
    val esquerda = c.ponto(165f)
    val direita = c.ponto(15f)
    val barba = Path().apply {
        moveTo(esquerda.x, esquerda.y)
        quadraticTo(c.cx, c.cy - 2f, direita.x, direita.y)
        arcTo(c.rect, 15f, 150f, forceMoveTo = false)
        close()
    }
    meio += Pincelada(barba, cabelo)
    // as patilhas sobem até à orelha, senão a barba parece uma mancha
    meio += Pincelada(
        Path().apply {
            moveTo(esquerda.x, esquerda.y); lineTo(c.cx - c.rx + 1f, c.cy - 7f)
            moveTo(direita.x, direita.y); lineTo(c.cx + c.rx - 1f, c.cy - 7f)
        },
        linha = cabelo, grossura = 5f,
    )
    meio += bigode(lerp(cabelo, Cafe.Espresso, 0.3f), rnd.float(7.5f, 10f), rnd.float(0f, 2.5f), c.cy + 6f)
    cima += Pincelada(
        Path().apply {
            moveTo(40f, c.topo + 8f); quadraticTo(50f, c.topo + 3f, 60f, c.topo + 8f)
            quadraticTo(50f, c.topo + 6f, 40f, c.topo + 8f); close()
        },
        lerp(pele, Cafe.Paper, 0.45f), linha = null,
    )
}

/** 1 — cabelo arrepiado. */
private fun Pentes.arrepiado(c: Cabeca, cabelo: Color, rnd: Baralho) {
    val sobe = c.topo - 30f
    val pontas = floatArrayOf(
        25f, 45f, 29f, 20f, 34f, 34f, 40f, 12f, 45f, 31f,
        51f, 10f, 57f, 32f, 63f, 14f, 69f, 35f, 74f, 22f, 76f, 47f,
    )
    val folga = rnd.float(0f, 3f)
    val moita = Path().apply {
        moveTo(pontas[0], pontas[1] + sobe - folga)
        var i = 2
        while (i < pontas.size) {
            lineTo(pontas[i], pontas[i + 1] + sobe - folga)
            i += 2
        }
        arcTo(c.rect, -26f, -128f, forceMoveTo = false)
        close()
    }
    cima += Pincelada(moita, cabelo)
    cima += Pincelada(c.calote(3f), linha = lerp(cabelo, Cafe.Paper, 0.3f), grossura = 2f)
}

/** 2 — touca de lã com pompom. */
private fun Pentes.touca(c: Cabeca, cabelo: Color, detalhe: Color, rnd: Baralho) {
    val t = c.topo
    cima += Pincelada(c.manto(14f), detalhe)
    // a dobra virada, mais escura, a correr-lhe por baixo
    cima += Pincelada(c.rebordo(), linha = lerp(detalhe, Cafe.Espresso, 0.3f), grossura = 7f)
    // as costuras da malha
    cima += Pincelada(
        Path().apply {
            moveTo(40f, t - 9f); quadraticTo(36f, t - 1f, 35f, t + 6f)
            moveTo(60f, t - 9f); quadraticTo(64f, t - 1f, 65f, t + 6f)
        },
        linha = lerp(detalhe, Cafe.Espresso, 0.22f), grossura = 1.8f,
    )
    cima += Pincelada(circulo(50f, t - 15f, rnd.float(4.5f, 6.5f)), Cafe.Paper)
    // as madeixas que escapam por baixo da dobra
    cima += Pincelada(
        Path().apply {
            moveTo(c.cx - c.rx + 2f, c.cy - 9f); quadraticTo(c.cx - c.rx + 6f, c.cy - 4f, c.cx - c.rx + 3f, c.cy + 2f)
            moveTo(c.cx + c.rx - 2f, c.cy - 9f); quadraticTo(c.cx + c.rx - 6f, c.cy - 4f, c.cx + c.rx - 3f, c.cy + 2f)
        },
        linha = cabelo, grossura = 4f,
    )
}

/** 3 — chapéu de palha de aba larga. */
private fun Pentes.chapeuPalha(c: Cabeca, detalhe: Color) {
    val t = c.topo + 2f
    cima += Pincelada(
        Path().apply {
            moveTo(32f, t); lineTo(34.5f, t - 19f)
            quadraticTo(50f, t - 25f, 65.5f, t - 19f); lineTo(68f, t); close()
        },
        Palha,
    )
    cima += Pincelada(
        Path().apply {
            moveTo(33.2f, t - 7f); quadraticTo(50f, t - 12f, 66.8f, t - 7f)
            lineTo(67.6f, t - 1f); quadraticTo(50f, t - 6f, 32.4f, t - 1f); close()
        },
        detalhe, grossura = 2.2f,
    )
    cima += Pincelada(ovalo(50f, t, 37f, 8.5f), Palha)
    cima += Pincelada(
        Path().apply {
            moveTo(20f, t + 2f); quadraticTo(50f, t + 8f, 80f, t + 2f)
        },
        linha = lerp(Palha, Cafe.Coffee, 0.3f), grossura = 1.6f,
    )
}

/** 4 — coque. */
private fun Pentes.coque(c: Cabeca, cabelo: Color, detalhe: Color) {
    val t = c.topo
    val franja = Path().apply {
        moveTo(c.cx - c.rx, c.cy - 2f)
        cubicTo(c.cx - c.rx - 1f, t - 6f, c.cx + c.rx + 1f, t - 6f, c.cx + c.rx, c.cy - 2f)
        quadraticTo(c.cx + 8f, t + 8f, c.cx - 2f, t + 12f)
        quadraticTo(c.cx - 12f, t + 15f, c.cx - c.rx, c.cy - 2f)
        close()
    }
    cima += Pincelada(circulo(50f, t - 10f, 8.6f), cabelo)
    cima += Pincelada(ovalo(50f, t - 1.5f, 8f, 3.4f), detalhe, grossura = 2.2f)
    cima += Pincelada(franja, cabelo)
    cima += Pincelada(
        Path().apply {
            moveTo(38f, t + 5f); quadraticTo(50f, t + 11f, 62f, t + 4f)
        },
        linha = lerp(cabelo, Cafe.Paper, 0.3f), grossura = 1.8f,
    )
}

/** 5 — cabelo comprido ondulado. */
private fun Pentes.compridoOndulado(c: Cabeca, cabelo: Color) {
    val t = c.topo
    val longo = Path().apply {
        moveTo(26f, 44f)
        cubicTo(21f, 57f, 30f, 65f, 23f, 77f)
        cubicTo(17f, 87f, 26f, 95f, 21f, 106f)
        lineTo(79f, 106f)
        cubicTo(74f, 95f, 83f, 87f, 77f, 77f)
        cubicTo(70f, 65f, 79f, 57f, 74f, 44f)
        cubicTo(70f, t - 20f, 30f, t - 20f, 26f, 44f)
        close()
    }
    val franja = Path().apply {
        moveTo(c.cx - c.rx, c.cy - 3f)
        cubicTo(c.cx - c.rx - 2f, t - 8f, c.cx + c.rx + 2f, t - 8f, c.cx + c.rx, c.cy - 3f)
        quadraticTo(c.cx + c.rx - 6f, t + 10f, c.cx + 3f, t + 8f)
        quadraticTo(c.cx - 9f, t + 6f, c.cx - c.rx + 5f, t + 13f)
        quadraticTo(c.cx - c.rx + 1f, t + 4f, c.cx - c.rx, c.cy - 3f)
        close()
    }
    tras += Pincelada(longo, cabelo)
    cima += Pincelada(franja, cabelo)
    cima += Pincelada(
        Path().apply {
            moveTo(21f, 60f); quadraticTo(27f, 73f, 21f, 86f); quadraticTo(26f, 96f, 21f, 104f)
        },
        linha = lerp(cabelo, Cafe.Paper, 0.3f), grossura = 1.8f,
    )
}

/** 6 — boné com pala para o lado. */
private fun Pentes.bone(c: Cabeca, detalhe: Color, rnd: Baralho) {
    val dir = if (rnd.ate(2) == 0) 1f else -1f
    val t = c.topo
    val y = c.cy - 12f                      // a testeira, a meio da testa
    val pala = Path().apply {
        moveTo(50f - dir * 15f, y - 3f)
        cubicTo(50f - dir * 2f, y - 11f, 50f + dir * 14f, y - 12f, 50f + dir * 26f, y - 5f)
        cubicTo(50f + dir * 18f, y + 2f, 50f + dir * 2f, y + 3f, 50f - dir * 15f, y)
        close()
    }
    cima += Pincelada(c.manto(10f), detalhe)
    cima += Pincelada(c.rebordo(), linha = lerp(detalhe, Cafe.Espresso, 0.3f), grossura = 5f)
    cima += Pincelada(pala, lerp(detalhe, Cafe.Espresso, 0.26f))
    cima += Pincelada(circulo(50f, t - 6f, 2.4f), lerp(detalhe, Cafe.Espresso, 0.4f), grossura = 2f)
    if (rnd.ate(2) == 0) {
        cima += Pincelada(
            Path().apply { moveTo(43f, t - 5f); quadraticTo(50f, t - 9f, 57f, t - 5f) },
            linha = lerp(detalhe, Cafe.Paper, 0.35f), grossura = 1.6f,
        )
    }
}

/** 7 — bigodes e cartola. */
private fun Pentes.cartolaBigodes(c: Cabeca, cabelo: Color, rnd: Baralho) {
    val t = c.topo
    val chapeu = lerp(Cafe.Espresso, Cafe.Coffee, 0.45f)
    meio += bigode(cabelo, rnd.float(9f, 11.5f), rnd.float(0f, 2f), c.cy + 6f)
    cima += Pincelada(
        Path().apply {
            moveTo(35f, t + 2f); lineTo(37.5f, t - 20f)
            lineTo(62.5f, t - 20f); lineTo(65f, t + 2f); close()
        },
        chapeu,
    )
    cima += Pincelada(
        Path().apply {
            moveTo(35.6f, t - 8f); lineTo(64.4f, t - 8f); lineTo(65f, t + 2f); lineTo(35f, t + 2f); close()
        },
        Cafe.Red, grossura = 2.2f,
    )
    cima += Pincelada(ovalo(50f, t + 2f, 30f, 6.5f), chapeu)
    cima += Pincelada(
        Path().apply { moveTo(41f, t - 17f); lineTo(57f, t - 17f) },
        linha = Cafe.Paper.copy(alpha = 0.3f), grossura = 2f,
    )
}

/** Um par de bigodes simétricos a partir do centro, ao nível `y`. */
private fun bigode(cor: Color, largo: Float, sobe: Float, y: Float): Pincelada {
    val fora = Path().apply {
        moveTo(50f, y)
        cubicTo(50f - largo * 0.5f, y - 3f - sobe, 50f - largo, y - 2f - sobe, 50f - largo - 1.5f, y + 3f + sobe)
        cubicTo(50f - largo * 0.7f, y + 3f + sobe, 50f - largo * 0.4f, y + 4f, 50f, y + 4.5f)
        close()
        moveTo(50f, y)
        cubicTo(50f + largo * 0.5f, y - 3f - sobe, 50f + largo, y - 2f - sobe, 50f + largo + 1.5f, y + 3f + sobe)
        cubicTo(50f + largo * 0.7f, y + 3f + sobe, 50f + largo * 0.4f, y + 4f, 50f, y + 4.5f)
        close()
    }
    return Pincelada(fora, cor, linha = null)
}

// ------------------------------------------------------------------- a sorte ---

/**
 * FNV-1a de 32 bits com avalanche de murmur no fim. O `hashCode` do Java só
 * encadeia factores de 31, por isso "Ana" e "Ano" mudam dois bits e sai quase
 * o mesmo boneco; aqui cada byte mexe em todos os bits e a cara fica estável
 * entre versões e aparelhos — nem `Random(seed)` nem `hashCode()` no desenho.
 */
private fun fnv(texto: String): Int {
    var h = -2128831035                            // 2166136261: offset basis do FNV
    for (b in texto.encodeToByteArray()) h = (h xor (b.toInt() and 0xFF)) * 16777619
    h = (h xor (h ushr 13)) * -2048144789          // 0x85ebca6b
    h = (h xor (h ushr 15)) * -1028477387          // 0xc2b2ae35
    return h xor (h ushr 16)
}

/** xorshift32: um pedido, uma volta do estado. Baralha sem depender do sistema. */
private class Baralho(private var estado: Int) {
    init {
        if (estado == 0) estado = -1640531527      // 0x9e3779b9 — nunca a zero
    }

    private fun u32(): Int {
        estado = estado xor (estado shl 13)
        estado = estado xor (estado ushr 17)
        estado = estado xor (estado shl 5)
        return estado
    }

    fun ate(top: Int): Int = if (top <= 0) 0 else ((u32().toLong() and 0x7FFFFFFFL) % top).toInt()

    fun float(de: Float, ate: Float): Float {
        val f = (u32().toLong() and 0xFFFFFFL) / 16777215f
        return de + (ate - de) * f
    }

    fun <T> sorteia(opcoes: List<T>): T = opcoes[ate(opcoes.size)]
}

// ---------------------------------------------------------------- os traços ---

private fun DrawScope.pinta(p: Pincelada) {
    if (p.enchimento != null) drawPath(p.path, p.enchimento)
    if (p.linha != null) drawPath(p.path, p.linha, style = Tinta(p.grossura))
}

private fun Tinta(grossura: Float) =
    Stroke(width = grossura, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun ovalo(cx: Float, cy: Float, rx: Float, ry: Float): Path =
    Path().apply { addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry)) }

private fun circulo(cx: Float, cy: Float, r: Float): Path =
    Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }
