// A vista que o servidor manda, traduzida em tipos que a interface entende.
// O teste VistaTest.kt confirma que isto bate certo com o que viewFor() produz,
// para o cliente nativo nunca ler um campo que o servidor não escreve.
package com.sueca.cafe.net

import com.sueca.cafe.TEAMS
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class Jogador(
    val seat: Int,
    val name: String,
    val avatar: String,
    val team: String,
    val bot: Boolean,
    val online: Boolean,
    val ready: Boolean,
    val away: Boolean,
    val cards: Int,
    val host: Boolean,
)

data class Colocada(val seat: Int, val card: String)

data class Vaza(
    val winner: Int,
    val team: String,
    val points: Int,
    val cards: List<String>,
    val plays: List<Colocada>,
)

data class ResultadoMao(
    val pointsA: Int,
    val pointsB: Int,
    val gamesA: Int,
    val gamesB: Int,
    val winner: String?,
    val kind: String,
    val label: String,
    val bandeira: Boolean,
)

data class Mao(
    val dealer: Int,
    val turn: Int,
    val leader: Int,
    val trump: String,
    val turned: String?,
    val turnedLive: Boolean,
    val turnedSeat: Int?,
    val trick: List<Colocada>,
    val lastTrick: Vaza?,
    val pointsA: Int,
    val pointsB: Int,
    val tricksA: Int,
    val tricksB: Int,
    val over: Boolean,
    val result: ResultadoMao?,
    val left: List<Int>,
    val hand: List<String>,
    val legal: Set<String>,
    val yourTurn: Boolean,
    val history: List<Vaza>,
)

data class Regras(
    val virarTrunfo: Boolean = true,
    val trunfarSeVazio: Boolean = false,
    val subirSempre: Boolean = false,
    val jogosParaGanhar: Int = 4,
    val bandeiraFecha: Boolean = true,
)

data class Vista(
    val id: String,
    val name: String,
    val status: String,
    val seat: Int,
    val rules: Regras,
    val fillBots: Boolean,
    val seats: List<Jogador?>,
    val host: Int,
    val gamesA: Int,
    val gamesB: Int,
    val matchWinner: String?,
    val matchVia: String?,
    val handsPlayed: Int,
    val handNo: Int,
    val mao: Mao?,
) {
    val me: Jogador? get() = seats.getOrNull(seat)
    val isHost: Boolean get() = host == seat
    val playing: Boolean get() = mao != null && status != "lobby"
    val watcher: Boolean get() = me == null

    fun player(i: Int): Jogador? = seats.getOrNull(i)

    fun teamLabel(t: String) = TEAMS[t]?.name ?: t
}

data class Aviso(val seq: Long, val kind: String, val text: String, val seat: Int?)

private val NO_ARRAY = JsonArray(emptyList())

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.arr(key: String): JsonArray = this[key] as? JsonArray ?: NO_ARRAY

private fun JsonObject.str(key: String, dflt: String = ""): String = text(key) ?: dflt

private fun JsonObject.num(key: String, dflt: Int = 0): Int = int(key) ?: dflt

private fun JsonArray.strings(): List<String> =
    mapNotNull { (it as? JsonPrimitive)?.content }.filter { it.isNotEmpty() && it != "null" }

private fun JsonArray.plays(): List<Colocada> = mapNotNull { el ->
    (el as? JsonObject)?.let { Colocada(it.num("seat"), it.str("card")) }
}

// Um JsonNull lido como texto vem com conteúdo "null" — para os campos opcionais
// é o mesmo que não vir nada.
private fun JsonObject.maybeStr(key: String): String? =
    when (val el = this[key]) {
        null, is JsonNull -> null
        is JsonPrimitive -> el.content.takeIf { it.isNotEmpty() && it != "null" }
        else -> null
    }

private fun vazaOf(o: JsonObject): Vaza = Vaza(
    winner = o.num("winner"),
    team = o.str("team"),
    points = o.num("points"),
    cards = o.arr("cards").strings(),
    plays = o.arr("plays").plays(),
)

fun rulesFrom(o: JsonObject?): Regras {
    if (o == null) return Regras()
    return Regras(
        virarTrunfo = o.flag("virarTrunfo", true),
        trunfarSeVazio = o.flag("trunfarSeVazio", false),
        subirSempre = o.flag("subirSempre", false),
        jogosParaGanhar = o.num("jogosParaGanhar", 4),
        bandeiraFecha = o.flag("bandeiraFecha", true),
    )
}

