// A mesa vista por um jogador: marcador, pano quadrado com as quatro cadeiras,
// a cruz da vaza, a carta virada, a mão própria, o registo de vazas e os emotes.
package com.sueca.cafe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sueca.cafe.RANK_INFO
import com.sueca.cafe.SUIT_INFO
import com.sueca.cafe.TEAMS
import com.sueca.cafe.net.Colocada
import com.sueca.cafe.net.Mao
import com.sueca.cafe.net.Vista
import com.sueca.cafe.rankOf
import com.sueca.cafe.sortHand
import com.sueca.cafe.suitOf

val EMOTES = listOf(
    "☕" to "Café", "👏" to "Palmas", "😅" to "Desculpa",
    "🔥" to "Grande jogada", "😱" to "Ai, não", "🤝" to "Boa parceria",
)

/** 0 = a meu pé, 1 = à direita, 2 = defronte, 3 = à esquerda. */
private fun posDe(seat: Int, meuLugar: Int) = (seat - meuLugar + 4) % 4

private data class Casa(val alinhamento: Alignment)

// Cada carta da vaza fica a 40% do caminho entre o centro do pano e a beira:
// a cruz abre-se com o tapete, por isso nunca encosta nas cadeiras seja qual
// for o ecrã.
private fun casaDaPos(pos: Int) = when (pos) {
    0 -> Casa(BiasAlignment(0f, 0.40f))
    1 -> Casa(BiasAlignment(0.40f, 0f))
    2 -> Casa(BiasAlignment(0f, -0.40f))
    else -> Casa(BiasAlignment(-0.40f, 0f))
}

@Composable
fun Mesa(vista: Vista, sessao: Sessao, abrirRegras: () -> Unit, modifier: Modifier = Modifier) {
    val g = vista.mao
    var verTodas by remember { mutableStateOf(false) }
    var emote by remember { mutableStateOf<Pair<String, Int>?>(null) }

    LaunchedEffect(sessao.feed) {
        val ultimo = sessao.feed.value.lastOrNull { it.kind == "emote" } ?: return@LaunchedEffect
        emote = ultimo.text to (ultimo.seat ?: vista.seat)
    }
    LaunchedEffect(emote) {
        if (emote != null) {
            kotlinx.coroutines.delay(1700)
            emote = null
        }
    }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Marcador(vista)
        Espaco(8.dp)
        if (g == null) {
            Box(Modifier.fillMaxWidth().height(240.dp), Alignment.Center) {
                Text(
                    "À espera da mesa…",
                    style = CafeType.text(15, FontWeight.Bold, Cafe.Espresso.copy(alpha = 0.55f)),
                )
            }
        } else {
            Pano(vista, g, emote)
            Espaco(8.dp)
            Banner(g, vista)
            Espaco(6.dp)
            MaoPropria(vista, g, jogar = sessao::jogar)
            Espaco(8.dp)
            RegistoVazas(g, verTodas) { verTodas = it }
            Espaco(8.dp)
            AccoesMesa(vista, g, sessao, abrirRegras)
        }
    }
}

// ------------------------------------------------------------------- marcador

@Composable
private fun Marcador(vista: Vista) {
    val minha = vista.me?.team ?: "a"
    val contraria = if (minha == "a") "b" else "a"
    val alvo = vista.rules.jogosParaGanhar
    Row(
        Modifier
            .fillMaxWidth()
            .cartao(raio = 14.dp, cor = Cafe.Paper, sombra = 2.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LadoEquipa(vista, minha, alvo, Modifier.weight(1f), direita = false)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp)) {
            Text("MÃO ${vista.handNo}", style = CafeType.text(12, FontWeight.ExtraBold, Cafe.AmberDeep))
            Text(
                "a $alvo jogos" + if (vista.rules.bandeiraFecha) " · bandeira" else "",
                style = CafeType.text(9, FontWeight.SemiBold, Cafe.Espresso.copy(alpha = 0.6f)),
            )
        }
        LadoEquipa(vista, contraria, alvo, Modifier.weight(1f), direita = true)
    }
}

