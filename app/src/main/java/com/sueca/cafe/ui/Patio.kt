// A sala de espera: as duas equipas, o "estou dentro", as regras da casa para o
// anfitrião e o endereço a que os outros se ligam.
package com.sueca.cafe.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sueca.cafe.TEAMS
import com.sueca.cafe.net.Jogador
import com.sueca.cafe.net.Vista

private val ALVOS = listOf(2, 4, 6, 10)

@Composable
fun Patio(vista: Vista, sessao: Sessao, endereco: String, abrirRegras: () -> Unit) {
    val contexto = LocalContext.current
    val humanos = vista.seats.count { it != null && !it.bot }
    val faltam = 4 - vista.seats.count { it != null }
    val minhaEquipa = vista.me?.team ?: "a"

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (sessao.temMesa && vista.isHost) {
            CartaoAnfitriao(vista, endereco) {
                copiar(contexto, endereco)
                sessao.avisa("Copiado ✓")
            }
            Espaco(10.dp)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .cartao(raio = 16.dp, pincel = Cafe.Wood, sombra = 3.dp)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(vista.name, style = CafeType.title(17), color = Cafe.Cream, textAlign = TextAlign.Center)
            Text("código ${vista.id}", style = CafeType.text(12, FontWeight.Bold, Cafe.Amber))
        }
        Espaco(10.dp)

        listOf("a", "b").forEach { team ->
            CartaoEquipa(
                vista = vista,
                team = team,
                minha = minhaEquipa,
                meuLugar = vista.seat,
                onMudar = { sessao.paraEquipa(team) },
            )
            Espaco(8.dp)
        }

        BotaoCafe(
            texto = if (vista.me?.ready == true) "Já não quero ✓" else "Estou dentro ✓",
            onClick = { sessao.estaDentro(vista.me?.ready != true) },
            modifier = Modifier.fillMaxWidth(),
            cor = if (vista.me?.ready == true) CorBotao.Papel else CorBotao.Verde,
            grande = true,
        )
        Espaco(10.dp)

        Text(
            when {
                faltam == 0 -> "Mesa cheia — vamos a isso!"
                !vista.fillBots -> "Faltam $faltam para encher as equipas."
                vista.isHost -> "Começas com ${vista.seats.count { it != null && it.bot }} parceiro(s) automático(s), ou espera por mais gente."
                else -> "$humanos jogador(es) à mesa · à espera que o anfitrião comece"
            },
            style = CafeType.text(12, FontWeight.SemiBold, Cafe.Espresso.copy(alpha = 0.7f)),
            textAlign = TextAlign.Center,
        )
        Espaco(12.dp)

        if (vista.isHost) {
            PanelRegras(vista, sessao)
            Espaco(10.dp)
            BotaoCafe(
                texto = "Começar a jogar",
                onClick = { sessao.comecar() },
                modifier = Modifier.fillMaxWidth(),
                cor = CorBotao.Ambar,
                grande = true,
            )
            Espaco(10.dp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            BotaoCafe("Regras", abrirRegras, Modifier.weight(1f), cor = CorBotao.Papel)
            BotaoCafe("Sair da mesa", { sessao.sairDaMesa() }, Modifier.weight(1f), cor = CorBotao.Perigo)
        }
    }
}

private fun copiar(contexto: Context, texto: String) {
    val cm = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("sueca", texto))
}

@Composable
private fun CartaoAnfitriao(vista: Vista, endereco: String, onCopiar: () -> Unit) {
    val alvo = endereco.removePrefix("http://")
    Column(
        Modifier
            .fillMaxWidth()
            .cartao(raio = 16.dp, cor = Cafe.Espresso, sombra = 3.dp)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("A MESA ESTÁ ANUNCIADA", style = CafeType.text(11, FontWeight.ExtraBold, Cafe.Matcha))
        Espaco(6.dp)
        Text(
            alvo.ifEmpty { "sem wi-fi: liga-o para os outros entrarem" },
            style = CafeType.hero(24).copy(color = Cafe.Cream),
            textAlign = TextAlign.Center,
        )
        Espaco(2.dp)
        Text("código ${vista.id}", style = CafeType.text(12, FontWeight.Bold, Cafe.Amber))
        if (alvo.isNotEmpty()) {
            Espaco(12.dp)
            QrCafe("sueca://entrar?host=$alvo&mesa=${vista.id}", 172.dp)
            Espaco(8.dp)
            Text(
                "Quem tem a app aponta a câmara e entra direito. Os outros escrevem este endereço.",
                style = CafeType.text(11, FontWeight.Medium, Cafe.Cream.copy(alpha = 0.7f)),
                textAlign = TextAlign.Center,
            )
        } else {
            Espaco(8.dp)
            Text(
                "Quem estiver no mesmo wi-fi abre este endereço e toca na tua mesa.",
                style = CafeType.text(11, FontWeight.Medium, Cafe.Cream.copy(alpha = 0.7f)),
                textAlign = TextAlign.Center,
            )
        }
        Espaco(10.dp)
        BotaoCafe("Copiar endereço", onCopiar, cor = CorBotao.Ambar)
        Espaco(8.dp)
        Text("À espera de jogadores…", style = CafeType.text(11, FontWeight.SemiBold, Cafe.Amber))
    }
}

