// As cores, letras e medidas do café: é a tradução do style.css/sueca.css da app
// web para Compose — mesmo espresso, mesmo creme, mesmo feltro e madeira.
package com.sueca.cafe.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sueca.cafe.R

object Cafe {
    val Espresso = Color(0xFF2A1710)     // tinta e cantos
    val Coffee = Color(0xFF4A2C1A)       // madeira escura
    val Cream = Color(0xFFF6EAD2)        // fundo do ecrã
    val Paper = Color(0xFFFFF8EA)        // painéis e cadeiras
    val Amber = Color(0xFFF0A63C)        // equipa Bica
    val AmberDeep = Color(0xFFD98A1F)
    val Red = Color(0xFFE2503C)          // copas e ouros
    val Matcha = Color(0xFF7FB069)       // feltro
    val MatchaDeep = Color(0xFF5D9147)
    val FeltLight = Color(0xFF93C17C)
    val Blue = Color(0xFF5B8BD9)         // equipa Galão
    val BlueDeep = Color(0xFF3F6CB8)
    val Grape = Color(0xFF9B6DD6)        // automático / saiu
    val CardFace = Color(0xFFFFFDF6)
    val TrumpFace = Color(0xFFFFF3D8)
    val Skin = Color(0xFFFFE3C2)
    val WoodLight = Color(0xFF8A5A34)
    val WoodMid = Color(0xFF6B4226)
    val WoodEdge = Color(0xFF7C4F2C)
    val FeltText = Color(0xFF1F3A18)
    val TeamABg = Color(0xFFFFE9C6)
    val TeamBBg = Color(0xFFDFEAFF)

    val Wood = Brush.linearGradient(listOf(WoodLight, WoodMid, WoodEdge))

    fun felt(w: Float, h: Float) = Brush.radialGradient(
        colors = listOf(FeltLight, Matcha, MatchaDeep),
        center = Offset(w * 0.5f, h * 0.42f),
        radius = maxOf(w, h) * 0.8f,
    )

    fun team(team: String) = if (team == "b") Blue else Amber
    fun teamDeep(team: String) = if (team == "b") BlueDeep else AmberDeep
    fun teamBg(team: String) = if (team == "b") TeamBBg else TeamABg

    // Os oito fundos dos bonecos, pela ordem do token d0:0 … d0:7.
    val AvatarBg = listOf(
        Color(0xFFFFE9C6), Color(0xFFDFEAFF), Color(0xFFE4F2D8), Color(0xFFFFE3DE),
        Color(0xFFECE4FF), Color(0xFFD9F2EF), Color(0xFFFFD9EA), Color(0xFFF5E7CF),
    )
}

private val Baloo = FontFamily(
    Font(R.font.baloo_400, FontWeight.Normal),
    Font(R.font.baloo_600, FontWeight.Medium),
    Font(R.font.baloo_600, FontWeight.SemiBold),
    Font(R.font.baloo_800, FontWeight.Bold),
    Font(R.font.baloo_800, FontWeight.ExtraBold),
)

private val Display = FontFamily(Font(R.font.luckiest_guy))

object CafeType {
    fun hero(size: Int) = TextStyle(fontFamily = Display, fontSize = size.sp, color = Cafe.Espresso)
    fun title(size: Int) = TextStyle(fontFamily = Display, fontSize = size.sp, color = Cafe.Espresso)
    fun text(
        size: Int,
        weight: FontWeight = FontWeight.SemiBold,
        color: Color = Cafe.Espresso,
    ) = TextStyle(fontFamily = Baloo, fontSize = size.sp, fontWeight = weight, color = color)
}

@Composable
fun SuecaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cafe.Amber,
            onPrimary = Cafe.Espresso,
            secondary = Cafe.Blue,
            background = Cafe.Cream,
            surface = Cafe.Paper,
            onSurface = Cafe.Espresso,
            error = Cafe.Red,
        ),
        typography = Typography(),
        content = content,
    )
}

/**
 * Cartão com ar de desenho animado: contorno grosso e sombra sólida deslocada.
 * A sombra fica dentro do próprio nó, por isso o conteúdo desloca-se para o
 * canto superior esquerdo e o lado direito/base ganha esse ar de relevo.
 */
fun Modifier.cartao(
    raio: Dp = 18.dp,
    cor: Color = Cafe.Paper,
    pincel: Brush? = null,
    espessura: Dp = 2.5.dp,
    sombra: Dp = 3.dp,
): Modifier {
    val shape = RoundedCornerShape(raio)
    return this
        .padding(end = sombra, bottom = sombra)
        .drawBehind {
            val r = CornerRadius(raio.toPx(), raio.toPx())
            drawRoundRect(
                color = Cafe.Espresso,
                topLeft = Offset(sombra.toPx(), sombra.toPx()),
                size = Size(size.width, size.height),
                cornerRadius = r,
            )
            if (pincel != null) {
                drawRoundRect(brush = pincel, size = Size(size.width, size.height), cornerRadius = r)
            } else {
                drawRoundRect(color = cor, size = Size(size.width, size.height), cornerRadius = r)
            }
        }
        .clip(shape)
        .border(espessura, Cafe.Espresso, shape)
}

/** O creme pontilhado do ecrã inicial. */
fun Modifier.fundoCafe(pontos: Boolean = true): Modifier = this.drawBehind {
    drawRect(Cafe.Cream)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Cafe.Amber.copy(alpha = 0.5f), Color.Transparent),
            center = Offset(size.width * 0.1f, 0f), radius = size.height * 0.55f,
        ),
        radius = size.height * 0.55f, center = Offset(size.width * 0.1f, 0f),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Cafe.Red.copy(alpha = 0.22f), Color.Transparent),
            center = Offset(size.width, size.height), radius = size.height * 0.5f,
        ),
        radius = size.height * 0.5f, center = Offset(size.width, size.height),
    )
    if (!pontos) return@drawBehind
    val passo = 22.dp.toPx()
    val raio = 1.6.dp.toPx()
    var y = passo / 2
    while (y < size.height) {
        var x = passo / 2
        while (x < size.width) {
            drawCircle(Cafe.Espresso.copy(alpha = 0.10f), raio, Offset(x, y))
            x += passo
        }
        y += passo
    }
}

/** Ao tocar, afunda e perde a sombra, como o `:active` do CSS. */
@Composable
fun Modifier.premivel(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val amount = animateDpAsState(
        if (pressed && enabled) 3.dp else 0.dp,
        tween(90),
        label = "afunda",
    )
    return this
        .offset(x = amount.value, y = amount.value)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/** Uma mecha de vapor a subir do título, desenhada em vetor. */
@Composable
fun VaporIcon(size: Dp = 22.dp, tint: Color = Cafe.Espresso) {
    Canvas(Modifier.size(size)) {
        val linha = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        val alto = this.size.height
        val largo = this.size.width
        val onda = Path().apply {
            moveTo(largo * 0.30f, alto * 0.92f)
            cubicTo(largo * 0.06f, alto * 0.68f, largo * 0.54f, alto * 0.44f, largo * 0.30f, alto * 0.12f)
        }
        drawPath(onda, tint, style = linha)
        drawPath(
            Path().apply {
                moveTo(largo * 0.68f, alto * 0.92f)
                cubicTo(largo * 0.44f, alto * 0.66f, largo * 0.92f, alto * 0.46f, largo * 0.68f, alto * 0.20f)
            },
            tint.copy(alpha = 0.55f), style = linha,
        )
    }
}
