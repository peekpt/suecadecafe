// O princípio: quem somos, abrir mesa (e o servidor interno acende-se) ou
// entrar na mesa de alguém pelo endereço e código.
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sueca.cafe.net.MesaPublica
import com.sueca.cafe.net.PORT
import androidx.compose.foundation.layout.fillMaxSize

@Composable
fun Inicio(
    sessao: Sessao,
    nome: String,
    boneco: Int,
    abrirRegras: () -> Unit,
) {
    var mesaAberta by remember { mutableStateOf(false) }
    var aEntrar by remember { mutableStateOf(false) }
    var nomeMesa by remember { mutableStateOf("Mesa do canto") }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CartaoIdentidade(sessao, nome, boneco)
        Espaco(14.dp)

        if (aEntrar) {
            EcraEntrar(sessao)
        } else {
            CampoTexto(
                valor = nomeMesa,
                onValor = { nomeMesa = it },
                rotulo = "Nome da mesa",
                dica = "ex.: Mesa do canto",
                limite = 24,
            )
            Espaco(12.dp)
            BotaoCafe(
                texto = "Abrir mesa de 4",
                onClick = { mesaAberta = true; sessao.abrirMesa(nomeMesa) },
                modifier = Modifier.fillMaxWidth(),
                grande = true,
            )
            if (mesaAberta) {
                Espaco(10.dp)
                Text("A acender o servidor da mesa…", style = CafeType.text(12, FontWeight.Bold, Cafe.Matcha))
            }
            Espaco(10.dp)
            BotaoCafe(
                texto = "Entrar numa mesa",
                onClick = { aEntrar = true },
                modifier = Modifier.fillMaxWidth(),
                cor = CorBotao.Azul,
                grande = true,
            )
        }

        Espaco(14.dp)
        BotaoCafe("📜 Como se joga (regras de Portugal)", abrirRegras, cor = CorBotao.Papel)
        Espaco(14.dp)
        Text(
            "A app de quem abre a mesa é o servidor: os outros ligam-se pelo wi-fi, " +
                "sem internet e sem contas. Quatro lugares, duas equipas.",
            style = CafeType.text(11, FontWeight.Medium, Cafe.Espresso.copy(alpha = 0.6f)),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
    }
}

@Composable
private fun CartaoIdentidade(sessao: Sessao, nome: String, boneco: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .cartao(raio = 18.dp, cor = Cafe.Paper, sombra = 3.dp)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Cafe.AvatarBg[boneco % 8])
                    .border(2.5.dp, Cafe.Espresso, CircleShape),
                Alignment.Center,
            ) { Avatar("d0:$boneco", nome.ifEmpty { "Jogador" }, 50.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("O teu nome", style = CafeType.text(11, FontWeight.Bold, Cafe.AmberDeep))
                CampoTexto(
                    valor = nome,
                    onValor = sessao::escreveNome,
                    dica = "ex.: Zé do Café",
                    limite = 14,
                    tamanhoLetra = 17,
                    corFundo = Cafe.Cream,
                )
            }
        }
        Espaco(12.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("O teu avatar", style = CafeType.text(11, FontWeight.Bold, Cafe.AmberDeep))
            Spacer(Modifier.width(8.dp))
            Text("é assim que te vão ver à mesa", style = CafeType.text(10, FontWeight.Medium, Cafe.Espresso.copy(alpha = 0.55f)))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f))
            BotaoCafe("🎲", { sessao.escolheBoneco((0..7).random()) }, cor = CorBotao.Papel)
        }
        Espaco(8.dp)
        EscolhaAvatar(
            name = nome.ifEmpty { "Jogador" },
            variant = boneco,
            onPick = sessao::escolheBoneco,
        )
    }
}