@Composable
private fun CartaoEquipa(vista: Vista, team: String, minha: String, meuLugar: Int, onMudar: () -> Unit) {
    val lugares = (0..3).filter { vista.player(it)?.team == team }
    val information = TEAMS.getValue(team)
    val cheia = lugares.size >= 2
    val possoMudar = vista.me?.team != team && !cheia && vista.me?.ready != true
    Column(
        Modifier
            .fillMaxWidth()
            .premivel(possoMudar, onMudar)
            .cartao(raio = 16.dp, cor = Cafe.teamBg(team), sombra = 3.dp)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(Cafe.team(team)))
            Spacer(Modifier.width(6.dp))
            Text("Equipa ${information.name}", style = CafeType.title(15))
            Spacer(Modifier.width(8.dp))
            if (minha == team) Chip("a tua equipa", Cafe.team(team))
            if (cheia && minha != team) Chip("lotação esgotada", Cafe.Espresso.copy(alpha = 0.15f))
            if (!cheia) Text(
                "${2 - lugares.size} lugar(es) livre(s)",
                style = CafeType.text(10, FontWeight.Bold, Cafe.Espresso.copy(alpha = 0.55f)),
            )
        }
        Espaco(8.dp)
        lugares.forEach { lugar -> vista.player(lugar)?.let { FichaLugar(it, lugar == meuLugar) } }
        repeat(2 - lugares.size) { FichaLugar(null, false) }
    }
}

@Composable
private fun FichaLugar(j: Jogador?, souEu: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (souEu) Cafe.Paper.copy(alpha = 0.8f) else Cafe.Espresso.copy(alpha = 0.05f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (j == null) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .border(2.dp, Cafe.Espresso.copy(alpha = 0.25f), CircleShape),
            )
            Text("lugar livre", style = CafeType.text(13, FontWeight.SemiBold, Cafe.Espresso.copy(alpha = 0.45f)))
        } else {
            Avatar(j.avatar, j.name, 30.dp)
            Column(Modifier.weight(1f)) {
                Text(j.name + if (souEu) " (tu)" else "", style = CafeType.text(13, FontWeight.Bold))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (j.host) Text("👑 anfitrião", style = CafeType.text(9, FontWeight.Bold, Cafe.AmberDeep))
                    if (j.bot) Text("🤖 parceiro automático", style = CafeType.text(9, FontWeight.Bold, Cafe.Grape))
                    if (!j.bot && !j.online) Text("desligado", style = CafeType.text(9, FontWeight.Bold, Cafe.Red))
                }
            }
            if (j.ready) Chip("✓ pronto", Cafe.Matcha)
        }
    }
}

@Composable
private fun PanelRegras(vista: Vista, sessao: Sessao) {
    val r = vista.rules
    Column(
        Modifier
            .fillMaxWidth()
            .cartao(raio = 16.dp, cor = Cafe.Paper, sombra = 3.dp)
            .padding(12.dp),
    ) {
        Text("Regras da casa", style = CafeType.title(15))
        Espaco(8.dp)
        Interruptor(
            "Virar o trunfo",
            "a última carta fica encostada à cadeira de quem dá",
            r.virarTrunfo,
        ) { sessao.regra("virarTrunfo", it) }
        Espaco(6.dp)
        Interruptor(
            "Obrigar a trunfar",
            "quem não assista tem de cortar se puder",
            r.trunfarSeVazio,
        ) { sessao.regra("trunfarSeVazio", it) }
        Espaco(6.dp)
        Interruptor(
            "Obrigar a subir",
            "tens de tentar ganhar a vaza quando podes",
            r.subirSempre,
        ) { sessao.regra("subirSempre", it) }
        Espaco(6.dp)
        Interruptor(
            "Bandeira fecha",
            "os 120 pontos ganham a partida na hora",
            r.bandeiraFecha,
        ) { sessao.regra("bandeiraFecha", it) }
        Espaco(6.dp)
        Interruptor(
            "Parceiros automáticos",
            "preenchem os lugares que faltem",
            vista.fillBots,
        ) { sessao.regra("fillBots", it) }

        Espaco(10.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Partida a", style = CafeType.text(12, FontWeight.Bold, Cafe.AmberDeep))
            Spacer(Modifier.width(8.dp))
            ALVOS.forEach { alvo ->
                val activo = r.jogosParaGanhar == alvo
                Box(
                    Modifier
                        .premivel { sessao.regra("jogosParaGanhar", alvo) }
                        .clip(RoundedCornerShape(50))
                        .background(if (activo) Cafe.Amber else Cafe.Espresso.copy(alpha = 0.06f))
                        .border(2.dp, if (activo) Cafe.Espresso else Cafe.Espresso.copy(alpha = 0.15f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text("$alvo", style = CafeType.text(13, FontWeight.ExtraBold))
                }
            }
            Text("jogos", style = CafeType.text(12, FontWeight.Bold, Cafe.AmberDeep))
        }
    }
}