@Composable
private fun LadoEquipa(vista: Vista, team: String, alvo: Int, modifier: Modifier, direita: Boolean) {
    val pontos = vista.mao?.let { if (team == "a") it.pointsA else it.pointsB } ?: 0
    val jogos = if (team == "a") vista.gamesA else vista.gamesB
    Column(modifier, horizontalAlignment = if (direita) Alignment.End else Alignment.Start) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (direita) Arrangement.End else Arrangement.Start,
        ) {
            if (!direita) PontoEquipa(team)
            Spacer(Modifier.width(5.dp))
            Text(
                TEAMS.getValue(team).name,
                style = CafeType.text(11, FontWeight.Bold, Cafe.teamDeep(team)),
                textAlign = if (direita) TextAlign.End else TextAlign.Start,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(5.dp))
            Palitos(jogos, alvo, Cafe.team(team))
            if (direita) PontoEquipa(team)
        }
        Text("$pontos", style = CafeType.hero(19))
    }
}

@Composable
private fun PontoEquipa(team: String) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(Cafe.team(team)))
}

@Composable
private fun Palitos(n: Int, alvo: Int, cor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0 until minOf(alvo, 6)) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i < n) cor else Cafe.Espresso.copy(alpha = 0.14f)),
            )
        }
    }
}

// ----------------------------------------------------------------------- pano

@Composable
private fun Pano(vista: Vista, mao: Mao, emote: Pair<String, Int>?) {
    val meuLugar = vista.seat
    var festa by remember { mutableLongStateOf(0L) }
    var vapor by remember { mutableLongStateOf(0L) }
    var recolha by remember { mutableStateOf<Pair<List<Colocada>, Int>?>(null) }
    val vazas = mao.history.size

    LaunchedEffect(vazas) {
        if (vazas > 0) festa = System.currentTimeMillis()
    }
    // quando a vaza fecha, o servidor já a tirou de cima: guardamos o eco para a
    // animação de recolha
    LaunchedEffect(mao.trick.size) {
        if (mao.trick.isEmpty() && mao.lastTrick != null && vazas > 0) {
            recolha = mao.lastTrick.plays to mao.lastTrick.winner
        }
    }
    LaunchedEffect(recolha) {
        if (recolha != null) {
            kotlinx.coroutines.delay(520)
            recolha = null
        }
    }
    LaunchedEffect(mao.trick.size) {
        val ultimo = mao.trick.lastOrNull()
        if (ultimo != null && suitOf(ultimo.card) == mao.trump) vapor = System.currentTimeMillis()
    }

    Box(
        Modifier
            .fillMaxWidth()
            .cartao(raio = 18.dp, pincel = Cafe.Wood, sombra = 4.dp)
            .padding(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(13.dp))
                .drawBehind { drawRect(brush = Cafe.felt(size.width, size.height)) }
                .border(2.dp, Cafe.Espresso.copy(alpha = 0.32f), RoundedCornerShape(13.dp))
                .drawBehind {
                    // marcas de copo: argolas, não discos
                    val tinta = Cafe.Coffee.copy(alpha = 0.09f)
                    val mancha = 34.dp.toPx()
                    drawCircle(tinta, mancha, Offset(size.width * 0.18f, size.height * 0.16f), style = Stroke(5.dp.toPx()))
                    drawCircle(tinta, mancha * 0.7f, Offset(size.width * 0.84f, size.height * 0.88f), style = Stroke(4.dp.toPx()))
                },
        ) {
            for (pos in 0..3) {
                val seat = (0..3).first { posDe(it, meuLugar) == pos }
                Cadeira(vista, mao, seat, pos, modifier = Modifier.align(alinhamentoCadeira(pos)))
            }

            if (mao.turnedLive && mao.turned != null) {
                val pos = posDe(mao.turnedSeat ?: mao.dealer, meuLugar)
                Box(Modifier.align(alinhamentoVirada(pos))) {
                    VoaDaCadeira(pos, mao.turned!!, trunfo = true, alcance = 60.dp) {
                        Carta(card = mao.turned!!, width = 34.dp, trump = true)
                    }
                }
            }

            if (recolha == null) {
                mao.trick.forEach { colocada ->
                    val pos = posDe(colocada.seat, meuLugar)
                    val casa = casaDaPos(pos)
                    Box(Modifier.align(casa.alinhamento)) {
                        key(colocada.card) {
                            VoaDaCadeira(pos, colocada.card, trunfo = suitOf(colocada.card) == mao.trump) {
                                Carta(card = colocada.card, width = 46.dp, trump = suitOf(colocada.card) == mao.trump)
                            }
                        }
                    }
                }
            } else {
                val (cartas, vencedor) = recolha!!
                val progresso = remember(cartas) { androidx.compose.animation.core.Animatable(0f) }
                LaunchedEffect(cartas) { progresso.animateTo(1f, androidx.compose.animation.core.tween(480)) }
                val casaVencedor = casaDaPos(posDe(vencedor, meuLugar))
                Box(Modifier.fillMaxSize(), casaVencedor.alinhamento) {
                    cartas.forEach { colocada ->
                        val desvio = casaDaPos(posDe(colocada.seat, meuLugar))
                        Box(Modifier.align(desvio.alinhamento)) {
                            RecolheVaza(progresso.value, posDe(colocada.seat, meuLugar)) {
                                Carta(card = colocada.card, width = 46.dp, trump = suitOf(colocada.card) == mao.trump)
                            }
                        }
                    }
                }
            }

            if (mao.trick.isEmpty() && recolha == null) TextoCentro(mao, vista)

            emote?.let {
                val pos = posDe(it.second, meuLugar)
                EmoteFlutuante(it.first, pos, casaDaPos(pos).alinhamento)
            }

            Particulas(festa, Modifier.align(Alignment.Center), TipoParticula.Graos)
            Particulas(vapor, Modifier.align(Alignment.Center), TipoParticula.Vapor)
        }
    }
}

