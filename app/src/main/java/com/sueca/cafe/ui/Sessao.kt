// O que a interface sabe e pode pedir. Um só caminho para o anfitrião e para o
// convidado: os dois falam com um servidor por HTTP, o anfitrião com o seu próprio.
package com.sueca.cafe.ui

import android.content.Context
import com.sueca.cafe.net.Aviso
import com.sueca.cafe.net.LOOPBACK
import com.sueca.cafe.net.PORT
import com.sueca.cafe.net.Http
import com.sueca.cafe.net.MesaPublica
import com.sueca.cafe.net.TableClient
import com.sueca.cafe.net.Vista
import com.sueca.cafe.net.avisoFrom
import com.sueca.cafe.net.int
import com.sueca.cafe.net.mesasPublicasFrom
import com.sueca.cafe.net.normalizeBase
import com.sueca.cafe.net.text
import com.sueca.cafe.net.viewFrom
import com.sueca.cafe.svc.CafeService
import com.sueca.cafe.svc.HostStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

enum class Ligacao { Nada, Ligando, Ligado, Fraca, SemServidor }

enum class Fase { Inicio, AAnunciar, AEntrar, EscolherMesa, Dentro }

class Sessao(private val context: Context) {
    private val prefs = context.getSharedPreferences("sueca", Context.MODE_PRIVATE)

    private val _nome = MutableStateFlow(prefs.getString("nome", "").orEmpty())
    val nome: StateFlow<String> = _nome

    private val _boneco = MutableStateFlow(prefs.getInt("boneco", 0))
    val boneco: StateFlow<Int> = _boneco

    private val _fase = MutableStateFlow(Fase.Inicio)
    val fase: StateFlow<Fase> = _fase

    private val _vista = MutableStateFlow<Vista?>(null)
    val vista: StateFlow<Vista?> = _vista

    private val _feed = MutableStateFlow<List<Aviso>>(emptyList())
    val feed: StateFlow<List<Aviso>> = _feed

    private val _ligacao = MutableStateFlow(Ligacao.Nada)
    val ligacao: StateFlow<Ligacao> = _ligacao

    private val _recado = MutableStateFlow<String?>(null)
    val recado: StateFlow<String?> = _recado

    private val _aDecidir = MutableStateFlow<List<MesaPublica>>(emptyList())
    val mesasEncontradas: StateFlow<List<MesaPublica>> = _aDecidir

    val urlAnfitriao: StateFlow<String> = HostStatus.url
    val codigoAnfitriao: StateFlow<String> = HostStatus.room

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var cliente: TableClient? = null
    private var sala = ""
    private var lugar = -1
    private var passe = ""
    private var desde = 0L
    private var vigia: Job? = null
    private var anfitriao = false

    val token: String get() = "d0:${_boneco.value.coerceIn(0, 7)}"
    val temMesa: Boolean get() = sala.isNotEmpty() && lugar >= 0

    fun escreveNome(texto: String) {
        _nome.value = texto.take(14)
        prefs.edit().putString("nome", _nome.value).apply()
    }

    fun escolheBoneco(v: Int) {
        _boneco.value = v.coerceIn(0, 7)
        prefs.edit().putInt("boneco", _boneco.value).apply()
    }

    private fun apelido(): String = _nome.value.trim().ifEmpty { "Jogador" }

    // ---------------------------------------------------------------- anfitrião

    /** Abre mesa: liga o servidor interno e entra nela como primeiro jogador. */
    fun abrirMesa(nomeMesa: String) {
        anfitriao = true
        _fase.value = Fase.AAnunciar
        _ligacao.value = Ligacao.Ligando
        CafeService.start(context, PORT)
        scope.launch {
            // o serviço diz-nos quando a porta está a escutar
            var espera = 0
            while (!HostStatus.running.value && espera < 40) {
                delay(100)
                espera++
            }
            if (!HostStatus.running.value) {
                _ligacao.value = Ligacao.SemServidor
                avisa("Não consegui abrir a mesa.")
                _fase.value = Fase.Inicio
                return@launch
            }
            usaCliente(LOOPBACK)
            val r = cliente!!.create(
                nomeMesa.ifBlank { "Mesa de café" }, apelido(), token,
            )
            entra(r, "Não consegui abrir a mesa.")
        }
    }

    // ---------------------------------------------------------------- convidado

