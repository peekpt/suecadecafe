// O dono das mesas e o que cada endpoint HTTP devolve. Toda a decisão de
// protocolo está aqui, separada do Ktor, para se poder testar sem rede.
package com.sueca.cafe.net

import kotlinx.serialization.json.*
import kotlin.random.Random

data class Http(val code: Int, val json: JsonObject)

class RoomStore(val port: Int = PORT) {
    private val rooms = LinkedHashMap<String, Room>()
    var ip: () -> String = { Lan.ipv4() }
    var rng: () -> Double = { Math.random() }

    val openRooms: List<Room> get() = rooms.values.toList()

    fun get(id: String?): Room? = id?.let { rooms[it] }

    private fun newRoomId(): String {
        var id: String
        do {
            id = (1..4).map { CODE_CHARS[Random.nextInt(CODE_CHARS.length)] }.joinToString("")
        } while (rooms.containsKey(id))
        return id
    }

    fun tickAll(now: Long) {
        val dead = ArrayList<String>()
        for ((id, room) in rooms) if (!room.tick(now)) dead.add(id)
        for (id in dead) rooms.remove(id)
    }

    fun clear(now: Long) {
        for (r in rooms.values) r.close(now, "cancel")
        rooms.clear()
    }

    private fun liveRoom(id: String?): Room? = id?.let { rooms[it] }?.takeIf { it.status != "closed" }

    fun info(): Http = Http(200, jsonObj(
        "app" to "sueca-no-cafe", "port" to port, "lanIp" to ip(), "players" to 4,
    ))

    fun listRooms(): Http = Http(200,
        jsonObj("rooms" to rooms.values.filter { it.status != "closed" }.map { publicRoom(it) }))

    fun createRoom(body: JsonObject, now: Long): Http {
        if (rooms.size >= MAX_ROOMS) return Http(503, jsonObj("error" to "Demasiadas mesas abertas."))
        val player = body["player"] as? JsonObject ?: EMPTY_JSON
        val room = Room(newRoomId(), cleanStr(body.text("name"), 24).ifEmpty { "Mesa de café" })
        room.created = now
        room.rng = rng
        val you = room.addSeat(player.text("name"), player.text("avatar"), "a", false, now)
            ?: return Http(500, jsonObj("error" to "Não houve lugar na mesa."))
        rooms[room.id] = room
        return Http(200, jsonObj("id" to room.id, "seat" to you.seat, "tk" to you.tk,
            "room" to viewFor(room, you.seat)))
    }

    fun join(id: String?, body: JsonObject, now: Long): Http {
        val room = liveRoom(id) ?: return Http(404, jsonObj("error" to "Essa mesa já não existe."))
        if (room.status != "lobby") {
            // mesa em jogo: só há lugar se alguém saiu e deixou a cadeira guardada
            val open = room.seats.firstOrNull { it != null && it.away }
            if (open == null) return Http(409, jsonObj("error" to "Essa mesa já está a jogar."))
            open.name = cleanStr(body.text("name"), 14).ifEmpty { "Jogador" }
            open.avatar = cleanStr(body.text("avatar"), 12).ifEmpty { "cafe" }
            open.tk = newToken()            // quem saiu perde o lugar: passou para o novo
            open.bot = false
            open.online = true
            open.away = false
            open.ready = true
            open.lastSeen = now
            room.emit(now, "t" to "took", "seat" to open.seat, "name" to open.name)
            return Http(200, jsonObj("seat" to open.seat, "tk" to open.tk,
                "room" to viewFor(room, open.seat)))
        }
        val s = room.addSeat(body.text("name"), body.text("avatar"), body.text("team"), false, now)
            ?: return Http(409, jsonObj("error" to "Essa equipa já tem os dois jogadores."))
        room.emit(now, "t" to "joined", "seat" to s.seat)
        return Http(200, jsonObj("seat" to s.seat, "tk" to s.tk, "room" to viewFor(room, s.seat)))
    }

    fun state(id: String?, seat: Int?, tk: String?, since: Long, now: Long): Http {
        val room = liveRoom(id) ?: return Http(404, jsonObj("error" to "Essa mesa já não existe."))
        val s = seat?.let { room.seatAt(it) }
            ?: return Http(409, jsonObj("error" to "Saíste da mesa."))
        val dono = s.tk != null && s.tk == tk
        if (!s.bot && !dono) return Http(403, jsonObj("error" to "Credencial errada."))
        if (dono) {
            s.lastSeen = now
            if (s.bot || !s.online) {
                s.bot = false
                s.online = true
                s.away = false
                room.emit(now, "t" to "back", "seat" to s.seat, "name" to s.name)
            }
        }
        return Http(200, jsonObj("view" to viewFor(room, s.seat),
            "events" to room.eventsSince(since)))
    }

    fun act(id: String?, body: JsonObject, now: Long): Http {
        val room = liveRoom(id) ?: return Http(404, jsonObj("error" to "Essa mesa já não existe."))
        val who = body.int("seat")
        val s = who?.let { room.seatAt(it) }
            ?: return Http(409, jsonObj("error" to "Saíste da mesa."))
        if (s.tk != null && s.tk != body.text("tk"))
            return Http(403, jsonObj("error" to "Credencial errada."))
        s.lastSeen = now
        s.online = true
        val r = room.action(who, body, now)
        val error = r.error
        if (error != null) return Http(400, jsonObj("error" to error))
        val at = r.seat ?: who
        return Http(200, buildJsonObject {
            add("ok", true)
            for ((k, v) in r.fields) put(k, v)
            if (r.seat != null) add("seat", r.seat)
            add("view", viewFor(room, at))
        })
    }

    // Só para a UI do anfitrião: a vista pública da mesa, sem credencial nenhuma.
    fun peek(id: String?, seat: Int): Http {
        val room = liveRoom(id) ?: return Http(404, jsonObj("error" to "Essa mesa já não existe."))
        return Http(200, jsonObj("view" to viewFor(room, seat)))
    }
}

private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