@Composable
private fun EcraEntrar(sessao: Sessao) {
    var endereco by remember { mutableStateOf("") }
    var codigo by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxWidth()
            .cartao(raio = 18.dp, cor = Cafe.Paper, sombra = 3.dp)
            .padding(14.dp),
    ) {
        Text("Entrar numa mesa", style = CafeType.title(17))
        Espaco(10.dp)
        CampoTexto(
            valor = endereco,
            onValor = { endereco = it },
            rotulo = "Endereço do telemóvel do anfitrião",
            dica = "ex.: 192.168.1.20:$PORT",
            limite = 40,
            tamanhoLetra = 15,
        )
        Espaco(10.dp)
        CampoTexto(
            valor = codigo,
            onValor = { codigo = it.uppercase() },
            rotulo = "Código da mesa",
            dica = "ex.: K645",
            limite = 4,
            tamanhoLetra = 22,
            alinharCentro = true,
            teclaFeita = { if (codigo.length == 4) sessao.procurar(endereco, codigo) },
        )
        Espaco(12.dp)
        BotaoCafe(
            texto = "Entrar",
            onClick = { sessao.procurar(endereco, codigo) },
            modifier = Modifier.fillMaxWidth(),
            cor = CorBotao.Azul,
            grande = true,
        )
        Espaco(8.dp)
        Text(
            "Podes só escrever o endereço: mostramos as mesas que lá estão anunciadas. " +
                "O código tem 4 letras e números.",
            style = CafeType.text(10, FontWeight.Medium, Cafe.Espresso.copy(alpha = 0.6f)),
        )
    }
}

@Composable
fun ATrabalhar(texto: String, detalhe: String = "") {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Cafe.Amber.copy(alpha = 0.3f)),
            Alignment.Center,
        ) { Copito(26.dp) }
        Espaco(12.dp)
        Text(texto, style = CafeType.text(15, FontWeight.ExtraBold))
        if (detalhe.isNotEmpty()) {
            Text(detalhe, style = CafeType.text(11, FontWeight.Medium, Cafe.Espresso.copy(alpha = 0.6f)), textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ListaMesas(sessao: Sessao, mesas: List<MesaPublica>) {
    Column(
        Modifier
            .fillMaxWidth()
            .cartao(raio = 18.dp, cor = Cafe.Paper, sombra = 3.dp)
            .padding(14.dp),
    ) {
        Text("Mesas anunciadas", style = CafeType.title(17))
        Espaco(10.dp)
        if (mesas.isEmpty()) {
            Text(
                "Ainda ninguém anunciou mesas aqui. Pede ao anfitrião o código e escreve-o em cima.",
                style = CafeType.text(12, FontWeight.Medium, Cafe.Espresso.copy(alpha = 0.6f)),
            )
        }
        mesas.forEach { m ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Cafe.Cream)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${m.id} · ${m.name}", style = CafeType.text(14, FontWeight.Bold))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        m.seats.filterNotNull().forEach { j ->
                            Avatar(j.avatar, j.name, 18.dp)
                        }
                    }
                }
                Chip(
                    when {
                        m.status == "lobby" -> "à espera"
                        m.status == "over" -> "acabou"
                        else -> "a jogar (mão ${m.handNo})"
                    },
                    if (m.status == "lobby") Cafe.Matcha else Cafe.Amber,
                )
                Spacer(Modifier.width(8.dp))
                BotaoCafe(
                    texto = if (m.taken >= 4) "Cheia" else "Entrar",
                    onClick = { sessao.entrarNa(m.id) },
                    activo = m.taken < 4 && m.status == "lobby",
                    cor = CorBotao.Azul,
                )
            }
        }
    }
}

@Composable
fun CabecalhoApp(estado: Ligacao, anfitriao: Boolean, url: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp), Alignment.Center) { Copito(28.dp, Cafe.Espresso) }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Sueca no Café",
                style = CafeType.hero(20),
            )
            if (anfitriao && url.isNotEmpty()) {
                Text(url.removePrefix("http://"), style = CafeType.text(9, FontWeight.Bold, Cafe.AmberDeep))
            }
        }
        FaixaLigacao(estado)
    }
}
