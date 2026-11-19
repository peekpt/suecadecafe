// As regras de Portugal, tal como estavam escritas no cliente web. É a única
// vez que se explicam: os ecrãs remetem todas para este diálogo.
package com.sueca.cafe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sueca.cafe.BuildConfig

private val Secoes = listOf(
    "O baralho" to listOf(
        "40 cartas: ficam de fora o 8, o 9 e o 10. Em cada naipe a ordem é " +
            "**Ás, 7, Rei, Valete, Dama, 6, 5, 4, 3, 2**.",
        "O **Valete bate a Dama** — é a regra que apanha sempre alguém de surpresa.",
        "Pontos: **Ás 11** · **7 10** · **Rei 4** · **Valete 3** · **Dama 2**. " +
            "As outras não valem nada. No baralho há **120 pontos**.",
    ),
    "A mesa" to listOf(
        "Quatro jogadores em **duas equipas**: quem está defronte de ti é o teu parceiro.",
        "Joga-se para a **direita**, no sentido contrário ao dos ponteiros do relógio.",
        "Dão-se **10 cartas** a cada um, em dois lotes de cinco. Quem dá guarda a " +
            "última carta, **virada para cima** encostada à sua cadeira: é ela que marca " +
            "o **trunfo** e recolhe-se à **terceira vaza** — ou antes, se for jogada.",
    ),
    "A vaza" to listOf(
        "Quem abre define o naipe e **os outros assistem** se tiverem cartas desse naipe.",
        "Sem o naipe, podes **trunfar** ou **baldar-te** — não és obrigado a matar.",
        "Ganha a vaza o **trunfo mais alto** ou, sem trunfos, a **carta mais alta do naipe " +
            "de saída**. Quem ganha abre a vaza seguinte.",
        "Todos os pontos das cartas da vaza vão para a equipa que a ganhou.",
        "Por baixo da mão escolhes se o registo mostra só a **última vaza** ou " +
            "**as vazas todas** da mão.",
    ),
    "Marcar jogos" to listOf(
        "**61 a 90** pontos = 1 jogo · **91 a 119** = 2 jogos · **120** = **bandeira** " +
            "(4 jogos e fecha a partida) · **60-60** ninguém marca.",
        "Ganha a partida a primeira equipa a chegar aos **4 jogos** — o alvo combina-se na sala.",
        "Regras da casa à escolha: obrigar a trunfar, obrigar a subir, virar ou não virar o trunfo.",
    ),
    "Quem sai do lugar" to listOf(
        "A mão não pára: o **parceiro automático** joga por essa cadeira, que fica **guardada**.",
        "O **anfitrião** é avisado e decide: **aguardar** que alguém entre, ou **fechar a mesa**.",
        "Quem saiu pode **retomar as suas cartas** abrindo o mesmo link; se entrar outra " +
            "pessoa, essa pessoa continua a mão naquele lugar.",
    ),
    "Etiqueta" to listOf(
        "Entre parceiros diz-se só o essencial, como **qual é o trunfo** quando alguém " +
            "pergunta. Dica de carta é renúncia a sério.",
        "Aqui o servidor não deixa jogar carta ilegal, por isso **renúncia é impossível**.",
    ),
)

// O "**negrito**" do texto, para não repetir a marcação à mão em cada linha.
private fun negrito(texto: String): AnnotatedString = buildAnnotatedString {
    var resto = texto
    var aNegrito = false
    while (resto.isNotEmpty()) {
        val corte = resto.indexOf("**")
        val pedaço = if (corte < 0) resto else resto.substring(0, corte)
        if (aNegrito) pushStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = Cafe.AmberDeep))
        append(pedaço)
        if (aNegrito) pop()
        aNegrito = !aNegrito
        resto = if (corte < 0) "" else resto.substring(corte + 2)
    }
}

@Composable
fun DialogoRegras(fechar: () -> Unit) {
    Dialog(fechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 620.dp)
                .clip(RoundedCornerShape(20.dp))
                .cartao(raio = 20.dp, cor = Cafe.Paper, sombra = 5.dp)
                .background(Cafe.Paper)
                .padding(18.dp),
        ) {
            TituloCafe("📜 Regras de Portugal", 22)
            Espaco(6.dp)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                for ((titulo, linhas) in Secoes) {
                    Text(
                        titulo.uppercase(),
                        style = CafeType.text(11, FontWeight.ExtraBold, Cafe.Coffee),
                        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                    )
                    for (l in linhas) {
                        Text(
                            bullet(l),
                            style = CafeType.text(12, FontWeight.Medium, Cafe.Espresso),
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
                }
            }
            Espaco(14.dp)
            Text(
                "Sueca no Café · v${BuildConfig.VERSION_NAME}",
                style = CafeType.text(11, FontWeight.Medium, Cafe.Coffee.copy(alpha = 0.65f)),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
            )
            BotaoCafe(
                texto = "Fechar",
                onClick = fechar,
                modifier = Modifier.fillMaxWidth(),
                cor = CorBotao.Ambar,
                grande = true,
            )
        }
    }
}

private fun bullet(texto: String): AnnotatedString = buildAnnotatedString {
    append("·  ")
    append(negrito(texto))
}
