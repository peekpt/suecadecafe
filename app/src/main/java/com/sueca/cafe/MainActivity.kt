// A raiz da app: decide o ecrã a partir da fase da sessão e mantém o recado,
// o cabeçalho de ligação e as regras sempre por cima do que estiver a correr.
package com.sueca.cafe

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sueca.cafe.net.MesaPublica
import com.sueca.cafe.net.Vista
import com.sueca.cafe.ui.ATrabalhar
import com.sueca.cafe.ui.CabecalhoApp
import com.sueca.cafe.ui.DialogoRegras
import com.sueca.cafe.ui.Espaco
import com.sueca.cafe.ui.Fase
import com.sueca.cafe.ui.Inicio
import com.sueca.cafe.ui.ListaMesas
import com.sueca.cafe.ui.Mesa
import com.sueca.cafe.ui.Patio
import com.sueca.cafe.ui.Recado
import com.sueca.cafe.ui.Sessao
import com.sueca.cafe.ui.SuecaTheme
import com.sueca.cafe.ui.fundoCafe

class MainActivity : ComponentActivity() {
    private lateinit var sessao: Sessao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessao = Sessao(this)
        setContent { SuecaTheme { App(sessao) } }
        trataLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        trataLink(intent)
    }

    override fun onStart() {
        super.onStart()
        sessao.retomar()
    }

    override fun onDestroy() {
        if (isFinishing) sessao.fechar()
        super.onDestroy()
    }

    /** Link que o QR do anfitrião abre: `sueca://entrar?host=ip:porta&mesa=CODIGO`. */
    private fun trataLink(i: Intent?) {
        val d = i?.data ?: return
        if (d.scheme != "sueca") return
        i.data = null
        sessao.entrarPorLink(d.getQueryParameter("host").orEmpty(), d.getQueryParameter("mesa").orEmpty())
    }
}

@Composable
fun App(sessao: Sessao) {
    val fase by sessao.fase.collectAsState()
    val vista by sessao.vista.collectAsState()
    val nome by sessao.nome.collectAsState()
    val boneco by sessao.boneco.collectAsState()
    val ligacao by sessao.ligacao.collectAsState()
    val recado by sessao.recado.collectAsState()
    val mesas by sessao.mesasEncontradas.collectAsState()
    val url by sessao.urlAnfitriao.collectAsState()
    var regras by rememberSaveable { mutableStateOf(false) }

    // Em Android 15+ a app desenha por baixo da barra de estado: o pano vai até
    // ao topo, o conteúdo pára onde os ícones do sistema começam.
    Box(Modifier.fillMaxSize().fundoCafe()) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .align(Alignment.TopCenter),
        ) {
            CabecalhoApp(ligacao, url.isNotEmpty(), url)
            Box(Modifier.padding(horizontal = 14.dp)) {
                Conteudo(fase, vista, sessao, nome, boneco, mesas, url) { regras = true }
            }
        }
        recado?.let {
            Recado(
                it,
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.ime)
                    .padding(bottom = 18.dp),
            )
        }
    }
    if (regras) DialogoRegras { regras = false }
}

@Composable
private fun Conteudo(
    fase: Fase,
    vista: Vista?,
    sessao: Sessao,
    nome: String,
    boneco: Int,
    mesas: List<MesaPublica>,
    url: String,
    abrirRegras: () -> Unit,
) {
    when (fase) {
        Fase.Dentro -> when {
            vista == null -> ATrabalhar("A entrar na mesa…")
            vista.playing -> Mesa(vista, sessao, abrirRegras)
            else -> Patio(vista, sessao, url, abrirRegras)
        }
        Fase.AAnunciar -> ATrabalhar("A acender o servidor da mesa…", "A abrir a porta 8743 na rede.")
        Fase.AEntrar -> ATrabalhar("A procurar a mesa…")
        Fase.EscolherMesa -> Column {
            ListaMesas(sessao, mesas)
            Espaco(12.dp)
            Inicio(sessao, nome, boneco, abrirRegras)
        }
        Fase.Inicio -> Inicio(sessao, nome, boneco, abrirRegras)
    }
}