private fun alinhamentoCadeira(pos: Int) = when (pos) {
    0 -> Alignment.BottomCenter
    1 -> Alignment.CenterEnd
    2 -> Alignment.TopCenter
    else -> Alignment.CenterStart
}

private fun alinhamentoVirada(pos: Int) = when (pos) {
    0 -> BiasAlignment(-0.52f, 0.66f)
    1 -> BiasAlignment(0.66f, -0.52f)
    2 -> BiasAlignment(0.52f, -0.66f)
    else -> BiasAlignment(-0.66f, 0.52f)
}

@Composable
private fun BoxScope.TextoCentro(mao: Mao, vista: Vista) {
    val texto = when {
        mao.over -> "fim da mão"
        mao.yourTurn -> "joga tu"
        else -> "${vista.player(mao.turn)?.name ?: "A mesa"} a pensar…"
    }
    Box(Modifier.fillMaxSize().padding(30.dp), Alignment.Center) {
        Text(
            texto,
            style = CafeType.text(12, FontWeight.Bold, Cafe.FeltText.copy(alpha = 0.8f)),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Cadeira(vista: Vista, mao: Mao, seat: Int, pos: Int, modifier: Modifier) {
    val j = vista.player(seat)
    val aJogar = !mao.over && mao.turn == seat && vista.status == "playing"
    val souEu = seat == vista.seat
    val lateral = pos == 1 || pos == 3
    val alpha = if (j != null && (j.away || !j.online)) 0.62f else 1f
    val halo = if (aJogar) HaloVez() else 0f

    Box(modifier.padding(6.dp)) {
        Column(
            Modifier
                .alpha(alpha)
                .cartao(raio = 15.dp, cor = if (j == null) Cafe.Paper.copy(alpha = 0.7f) else Cafe.teamBg(j.team), sombra = 2.dp)
                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            horizontalAlignment = if (lateral) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Cafe.team(j?.team ?: "a"))
                        .border(1.5.dp, Cafe.Espresso, CircleShape),
                ) {
                    if (j != null) Avatar(j.avatar, j.name, 24.dp, Modifier.align(Alignment.Center))
                }
                if (!lateral) {
                    Text(
                        j?.name ?: "lugar livre",
                        style = CafeType.text(11, FontWeight.Bold),
                        maxLines = 1,
                        modifier = Modifier.width(if (j == null) 60.dp else 66.dp),
                    )
                }
            }
            if (lateral && j != null) {
                Text(j.name, style = CafeType.text(9, FontWeight.Bold), maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (j == null) {
                    Text("vazio", style = CafeType.text(9, FontWeight.Bold, Cafe.Espresso.copy(alpha = 0.4f)))
                } else {
                    Chip(
                        when {
                            j.bot && j.away -> "saiu"
                            j.bot -> "auto"
                            else -> TEAMS.getValue(j.team).name
                        },
                        if (j.bot) Cafe.Grape else Cafe.team(j.team),
                        corTexto = if (j.bot) Cafe.Paper else Cafe.Espresso,
                    )
                    if (j.cards > 0) ContadorCartas(j.cards)
                }
            }
        }
        if (aJogar) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(15.dp))
                    .border(3.dp, Cafe.Amber.copy(alpha = halo), RoundedCornerShape(15.dp)),
            )
        }
        if (j != null && j.host) {
            Text("👑", fontSize = 12.sp, modifier = Modifier.align(Alignment.TopStart).offset((-2).dp, (-7).dp))
        }
        if (souEu) {
            Chip("tu", Cafe.Matcha, modifier = Modifier.align(Alignment.TopEnd).offset(2.dp, (-5).dp))
        }
    }
}

