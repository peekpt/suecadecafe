// A mesa: quem está em cada lugar, o que cada um vê, as ações possíveis e o
// relógio que faz os bots jogar e liberta quem desapareceu.
// É toda lógica do anunciador antigo (webapp_old/server.mjs), sem HTTP nenhum,
// para se poder testar sem accender o servidor.
package com.sueca.cafe.net

import com.sueca.cafe.*
import kotlinx.serialization.json.*
import kotlin.random.Random

const val PORT = 8743
const val ROOM_TTL = 60 * 60 * 1000L   // mesa sem ninguém durante uma hora morre
const val LOBBY_TTL = 45 * 1000L       // lugar na sala de espera sem sinais liberta a cadeira
const val GAME_TTL = 150 * 1000L       // em jogo, quem desaparece é tapado pelo parceiro automático
const val NEXT_HAND_MS = 14 * 1000L    // pausa entre mãos
const val CLOSED_KEEP = 60 * 1000L
const val FEED_MAX = 80
const val MAX_ROOMS = 32

private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
private const val TOKEN_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"

fun cleanStr(s: String?, max: Int): String =
    (s ?: "").replace(Regex("\\s+"), " ").trim().take(max)

fun newToken(): String = (1..8).map { TOKEN_CHARS[Random.nextInt(TOKEN_CHARS.length)] }.joinToString("")

class Seat(
    var seat: Int,
    var team: String,
    var name: String,
    var avatar: String,
    var bot: Boolean,
    var tk: String?,
    var online: Boolean,
    var away: Boolean,
    var ready: Boolean,
    var lastSeen: Long,
)

class ActResult private constructor(
    val error: String?,
    val seat: Int?,
    val fields: JsonObject,
) {
    companion object {
        fun err(message: String) = ActResult(message, null, EMPTY_JSON)
        fun ok(seat: Int? = null, fields: JsonObject = EMPTY_JSON) = ActResult(null, seat, fields)
    }
}

class Room(val id: String, var name: String) {
    var status = "lobby"              // lobby | playing | over | closed
    var host = 0
    val seats = arrayOfNulls<Seat>(4)
    var rules = Rules()
    var fillBots = true
    var hand: GameState? = null
    var match = newMatch()
    var dealer = 0
    var handNo = 0
    var nextHandAt = 0L
    var botAt = 0L
    val feed = ArrayList<JsonObject>()
    var seq = 0L
    var created = 0L
    var startedAt = 0L
    var closedAt = 0L
    var rng: () -> Double = { Math.random() }
    var botDelayMs: () -> Long = { botDelay().toLong() }

    fun seatAt(seat: Int): Seat? = if (seat in 0..3) seats[seat] else null

    fun emit(now: Long, vararg fields: Pair<String, Any?>) {
        seq++
        val item = buildJsonObject {
            add("seq", seq)
            add("at", now)
            for ((k, v) in fields) add(k, v)
        }
        feed.add(item)
        while (feed.size > FEED_MAX) feed.removeAt(0)
    }

