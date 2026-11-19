// Garante que o cliente nativo lê exactamente o que o servidor escreve: se
// viewFor() mudar uma chave, um destes testes passa a falhar.
// Uso: ./gradlew :app:testDebugUnitTest
package com.sueca.cafe

import com.sueca.cafe.net.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

private fun obj(vararg fields: Pair<String, Any>): JsonObject = buildJsonObject {
    for ((k, v) in fields) put(k, when (v) {
        is JsonElement -> v
        is String -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        else -> JsonPrimitive(v.toString())
    })
}

class VistaTest {
    private fun store() = RoomStore(8743).apply {
        rng = { 0.5 }
        ip = { "10.0.0.1" }
    }

    private fun view(json: JsonObject): Vista = viewFrom(json["view"] as? JsonObject
        ?: json["room"] as? JsonObject)!!

    @Test
    fun `a sala de espera chega bem lida do outro lado`() {
        val s = store()
        val created = s.createRoom(obj(
            "name" to "Mesa da Rua",
            "player" to obj("name" to "Ana", "avatar" to "d0:1"),
        ), 1000L)
        val v = view(created.json)
        assertEquals(200, created.code)
        assertEquals("lobby", v.status)
        assertEquals(0, v.seat)
        assertTrue(v.isHost)
        assertFalse(v.playing)
        assertNull(v.mao)
        assertEquals("Ana", v.me?.name)
        assertEquals("d0:1", v.me?.avatar)
        assertEquals("a", v.me?.team)
        assertEquals(4, v.seats.size)
        assertNull(v.seats[1])
        assertEquals(Regras(), v.rules)
        assertTrue(v.fillBots)
    }

    @Test
    fun `em jogo o cliente ve a mão dele e não a dos outros`() {
        val s = store()
        val created = s.createRoom(obj(
            "name" to "Mesa", "player" to obj("name" to "Ana", "avatar" to "d1:2"),
        ), 1000L)
        val id = (created.json["id"] as JsonPrimitive).content
        val tk = (created.json["tk"] as JsonPrimitive).content
        s.join(id, obj("name" to "Bruno", "avatar" to "d0:3", "team" to "b"), 1100L)
        val started = s.act(id, obj("seat" to 0, "tk" to tk, "type" to "start"), 1200L)
        val v = view(started.json)
        assertTrue(v.playing)
        val g = v.mao!!
        assertEquals(10, g.hand.size)
        assertEquals(listOf(10, 10, 10, 10), g.left)
        assertEquals(10, v.seats[1]?.cards ?: 0)     // do Bruno só se sabe quantas tem
        assertTrue(g.turn == 0 || g.turn == 1 || g.turn == 2 || g.turn == 3)
        assertEquals(g.yourTurn, g.turn == v.seat)
        assertTrue(g.trump in listOf("e", "c", "o", "p"))
        assertNotNull(g.turned)
        assertTrue(g.history.isEmpty())
        assertTrue(g.trick.isEmpty())
    }

    @Test
    fun `as vazas lidas têm o vencedor e os pontos`() {
        val s = store()
        val created = s.createRoom(obj("name" to "Mesa", "player" to obj("name" to "Ana")), 1000L)
        val id = (created.json["id"] as JsonPrimitive).content
        val tk = (created.json["tk"] as JsonPrimitive).content
        s.act(id, obj("seat" to 0, "tk" to tk, "type" to "start"), 1200L)
        val room = s.get(id)!!
        // deixa a mesa nos automáticos e corre o relógio até haver vazas
        for (seat in room.seats) seat!!.bot = true
        room.botDelayMs = { 0 }
        var now = 2000L
        var guard = 0
        while (room.hand!!.history.isEmpty() && guard++ < 200) {
            now += 100
            s.tickAll(now)
        }
        val st = s.state(id, 0, tk, 0L, now)
        val v = view(st.json)
        val primeira = v.mao!!.history.first()
        assertEquals(4, primeira.plays.size)
        assertTrue(primeira.points > 0)
        assertEquals(primeira.winner, room.hand!!.history.first().winner)
        assertNotNull(v.mao!!.lastTrick)
        assertTrue(v.mao!!.tricksA + v.mao!!.tricksB >= 1)
    }

    @Test
    fun `o convidado fica a saber o id da mesa a partir do join`() {
        val s = store()
        val created = s.createRoom(obj("name" to "Mesa", "player" to obj("name" to "Ana")), 1000L)
        val id = (created.json["id"] as JsonPrimitive).content
        val j = s.join(id, obj("name" to "Bruno", "avatar" to "d0:3", "team" to "b"), 1100L)
        assertEquals(200, j.code)
        // o `join` não repete o id à frente: o cliente tem de o ir buscar à vista,
        // senão pede o estado para /api/rooms//state e perde a mesa em 3 segundos
        val sala = j.json.text("id") ?: (j.json["room"] as? JsonObject)?.text("id")
        assertEquals(id, sala)
        val st = s.state(sala!!, j.json.int("seat")!!, j.json.text("tk")!!, 0L, 1200L)
        assertEquals(200, st.code)
    }

    @Test
    fun `os eventos do feed chegam como avisos`() {        val s = store()
        val created = s.createRoom(obj("name" to "Mesa", "player" to obj("name" to "Ana")), 1000L)
        val id = (created.json["id"] as JsonPrimitive).content
        val tk = (created.json["tk"] as JsonPrimitive).content
        s.act(id, obj("seat" to 0, "tk" to tk, "type" to "chat", "text" to "olá"), 1300L)
        val st = s.state(id, 0, tk, 0L, 1400L)
        val events = (st.json["events"] as JsonArray).map { avisoFrom(it as JsonObject) }
        val chat = events.first { it.kind == "chat" }
        assertEquals("olá", chat.text)
        assertEquals(0, chat.seat)
        assertTrue(events.first().kind.isNotEmpty())
    }
}