@Composable
private fun ContadorCartas(n: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Cafe.WoodMid)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text("$n", style = CafeType.text(10, FontWeight.ExtraBold, Cafe.Cream))
        Box(
            Modifier
                .size(width = 7.dp, height = 10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.linearGradient(listOf(Cafe.WoodLight, Cafe.Coffee)),
                )
                .border(1.dp, Cafe.Espresso, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun EmoteFlutuante(texto: String, pos: Int, alinhamento: Alignment) {
    val t = remember(texto) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(texto) {
        t.animateTo(1f, androidx.compose.animation.core.tween(1600, easing = LinearEasingLocal))
    }
    Box(Modifier.fillMaxSize().padding(40.dp), alinhamento) {
        Text(
            texto,
            fontSize = (20 + t.value * 12).sp,
            modifier = Modifier
                .alpha(1f - t.value * t.value)
                .offset(y = (-80 * t.value).dp),
        )
    }
}

private val LinearEasingLocal = androidx.compose.animation.core.LinearEasing

// ----------------------------------------------------------------------- mão

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaoPropria(vista: Vista, mao: Mao, jogar: (String) -> Unit) {
    val ordem = remember(mao.hand) { sortHand(mao.hand) }
    val dica = when {
        mao.over -> "A mão acabou"
        mao.yourTurn && mao.legal.size == 1 -> "Só tens uma carta legal"
        mao.yourTurn -> "Toca numa carta para a jogar"
        else -> "${vista.player(mao.turn)?.name ?: "A mesa"} está a jogar"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .cartao(raio = 16.dp, cor = Cafe.Paper, sombra = 3.dp)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                dica,
                style = CafeType.text(11, FontWeight.Bold, Cafe.Espresso.copy(alpha = 0.7f)),
                modifier = Modifier.weight(1f),
            )
            Chip("trunfo ${SUIT_INFO.getValue(mao.trump).name}", Cafe.TrumpFace)
        }
        Espaco(8.dp)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ordem.forEachIndexed { i, carta ->
                key(carta) {
                    val pode = mao.yourTurn && carta in mao.legal
                    EntraNaMao(i, mao.hand) {
                        Carta(
                            card = carta,
                            width = 54.dp,
                            trump = suitOf(carta) == mao.trump,
                            dimmed = mao.yourTurn && !pode,
                            showPoints = true,
                            onClick = if (pode) ({ jogar(carta) }) else null,
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------ registo e acções

@Composable
private fun RegistoVazas(mao: Mao, todas: Boolean, onMudou: (Boolean) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .cartao(raio = 16.dp, cor = Cafe.Paper, sombra = 3.dp)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Vazas",
                style = CafeType.text(12, FontWeight.ExtraBold, Cafe.AmberDeep),
                modifier = Modifier.weight(1f),
            )
            InterruptorPill(todas, onMudou)
        }
        Espaco(6.dp)
        if (mao.history.isEmpty()) {
            Text(
                "As vazas desta mão aparecem aqui.",
                style = CafeType.text(11, FontWeight.Medium, Cafe.Espresso.copy(alpha = 0.5f)),
            )
        } else {
            val mostradas = if (todas) mao.history.reversed() else listOf(mao.history.last())
            mostradas.forEachIndexed { i, vaza ->
                val numero = mao.history.size - i
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "$numero",
                        style = CafeType.text(10, FontWeight.Bold, Cafe.Espresso.copy(alpha = 0.45f)),
                        modifier = Modifier.width(16.dp),
                    )
                    vaza.plays.forEach { p ->
                        Box(
                            Modifier
                                .width(20.dp)
                                .height(28.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (p.seat == vaza.winner) Cafe.Amber else Cafe.CardFace)
                                .border(1.dp, Cafe.Espresso, RoundedCornerShape(3.dp)),
                            Alignment.Center,
                        ) {
                            Text(RANK_INFO.getValue(rankOf(p.card)).label, style = CafeType.text(9, FontWeight.ExtraBold))
                        }
                    }
                    Text(
                        "${vaza.points} p · ${TEAMS.getValue(vaza.team).name}",
                        style = CafeType.text(10, FontWeight.Bold, Cafe.teamDeep(vaza.team)),
                    )
                }
                Spacer(Modifier.height(3.dp))
            }
        }
    }
}

@Composable
private fun InterruptorPill(todas: Boolean, onMudou: (Boolean) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Cafe.Espresso.copy(alpha = 0.08f))
            .padding(2.dp),
    ) {
        listOf(false to "Última", true to "Todas").forEach { (valor, rotulo) ->
            Box(
                Modifier
                    .premivel { onMudou(valor) }
                    .clip(RoundedCornerShape(50))
                    .background(if (valor == todas) Cafe.Amber else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text(rotulo, style = CafeType.text(10, FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun Banner(g: Mao, vista: Vista) {
    val texto = when {
        g.over -> "Mão terminada"
        g.yourTurn -> "É a tua vez"
        else -> "${vista.player(g.turn)?.name ?: "A mesa"} a jogar"
    }
    Box(
        Modifier
            .fillMaxWidth()
            .cartao(raio = 14.dp, cor = if (g.yourTurn) Cafe.Matcha else Cafe.Paper, sombra = 2.dp)
            .padding(vertical = 8.dp),
        Alignment.Center,
    ) {
        Text(texto, style = CafeType.text(15, FontWeight.ExtraBold))
    }
}

@Composable
private fun AccoesMesa(vista: Vista, mao: Mao, sessao: Sessao, abrirRegras: () -> Unit) {
    var picker by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .cartao(raio = 16.dp, cor = Cafe.Paper, sombra = 3.dp)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (picker) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EMOTES.forEach { (emoji, nome) ->
                    Box(
                        Modifier
                            .premivel { sessao.emote(emoji); picker = false }
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Cafe.Cream)
                            .border(2.dp, Cafe.Espresso, RoundedCornerShape(12.dp)),
                        Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(emoji, fontSize = 18.sp)
                            Text(nome, style = CafeType.text(7, FontWeight.Bold, Cafe.Espresso.copy(alpha = 0.6f)))
                        }
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotaoCafe("☕ Emotes", { picker = true }, Modifier.weight(1f), cor = CorBotao.Papel)
                BotaoCafe("Regras", abrirRegras, Modifier.weight(1f), cor = CorBotao.Papel)
                BotaoCafe("Sair", { sessao.sairDaMesa() }, Modifier.weight(1f), cor = CorBotao.Perigo)
            }
        }
        val podePassar = mao.over && vista.matchWinner == null
        val podeReiniciar = vista.matchWinner != null && vista.isHost
        if (podePassar || podeReiniciar) {
            BotaoCafe(
                if (podeReiniciar) "Nova partida" else "Mão seguinte",
                { if (podeReiniciar) sessao.revanche() else sessao.maoSeguinte() },
                Modifier.fillMaxWidth(),
                cor = if (podeReiniciar) CorBotao.Azul else CorBotao.Verde,
                grande = true,
            )
        }
    }
}
