// Peças de interface partilhadas: botões, campos, interruptores e etiquetas com
// o ar de cartaz de café (contorno grosso, sombra sólida, letras redondas).
package com.sueca.cafe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class CorBotao { Ambar, Azul, Verde, Papel, Perigo }

private fun corDe(c: CorBotao) = when (c) {
    CorBotao.Ambar -> Cafe.Amber
    CorBotao.Azul -> Cafe.Blue
    CorBotao.Verde -> Cafe.Matcha
    CorBotao.Perigo -> Cafe.Red
    CorBotao.Papel -> Cafe.Paper
}

@Composable
fun BotaoCafe(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cor: CorBotao = CorBotao.Ambar,
    grande: Boolean = false,
    activo: Boolean = true,
) {
    val enchimento = corDe(cor)
    val altura = if (grande) 58.dp else 46.dp
    Box(
        modifier = modifier
            .premivel(activo, onClick)
            .cartao(raio = 16.dp, cor = if (activo) enchimento else enchimento.copy(alpha = 0.45f), sombra = if (activo) 3.dp else 0.dp)
            .height(altura)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            texto,
            style = CafeType.text(if (grande) 19 else 16, FontWeight.Bold)
                .copy(color = if (activo) Cafe.Espresso else Cafe.Espresso.copy(alpha = 0.5f)),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun CampoTexto(
    valor: String,
    onValor: (String) -> Unit,
    rotulo: String? = null,
    dica: String = "",
    limite: Int = 24,
    modifier: Modifier = Modifier,
    corFundo: Color = Cafe.Paper,
    tamanhoLetra: Int = 18,
    alinharCentro: Boolean = false,
    teclaFeita: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (rotulo != null) {
            Text(rotulo, style = CafeType.text(12, FontWeight.Bold, Cafe.AmberDeep))
            Spacer(Modifier.height(4.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .cartao(raio = 14.dp, cor = corFundo, sombra = 2.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = valor,
                onValueChange = { onValor(it.take(limite)) },
                singleLine = true,
                textStyle = CafeType.text(tamanhoLetra, FontWeight.Bold).copy(
                    textAlign = if (alinharCentro) TextAlign.Center else TextAlign.Start,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { teclaFeita() }),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Cafe.AmberDeep),
                decorationBox = { campo ->
                    Box(if (alinharCentro) Modifier.fillMaxWidth() else Modifier, Alignment.CenterStart) {
                        if (valor.isEmpty() && dica.isNotEmpty()) {
                            Text(dica, style = CafeType.text(tamanhoLetra, FontWeight.Medium, Cafe.Espresso.copy(alpha = 0.35f)),
                                textAlign = if (alinharCentro) TextAlign.Center else TextAlign.Start)
                        }
                        campo()
                    }
                },
            )
        }
    }
}

@Composable
fun Interruptor(
    rotulo: String,
    legenda: String,
    ligado: Boolean,
    modifier: Modifier = Modifier,
    bloqueado: Boolean = false,
    onMudar: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier
            .premivel(!bloqueado) { onMudar(!ligado) }
            .fillMaxWidth()
            .cartao(raio = 14.dp, cor = if (ligado) Cafe.TeamABg else Cafe.Paper, sombra = 2.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(rotulo, style = CafeType.text(14, FontWeight.Bold))
            Text(legenda, style = CafeType.text(11, FontWeight.Medium, Cafe.Espresso.copy(alpha = 0.65f)))
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(width = 46.dp, height = 26.dp)
                .clip(RoundedCornerShape(50))
                .background(if (ligado) Cafe.Matcha else Cafe.Espresso.copy(alpha = 0.18f))
                .border(2.dp, Cafe.Espresso, RoundedCornerShape(50)),
            Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(2.dp)
                    .size(20.dp)
                    .align(if (ligado) Alignment.CenterEnd else Alignment.CenterStart)
                    .clip(RoundedCornerShape(50))
                    .background(Cafe.Paper)
                    .border(2.dp, Cafe.Espresso, RoundedCornerShape(50)),
            )
        }
    }
}

@Composable
fun Etiqueta(texto: String, cor: Color = Cafe.AmberDeep, modifier: Modifier = Modifier, tamanho: Int = 11) {
    Text(
        texto.uppercase(),
        modifier = modifier,
        style = CafeType.text(tamanho, FontWeight.Bold, cor),
    )
}

@Composable
fun Chip(texto: String, cor: Color, corTexto: Color = Cafe.Espresso, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(cor)
            .border(1.5.dp, Cafe.Espresso.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(texto, style = CafeType.text(10, FontWeight.Bold, corTexto))
    }
}

@Composable
fun Painel(
    modifier: Modifier = Modifier,
    cor: Color = Cafe.Paper,
    pincel: androidx.compose.ui.graphics.Brush? = null,
    conteudo: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .cartao(raio = 18.dp, cor = cor, pincel = pincel)
            .padding(14.dp),
        content = conteudo,
    )
}

@Composable
fun FaixaLigacao(estado: Ligacao, modifier: Modifier = Modifier) {
    val (texto, cor) = when (estado) {
        Ligacao.Nada -> "sem mesa" to Cafe.Espresso.copy(alpha = 0.3f)
        Ligacao.Ligando -> "a ligar…" to Cafe.Amber
        Ligacao.Ligado -> "ligado" to Cafe.Matcha
        Ligacao.Fraca -> "ligação fraca" to Cafe.Red
        Ligacao.SemServidor -> "sem servidor" to Cafe.Red
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Cafe.Espresso.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(cor))
        Text(texto, style = CafeType.text(11, FontWeight.Bold, Cafe.Espresso.copy(alpha = 0.75f)))
    }
}

/** Aviso instantâneo por baixo do topo, como o toast da app web. */
@Composable
fun Recado(texto: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .cartao(raio = 14.dp, cor = Cafe.Espresso, sombra = 3.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(texto, style = CafeType.text(13, FontWeight.SemiBold, Cafe.Cream), textAlign = TextAlign.Center)
    }
}

@Composable
fun TituloCafe(texto: String, tamanho: Int = 26, modifier: Modifier = Modifier, cor: Color = Cafe.Espresso) {
    Text(texto, modifier, style = CafeType.hero(tamanho).copy(color = cor, letterSpacing = 0.5.sp))
}

@Composable
fun Espaco(h: Dp) = Spacer(Modifier.height(h))
