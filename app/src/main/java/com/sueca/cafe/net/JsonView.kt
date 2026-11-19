// Tradutores entre o modelo do motor e o formato JSON que circula na rede.
// Mantém as mesmas chaves do anunciador antigo (webapp_old/server.mjs), para que
// qualquer cliente — a app nativa ou o browser — entenda a mesma resposta.
package com.sueca.cafe.net

import com.sueca.cafe.*
import kotlinx.serialization.json.*

fun j(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is JsonElement -> v
    is Boolean -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    is String -> JsonPrimitive(v)
    is Iterable<*> -> JsonArray(v.map { j(it) })
    is Array<*> -> JsonArray(v.map { j(it) })
    else -> JsonPrimitive(v.toString())
}

fun JsonObjectBuilder.add(key: String, value: Any?) {
    put(key, j(value))
}

fun jsonObj(vararg fields: Pair<String, Any?>): JsonObject =
    buildJsonObject { for ((k, v) in fields) add(k, v) }

val EMPTY_JSON = JsonObject(emptyMap())

fun JsonObject.text(key: String): String? {
    val p = this[key] as? JsonPrimitive ?: return null
    return if (p.isString) p.content else null
}

fun JsonObject.number(key: String): Double? {
    val p = this[key] as? JsonPrimitive ?: return null
    return p.content.toDoubleOrNull()
}

fun JsonObject.int(key: String): Int? = number(key)?.toInt()

// Como `!!v` do JavaScript, com valor por omissão quando o campo não vem.
fun JsonObject.flag(key: String, dflt: Boolean): Boolean {
    val el = this[key] ?: return dflt
    if (el is JsonNull) return dflt
    if (el !is JsonPrimitive) return true
    val c = el.content
    if (el.isString) return c.isNotEmpty() && c != "false" && c != "0"
    return when (c) {
        "true" -> true
        "false" -> false
        else -> (c.toDoubleOrNull() ?: 1.0) != 0.0
    }
}

fun rulesJson(r: Rules) = jsonObj(
    "virarTrunfo" to r.virarTrunfo,
    "trunfarSeVazio" to r.trunfarSeVazio,
    "subirSempre" to r.subirSempre,
    "jogosParaGanhar" to r.jogosParaGanhar,
    "bandeiraFecha" to r.bandeiraFecha,
)

fun countJson(c: TeamCount) = jsonObj("a" to c.a, "b" to c.b)
fun gamesJson(m: Match) = jsonObj("a" to m.gamesA, "b" to m.gamesB)

fun playJson(p: Play) = jsonObj("seat" to p.seat, "card" to p.card)

fun trickJson(t: TrickRecord) = jsonObj(
    "winner" to t.winner, "team" to t.team, "cards" to t.cards,
    "points" to t.points, "plays" to t.plays.map { playJson(it) },
)

fun historyJson(t: TrickRecord) = jsonObj(
    "winner" to t.winner, "team" to t.team, "points" to t.points,
    "plays" to t.plays.map { playJson(it) },
)

fun resultJson(r: HandResult) = jsonObj(
    "points" to jsonObj("a" to r.pointsA, "b" to r.pointsB),
    "tricks" to jsonObj("a" to r.tricksA, "b" to r.tricksB),
    "games" to jsonObj("a" to r.gamesA, "b" to r.gamesB),
    "winner" to r.winner, "kind" to r.kind, "label" to r.label, "bandeira" to r.bandeira,
)

fun seatJson(room: Room, i: Int): JsonObject? {
    val s = room.seats[i] ?: return null
    return jsonObj(
        "seat" to s.seat, "team" to s.team, "name" to s.name, "avatar" to s.avatar,
        "bot" to s.bot, "online" to s.online, "ready" to s.ready, "away" to s.away,
        "cards" to (room.hand?.hands?.get(i)?.size ?: 0),
        "host" to (room.host == i),
    )
}

// O que um lugar vê: a própria mão por inteiro, dos outros só informações públicas.
fun viewFor(room: Room, seat: Int): JsonObject = buildJsonObject {
    val g = room.hand
    val you = room.seatAt(seat)
    add("app", "sueca-no-cafe")
    add("id", room.id)
    add("name", room.name)
    add("status", room.status)
    add("rules", rulesJson(room.rules))
    add("fillBots", room.fillBots)
    add("seat", seat)
    add("you", you?.let {
        jsonObj("seat" to it.seat, "name" to it.name, "avatar" to it.avatar,
            "team" to it.team, "bot" to it.bot)
    })
    add("seats", List(4) { i -> seatJson(room, i) })
    add("match", jsonObj(
        "games" to gamesJson(room.match), "winner" to room.match.winner,
        "via" to room.match.via, "hands" to room.match.hands.size,
    ))
    add("handNo", room.handNo)
    add("host", room.host)
    if (g == null) return@buildJsonObject

    val watcher = you == null
    add("game", jsonObj(
        "dealer" to g.dealer, "turn" to g.turn, "leader" to g.leader,
        "trump" to g.trump, "turned" to g.turned, "turnedLive" to g.turnedLive,
        "turnedSeat" to g.turnedSeat,
        "trick" to g.trick.map { playJson(it) },
        "lastTrick" to g.history.lastOrNull()?.let { trickJson(it) },
        "points" to countJson(g.points), "tricks" to countJson(g.tricks),
        "over" to g.over,
        "result" to if (g.over) g.result?.let { resultJson(it) } else null,
        "left" to g.hands.map { it.size },
        "yourHand" to if (watcher) emptyList<String>() else g.hands[seat],
        "legal" to if (watcher || g.over) emptyList<String>() else legalPlays(g, seat),
        "yourTurn" to (!watcher && !g.over && g.turn == seat),
        "history" to g.history.map { historyJson(it) },
    ))
}

fun publicRoom(r: Room) = jsonObj(
    "id" to r.id, "name" to r.name, "status" to r.status, "handNo" to r.handNo,
    "trump" to r.hand?.trump,
    "seats" to r.seats.mapIndexed { i, s ->
        if (s == null) null else jsonObj(
            "seat" to i, "name" to s.name, "team" to s.team,
            "bot" to s.bot, "online" to s.online, "away" to s.away,
        )
    },
    "taken" to r.seats.count { it != null },
    "games" to gamesJson(r.match),
    "created" to r.created,
)