    fun eventsSince(since: Long): List<JsonObject> =
        feed.filter { ((it["seq"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L) > since }

    fun humans(): List<Seat> = seats.filterNotNull().filter { !it.bot }
    fun humanOnline(): Boolean = seats.any { it != null && !it.bot && it.online }
    fun botsNeeded(): Boolean = seats.any { it == null }

    // O anfitrião fica no 0 (em baixo, para ele); o parceiro entra no 2, frente a
    // frente, e os adversários nos 1 e 3.
    fun freeSeatFor(team: String): Int {
        val wanted = if (team == "b") listOf(1, 3) else listOf(0, 2)
        return wanted.firstOrNull { seats[it] == null } ?: -1
    }

    fun addSeat(name: String?, avatar: String?, team: String?, bot: Boolean, now: Long): Seat? {
        val want = if (team == "b") "b" else "a"
        val seat = freeSeatFor(want)
        if (seat < 0) return null
        val s = Seat(
            seat = seat,
            team = teamOfSeat(seat),          // a equipa resulta do lugar
            name = cleanStr(name, 14).ifEmpty { "Jogador" },
            avatar = cleanStr(avatar, 12).ifEmpty { "cafe" },
            bot = bot,
            tk = if (bot) null else newToken(),
            online = !bot,
            away = false,
            ready = bot,
            lastSeen = now,
        )
        seats[seat] = s
        return s
    }

    fun startHand(now: Long) {
        handNo++
        val g = createHand(rules, dealer, rng)
        hand = g
        status = "playing"
        nextHandAt = 0
        botAt = 0
        emit(now, "t" to "deal", "handNo" to handNo, "dealer" to dealer,
            "trump" to g.trump, "turned" to g.turned)
    }

    // Fecho de uma mão: marca jogos, decide a partida e põe o relógio da mão seguinte.
    fun settleHand(now: Long) {
        val res = hand?.result ?: return
        match = applyHand(match, res, rules)
        if (match.winner != null) {
            status = "over"
            nextHandAt = 0
        } else {
            nextHandAt = now + NEXT_HAND_MS
        }
        emit(now, "t" to "hand", "result" to resultJson(res), "match" to gamesJson(match),
            "winner" to match.winner, "via" to match.via, "at" to nextHandAt)
    }

    fun close(now: Long, reason: String = "") {
        if (status == "closed") return
        status = "closed"
        closedAt = now
        emit(now, "t" to "closed", "reason" to reason)
    }

    // Quem sai a meio deixa a cadeira guardada: o automático joga por ele, a mão
    // fica lá, e o mesmo token volta a tomar o lugar. A coroa passa para outro humano.
    fun handToBot(seat: Int, timeout: Boolean, now: Long): Seat? {
        val s = seatAt(seat) ?: return null
        if (s.bot) return null
        s.online = false
        s.bot = true
        s.away = true
        if (host == seat) {
            val next = humans().firstOrNull { it.seat != seat }
            if (next != null) {
                host = next.seat
                emit(now, "t" to "host", "seat" to next.seat, "name" to next.name)
            }
        }
        emit(now, "t" to "gone", "seat" to seat, "name" to s.name, "timeout" to timeout, "host" to host)
        emit(now, "t" to "botIn", "seat" to seat, "timeout" to timeout)
        return s
    }

    fun startMatch(now: Long) {
        for (i in 0..3) {
            if (seats[i] == null) {
                val s = addSeat("Parceiro ${i + 1}", "d0:0", teamOfSeat(i), true, now)
                if (s != null) emit(now, "t" to "botIn", "seat" to s.seat)
            }
        }
        match = newMatch()
        handNo = 0
        hand = null
        startedAt = now
        startHand(now)
    }

    fun action(seat: Int, body: JsonObject, now: Long): ActResult {
        val s = seatAt(seat) ?: return ActResult.err("Lugar livre — entra na mesa primeiro.")
        when (body.text("type")) {
            "ready" -> {
                s.ready = body.flag("value", !s.ready)
                emit(now, "t" to "ready", "seat" to seat, "value" to s.ready)
                return ActResult.ok()
            }
            "team" -> {
                if (status != "lobby") return ActResult.err("A partida já começou.")
                if (s.ready) return ActResult.err("Tira o \"estou dentro\" primeiro.")
                val want = if (body.text("value") == "b") "b" else "a"
                if (teamOfSeat(seat) == want) return ActResult.ok()
                val free = freeSeatFor(want)
                if (free < 0) return ActResult.err("Essa equipa já tem os dois jogadores.")
                seats[seat] = null
                s.seat = free
                s.team = teamOfSeat(free)
                seats[free] = s
                if (host == seat) host = free
                emit(now, "t" to "team", "seat" to free)
                return ActResult.ok(seat = free)
            }
            "chat", "emote" -> {
                val type = body.text("type")!!
                val text = cleanStr(body.text("text"), 60)
                if (text.isEmpty() && type == "chat") return ActResult.err("vazio")
                emit(now, "t" to type, "seat" to seat, "text" to text)
                return ActResult.ok()
            }
            "rules" -> {
                if (host != seat) return ActResult.err("Só o anfitrião muda as regras.")
                if (status != "lobby") return ActResult.err("As regras só se combinam antes de começar.")
                val key = body.text("key")
                val value = body.number("value")
                when (key) {
                    "jogosParaGanhar" -> {
                        val n = value?.toInt()
                        rules.jogosParaGanhar = if (n != null && n in listOf(1, 2, 4, 6, 10)) n else 4
                    }
                    "fillBots" -> fillBots = body.flag("value", true)
                    "virarTrunfo" -> rules.virarTrunfo = body.flag("value", rules.virarTrunfo)
                    "trunfarSeVazio" -> rules.trunfarSeVazio = body.flag("value", rules.trunfarSeVazio)
                    "subirSempre" -> rules.subirSempre = body.flag("value", rules.subirSempre)
                    "bandeiraFecha" -> rules.bandeiraFecha = body.flag("value", rules.bandeiraFecha)
                    else -> return ActResult.err("regra desconhecida")
                }
                emit(now, "t" to "rules", "key" to key)
                return ActResult.ok(fields = jsonObj("rules" to rulesJson(rules), "fillBots" to fillBots))
            }
            "start" -> {
                if (host != seat) return ActResult.err("Só o anfitrião começa o jogo.")
                if (status != "lobby") return ActResult.err("A mesa já está em jogo.")
                // com os automáticos ligados pode jogar-se sozinho contra a mesa toda
                if (!fillBots && botsNeeded()) return ActResult.err("Faltam jogadores para as duas equipas.")
                startMatch(now)
                emit(now, "t" to "start")
                return ActResult.ok()
            }
            "next" -> {
                val g = hand
                if (g == null || !g.over) return ActResult.err("Ainda não acabaste esta mão.")
                if (match.winner != null) return ActResult.err("A partida já acabou.")
                dealer = (dealer + 1) % 4
                startHand(now)
                return ActResult.ok()
            }
            "rematch" -> {
                if (host != seat) return ActResult.err("Só o anfitrião reinicia.")
                dealer = (dealer + 1) % 4
                startMatch(now)
                emit(now, "t" to "start", "rematch" to true)
                return ActResult.ok()
            }
            "play" -> {
                val g = hand
                if (g == null || g.over) return ActResult.err("Não há mão a decorrer.")
                if (s.bot) return ActResult.err("Esse lugar é automático.")
                val card = body.text("card") ?: ""
                when (val res = playCard(g, seat, card)) {
                    is PlayResult.Error -> return ActResult.err(res.message)
                    else -> {
                        emit(now, "t" to "play", "seat" to seat, "card" to card, "trump" to g.trump)
                        if (res is PlayResult.Finished) settleHand(now)
                        return ActResult.ok()
                    }
                }
            }
            "leave" -> {
                if (status == "lobby") {
                    // na sala de espera a cadeira fica logo livre para quem quiser entrar
                    emit(now, "t" to "left", "seat" to seat)
                    if (host == seat) {
                        val next = humans().firstOrNull { it.seat != seat }
                        host = next?.seat ?: seat
                    }
                    seats[seat] = null
                } else {
                    handToBot(seat, false, now)
                }
                if (!humanOnline()) close(now)
                return ActResult.ok()
            }
            "cancel" -> {
                if (host != seat) return ActResult.err("Só o anfitrião fecha a mesa.")
                close(now, "cancel")
                return ActResult.ok()
            }
            else -> return ActResult.err("ação desconhecida")
        }
    }

    // O relógio da mesa. Devolve false quando a mesa já pode sair do mapa.
    fun tick(now: Long): Boolean {
        if (status == "closed") return now - closedAt <= CLOSED_KEEP

        if (now - created > ROOM_TTL && !humanOnline()) {
            close(now)
            return true
        }

        for (s in seats) {
            if (s == null || s.bot) continue
            val ttl = if (status == "lobby") LOBBY_TTL else GAME_TTL
            if (now - s.lastSeen <= ttl) continue
            if (status == "lobby") {
                emit(now, "t" to "left", "seat" to s.seat)
                if (host == s.seat) {
                    val next = humans().firstOrNull { it.seat != s.seat }
                    host = next?.seat ?: s.seat
                }
                seats[s.seat] = null
                if (seats.none { it != null && !it.bot }) close(now)
            } else if (s.online) {
                handToBot(s.seat, true, now)
            }
        }

        val g = hand ?: return true

        if (g.over) {
            if (match.winner != null) {
                status = "over"
                return true
            }
            if (nextHandAt != 0L && now >= nextHandAt) {
                dealer = (dealer + 1) % 4
                startHand(now)
            }
            return true
        }

        val cur = seatAt(g.turn)
        if (cur != null && cur.bot) {
            if (botAt == 0L) {
                botAt = now + botDelayMs()
            } else if (now >= botAt) {
                botAt = 0
                val who = g.turn
                val card = chooseCard(g, who)
                if (card != null) {
                    var played = card
                    if (playCard(g, who, card) is PlayResult.Error) {   // rede de segurança
                        played = legalPlays(g, who).firstOrNull() ?: ""
                        if (played.isNotEmpty()) playCard(g, who, played)
                    }
                    // o lugar é o de quem jogou, guardado antes de playCard mudar a vez
                    if (played.isNotEmpty())
                        emit(now, "t" to "play", "seat" to who, "card" to played,
                            "trump" to g.trump, "bot" to true)
                    if (g.over) settleHand(now)
                }
            }
        } else {
            botAt = 0
        }
        return true
    }
}