    /** Tenta entrar no servidor do endereço dado; se não houver código, mostra as mesas. */
    fun procurar(endereco: String, codigo: String) {
        val base = normalizeBase(endereco)
        if (base.isEmpty()) {
            avisa("Escreve o endereço do telemóvel do anfitrião.")
            return
        }
        _fase.value = Fase.AEntrar
        _ligacao.value = Ligacao.Ligando
        usaCliente(base)
        scope.launch {
            val c = cliente!!
            if (codigo.length == 4) {
                entra(c.join(codigo.uppercase(), apelido(), token), "Não consegui entrar.")
            } else {
                val l = c.rooms()
                if (l.code != 200) {
                    _ligacao.value = Ligacao.SemServidor
                    avisa(l.json.text("error") ?: "Sem ligação a $base")
                    _fase.value = Fase.Inicio
                    return@launch
                }
                _aDecidir.value = mesasPublicasFrom(l.json)
                _fase.value = Fase.EscolherMesa
                _ligacao.value = Ligacao.Ligado
            }
        }
    }

    fun entrarNa(codigo: String) {
        _fase.value = Fase.AEntrar
        scope.launch {
            val c = cliente ?: return@launch
            entra(c.join(codigo.uppercase(), apelido(), token), "Não consegui entrar.")
        }
    }

    /** O convite que vem do QR do anfitrião: `sueca://entrar?host=…&mesa=…`. */
    fun entrarPorLink(host: String, mesa: String) {
        if (host.isEmpty()) return
        if (_fase.value != Fase.Inicio) {
            avisa("Já estás numa mesa — sai primeiro para entrares noutra.")
            return
        }
        procurar(host, mesa)
    }

    // ------------------------------------------------------------------- comum

    private fun usaCliente(base: String) {
        cliente?.close()
        cliente = TableClient(base)
    }

    private suspend fun entra(r: Http, falha: String) {
        val c = cliente ?: return
        if (r.code != 200) {
            _ligacao.value = Ligacao.SemServidor
            avisa(r.json.text("error") ?: falha)
            _fase.value = Fase.Inicio
            return
        }
        // o `create` traz o id à frente; o `join` só o mostra dentro da vista
        val corpo = r.json["view"] as? JsonObject ?: r.json["room"] as? JsonObject
        sala = r.json.text("id") ?: corpo?.text("id") ?: sala
        lugar = r.json.int("seat") ?: lugar
        passe = r.json.text("tk").orEmpty()
        desde = 0L
        _feed.value = emptyList()
        _vista.value = viewFrom(corpo)
        _fase.value = Fase.Dentro
        _ligacao.value = Ligacao.Ligado
        guardarLugar(c.root, sala, lugar, passe)
        vigiar()
    }

    private fun guardarLugar(base: String, id: String, seat: Int, tk: String) {
        prefs.edit().putString("base", base).putString("sala", id)
            .putInt("lugar", seat).putString("passe", tk).apply()
    }

    /** Ao abrir a app, retoma a mesa onde estávamos, se ainda lá estivermos. */
    fun retomar() {
        if (_fase.value != Fase.Inicio) return
        val base = prefs.getString("base", "") ?: ""
        val id = prefs.getString("sala", "") ?: ""
        val seat = prefs.getInt("lugar", -1)
        val tk = prefs.getString("passe", "") ?: ""
        if (base.isEmpty() || id.isEmpty() || seat < 0 || tk.isEmpty()) return
        usaCliente(base)
        val c = cliente ?: return
        _fase.value = Fase.AEntrar
        scope.launch {
            val r = c.state(id, seat, tk, 0L)
            if (r.code == 200) {
                sala = id; lugar = seat; passe = tk; desde = 0L
                _vista.value = viewFrom(r.json["view"] as? JsonObject)
                _fase.value = Fase.Dentro
                _ligacao.value = Ligacao.Ligado
                vigiar()
            } else {
                limpaLugar()
                _fase.value = Fase.Inicio
            }
        }
    }

    private fun limpaLugar() {
        prefs.edit().remove("base").remove("sala").remove("lugar").remove("passe").apply()
        sala = ""; lugar = -1; passe = ""
    }

    private fun vigiar() {
        vigia?.cancel()
        val c = cliente ?: return
        vigia = scope.launch {
            var falhas = 0
            while (isActive) {
                val r = runCatching { c.state(sala, lugar, passe, desde) }.getOrNull()
                if (r == null || r.code == 0) {
                    falhas++
                    _ligacao.value = if (falhas == 1) Ligacao.Fraca else Ligacao.SemServidor
                    if (falhas >= 4) {
                        avisa("Perdi a ligação à mesa.")
                        _fase.value = Fase.Inicio
                        limpaLugar()
                        break
                    }
                    delay(700)
                    continue
                }
                if (falhas > 0) _ligacao.value = Ligacao.Ligado
                falhas = 0
                when (r.code) {
                    200 -> {
                        recebe(r.json)
                        val v = _vista.value
                        if (v?.status == "closed") break
                    }
                    else -> {
                        avisa(r.json.text("error") ?: "Saíste da mesa.")
                        _fase.value = Fase.Inicio
                        limpaLugar()
                        break
                    }
                }
            }
        }
    }

