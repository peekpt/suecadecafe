// Um teste que liga mesmo o servidor e lhe faz pedidos HTTP, para apanhar
// erros de rota e de formato que os testes de lógica não veem.
package com.sueca.cafe

import com.sueca.cafe.net.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL

private fun freePort(): Int = ServerSocket(0).use { it.localPort }

private class Http(val base: String) {
    fun call(path: String, body: String? = null, method: String? = null): Pair<Int, String> {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method ?: if (body == null) "GET" else "POST"
        c.connectTimeout = 4000
        c.readTimeout = 8000
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("content-type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        return code to text
    }

    fun json(path: String, body: String? = null): Pair<Int, JsonObject> {
        val (code, text) = call(path, body)
        return code to (Json.parseToJsonElement(text) as JsonObject)
    }
}

class ServerHttpTest {
    private fun withServer(block: (Http, RoomStore) -> Unit) {
        val p = freePort()
        val store = RoomStore(p).apply {
            rng = { 0.5 }
            ip = { "10.0.0.9" }
        }
        val server = CafeServer(store)
        server.start()
        try {
            block(Http("http://127.0.0.1:$p"), store)
        } finally {
            server.stop()
        }
    }

    @Test
    fun informacaoServida() {
        withServer { http, _ ->
            val (code, info) = http.json("/api/info")
            assertEquals(200, code)
            assertEquals("sueca-no-cafe", (info["app"] as JsonPrimitive).content)
            assertEquals("10.0.0.9", (info["lanIp"] as JsonPrimitive).content)
            assertEquals(4, (info["players"] as JsonPrimitive).content.toInt())
        }
    }

    @Test
    fun criarEntrarJogarPeloHttp() {
        withServer { http, _ ->
            val (code, created) = http.json("/api/rooms",
                """{"name":"Mesa HTTP","player":{"name":"Ana","avatar":"d0:3"}}""")
            assertEquals(200, code)
            val id = (created["id"] as JsonPrimitive).content
            val tk = (created["tk"] as JsonPrimitive).content
            assertEquals(0, (created["seat"] as JsonPrimitive).content.toInt())
            val view = created["room"]!! as JsonObject
            assertEquals("Mesa HTTP", (view["name"] as JsonPrimitive).content)
            assertEquals("lobby", (view["status"] as JsonPrimitive).content)

            // lista de mesas
            val rooms = http.json("/api/rooms").second["rooms"]!! as JsonArray
            assertTrue(rooms.any { (it.jsonObject["id"] as JsonPrimitive).content == id })

            // entrar para a outra equipa
            val (jcode, joined) = http.json("/api/rooms/$id/join",
                """{"name":"Bruno","team":"b","avatar":"d0:1"}""")
            assertEquals(200, jcode)
            assertEquals(1, (joined["seat"] as JsonPrimitive).content.toInt())
            val tkB = (joined["tk"] as JsonPrimitive).content
            assertNotNull(tkB)

            // estado com e sem credencial
            assertEquals(403, http.json("/api/rooms/$id/state?seat=0").first)
            val (scode, st) = http.json("/api/rooms/$id/state?seat=0&tk=$tk&since=0")
            assertEquals(200, scode)
            assertTrue((st["events"] as JsonArray).isNotEmpty())
            // na sala de espera ainda não há cartas
            assertEquals(0, ((st["view"] as JsonObject)["seats"] as JsonArray)[0].jsonObject.intOf("cards"))
            assertNull((st["view"] as JsonObject)["game"])

            // começar e jogar uma carta
            val (kcode, started) = http.json("/api/rooms/$id/action",
                """{"seat":0,"tk":"$tk","type":"start"}""")
            assertEquals(200, kcode)
            val sv = started["view"] as JsonObject
            assertEquals(10, (sv["seats"] as JsonArray)[0].jsonObject.intOf("cards"))
            val gv = sv["game"] as JsonObject
            assertEquals(10, (gv["yourHand"] as JsonArray).size)
            val first = (gv["legal"] as JsonArray).firstOrNull()?.jsonPrimitive?.content
            if (first != null) {
                val (pcode, played) = http.json("/api/rooms/$id/action",
                    """{"seat":0,"tk":"$tk","type":"play","card":"$first"}""")
                assertEquals(200, pcode)
                val g2 = (played["view"] as JsonObject)["game"] as JsonObject
                assertEquals(9, (g2["left"] as JsonArray)[0].jsonPrimitive.content.toInt())
            }
            // sair
            val out = http.json("/api/rooms/$id/action", """{"seat":1,"tk":"$tkB","type":"leave"}""")
            assertEquals(200, out.first)
        }
    }

    @Test
    fun pedidosMalFormadosDoExplicacao() {
        withServer { http, store ->
            http.json("/api/rooms", """{"name":"X","player":{"name":"Ana"}}""")
            val id = store.openRooms.first().id
            val (code, bad) = http.json("/api/rooms/$id/action", "isto não é json")
            assertEquals(400, code)
            assertEquals("json inválido", (bad["error"] as JsonPrimitive).content)
            val (c2, e2) = http.json("/api/rooms/NOPE/state?seat=0")
            assertEquals(404, c2)
            assertEquals("Essa mesa já não existe.", (e2["error"] as JsonPrimitive).content)
            val (c3, _) = http.json("/api/rooms/$id/action", """{"type":"ready"}""")
            assertEquals(409, c3)
            val (c4, _) = http.call("/api/rooms/$id/join", null, "GET")
            assertEquals(404, c4)     // join só existe em POST
        }
    }

    @Test
    fun oRelogioCorreComServidorAceso() {
        withServer { http, store ->
            val created = http.json("/api/rooms", """{"name":"Mesa","player":{"name":"Ana"}}""").second
            val id = (created["id"] as JsonPrimitive).content
            val tk = (created["tk"] as JsonPrimitive).content
            http.json("/api/rooms/$id/action", """{"seat":0,"tk":"$tk","type":"start"}""")
            val room = store.get(id)!!
            for (s in room.seats) s!!.bot = true
            room.botDelayMs = { 0 }
            val limite = System.currentTimeMillis() + 8_000
            while (room.hand!!.tricks.a + room.hand!!.tricks.b < 2 && System.currentTimeMillis() < limite)
                Thread.sleep(150)
            val vazas = room.hand!!.tricks.a + room.hand!!.tricks.b
            assertTrue("o relógio do servidor fez $vazas vazas", vazas >= 2)
            val st = http.json("/api/rooms/$id/state?seat=0&tk=$tk&since=0").second
            val plays = (st["events"] as JsonArray).count { (it.jsonObject["t"] as? JsonPrimitive)?.content == "play" }
            assertTrue("houve $plays jogadas no feed", plays >= 8)
        }
    }
}

private fun JsonObject.intOf(key: String): Int = (this[key] as JsonPrimitive).content.toInt()