fun resultFrom(o: JsonObject?): ResultadoMao? {
    val p = o?.obj("points") ?: return null
    val g = o.obj("games")
    return ResultadoMao(
        pointsA = p.num("a"), pointsB = p.num("b"),
        gamesA = g?.num("a") ?: 0, gamesB = g?.num("b") ?: 0,
        winner = o.maybeStr("winner"),
        kind = o.str("kind"),
        label = o.str("label"),
        bandeira = o.flag("bandeira", false),
    )
}

fun viewFrom(o: JsonObject?): Vista? {
    val v = o ?: return null
    val seats = v.arr("seats").map { el ->
        (el as? JsonObject)?.let {
            Jogador(
                seat = it.num("seat"), name = it.str("name"), avatar = it.str("avatar"),
                team = it.str("team"), bot = it.flag("bot", false), online = it.flag("online", false),
                ready = it.flag("ready", false), away = it.flag("away", false),
                cards = it.num("cards"), host = it.flag("host", false),
            )
        }
    }
    val match = v.obj("match")
    val games = match?.obj("games")
    val g = v.obj("game")
    val mao = g?.let {
        val points = it.obj("points")
        val tricks = it.obj("tricks")
        Mao(
            dealer = it.num("dealer"), turn = it.num("turn"), leader = it.num("leader"),
            trump = it.str("trump"), turned = it.maybeStr("turned"),
            turnedLive = it.flag("turnedLive", false),
            turnedSeat = it.int("turnedSeat"),
            trick = it.arr("trick").plays(),
            lastTrick = it.obj("lastTrick")?.let(::vazaOf),
            pointsA = points?.num("a") ?: 0, pointsB = points?.num("b") ?: 0,
            tricksA = tricks?.num("a") ?: 0, tricksB = tricks?.num("b") ?: 0,
            over = it.flag("over", false),
            result = resultFrom(it.obj("result")),
            left = it.arr("left").strings().mapNotNull { c -> c.toIntOrNull() },
            hand = it.arr("yourHand").strings(),
            legal = it.arr("legal").strings().toSet(),
            yourTurn = it.flag("yourTurn", false),
            history = it.arr("history").mapNotNull { e -> (e as? JsonObject)?.let(::vazaOf) },
        )
    }
    return Vista(
        id = v.str("id"), name = v.str("name"), status = v.str("status"),
        seat = v.num("seat"), rules = rulesFrom(v.obj("rules")),
        fillBots = v.flag("fillBots", true), seats = seats, host = v.num("host"),
        gamesA = games?.num("a") ?: 0, gamesB = games?.num("b") ?: 0,
        matchWinner = match?.maybeStr("winner"), matchVia = match?.maybeStr("via"),
        handsPlayed = match?.num("hands") ?: 0, handNo = v.num("handNo"),
        mao = mao,
    )
}

// Um evento do feed reduzido ao que a interface costuma mostrar.
fun avisoFrom(e: JsonObject): Aviso = Aviso(
    seq = (e["seq"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L,
    kind = e.str("t"),
    text = e.maybeStr("text").orEmpty(),
    seat = e.int("seat"),
)

/** Uma linha da lista de mesas anunciadas (`GET /api/rooms`). */
data class MesaPublica(
    val id: String,
    val name: String,
    val status: String,
    val handNo: Int,
    val taken: Int,
    val gamesA: Int,
    val gamesB: Int,
    val seats: List<Jogador?>,
) {
    val livre: Boolean get() = status == "lobby" && taken < 4
}

fun mesasPublicasFrom(o: JsonObject): List<MesaPublica> =
    o.arr("rooms").mapNotNull { el ->
        (el as? JsonObject)?.let { r ->
            val games = r.obj("games")
            MesaPublica(
                id = r.str("id"), name = r.str("name"), status = r.str("status"),
                handNo = r.num("handNo"), taken = r.num("taken"),
                gamesA = games?.num("a") ?: 0, gamesB = games?.num("b") ?: 0,
                seats = r.arr("seats").map { s ->
                    (s as? JsonObject)?.let {
                        Jogador(
                            seat = it.num("seat"), name = it.str("name"), avatar = it.str("avatar"),
                            team = it.str("team"), bot = it.flag("bot", false),
                            online = it.flag("online", false), ready = false, away = it.flag("away", false),
                            cards = 0, host = false,
                        )
                    }
                },
            )
        }
    }