    private fun recebe(json: JsonObject) {
        viewFrom(json["view"] as? JsonObject)?.let { _vista.value = it }
        val eventos = (json["events"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: return
        if (eventos.isEmpty()) return
        val avisos = eventos.map(::avisoFrom)
        desde = maxOf(desde, avisos.maxOf { it.seq })
        _feed.value = (_feed.value + avisos).takeLast(60)
        for (ev in avisos) {
            val quem = nomeDe(ev.seat)
            val minha = ev.seat == lugar
            when (ev.kind) {
                "joined" -> quem?.let { avisa("$it sentou-se.") }
                "left" -> quem?.let { avisa("$it saiu da mesa.") }
                "gone" -> avisa(
                    if (minha) "Estavas ausente — o parceiro automático pegou nas tuas cartas."
                    else quem?.let { "$it desapareceu — o automático joga por essa cadeira." } ?: ""
                )
                "host" -> if (minha) avisa("Ficaste como anfitrião da mesa.")
                "took" -> avisa(if (minha) "Outro jogador entrou na tua cadeira." else "$quem entrou na cadeira.")
                "team" -> quem?.let { avisa("$it mudou de equipa.") }
                "rules" -> avisa("O anfitrião mexeu nas regras da casa.")
                else -> {}
            }
        }
        if (_vista.value?.status == "closed") avisa("A mesa fechou.")
    }

    private fun nomeDe(seat: Int?): String? =
        seat?.let { _vista.value?.player(it)?.name }

    /** Pede uma acção e pinta logo a vista que o servidor devolve. */
    fun actua(vararg campo: Pair<String, Any?>) {
        val c = cliente ?: return
        if (!temMesa) return
        scope.launch {
            val r = c.act(sala, lugar, passe, *campo)
            if (r.code == 200) {
                (r.json["view"] as? JsonObject)?.let { _vista.value = viewFrom(it) }
                r.json.int("seat")?.let { lugar = it }
            } else {
                avisa(r.json.text("error") ?: "Não foi possível.")
            }
        }
    }

    fun estaDentro(dentro: Boolean) = actua("type" to "ready", "value" to dentro)
    fun paraEquipa(team: String) = actua("type" to "team", "value" to team)
    fun jogar(card: String) = actua("type" to "play", "card" to card)
    fun comecar() = actua("type" to "start")
    fun maoSeguinte() = actua("type" to "next")
    fun revanche() = actua("type" to "rematch")
    fun emote(texto: String) = actua("type" to "emote", "text" to texto)
    fun diz(texto: String) = actua("type" to "chat", "text" to texto)
    fun regra(chave: String, valor: Any) = actua("type" to "rules", "key" to chave, "value" to valor)
    /** O anfitrião fecha a mesa para toda a gente e desliga o servidor interno. */
    fun fecharMesa() {
        val eraAnfitriao = anfitriao
        actua("type" to "cancel")
        if (eraAnfitriao) {
            anfitriao = false
            CafeService.stop(context)
        }
    }

    fun sairDaMesa() {
        val c = cliente ?: return
        val id = sala
        val meuLugar = lugar
        val meuPasse = passe
        val eraAnfitriao = anfitriao
        scope.launch {
            runCatching {
                c.act(id, meuLugar, meuPasse, "type" to if (eraAnfitriao) "cancel" else "leave")
            }
            acabar()
        }
    }

    fun avisa(texto: String) {
        if (texto.isEmpty()) return
        _recado.value = texto
        scope.launch {
            delay(2800)
            if (_recado.value == texto) _recado.value = null
        }
    }

    fun consomeRecado() {
        _recado.value = null
    }

    fun voltarAoInicio() {
        vigia?.cancel()
        limpaLugar()
        _vista.value = null
        _feed.value = emptyList()
        _fase.value = Fase.Inicio
        _ligacao.value = Ligacao.Nada
    }

    fun acabar() {
        vigia?.cancel()
        vigia = null
        cliente?.close()
        cliente = null
        if (anfitriao) CafeService.stop(context)
        anfitriao = false
        _fase.value = Fase.Inicio
        _vista.value = null
        _ligacao.value = Ligacao.Nada
        // guardamos o lugar: se a cadeira ficou guardada, ao voltar retomamos a mão
    }

    fun fechar() {
        scope.cancel()
        cliente?.close()
    }
}
