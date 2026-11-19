package com.sueca.cafe

import org.junit.Assert.*
import org.junit.Test

// Testes das regras da Sueca — espelho do engine.test.mjs da app web.

class EngineTest {

    private fun fixedRng(v: Double) = { v }

    @Test fun `baralho tem 40 cartas unicas e 120 pontos`() {
        val deck = makeDeck()
        assertEquals(40, deck.size)
        assertEquals(40, deck.toSet().size)
        assertEquals(TOTAL_POINTS, pointsIn(deck))
        for (s in listOf("e", "c", "o", "p"))
            assertEquals(10, deck.count { suitOf(it) == s })
        assertFalse(deck.any { listOf("8", "9", "10").contains(rankOf(it)) })
    }

    @Test fun `forca e valores das cartas`() {
        assertEquals(listOf("A", "7", "R", "V", "D", "6", "5", "4", "3", "2"), RANK_ORDER)
        assertEquals(11, valueOf("eA")); assertEquals(10, valueOf("e7"))
        assertEquals(4, valueOf("eR")); assertEquals(3, valueOf("eV")); assertEquals(2, valueOf("eD"))
        for (r in listOf("6", "5", "4", "3", "2")) assertEquals(0, valueOf("e$r"))
        assertTrue(strengthOf("eV") < strengthOf("eD"))   // valete bate dama
        assertTrue(strengthOf("e7") < strengthOf("eR"))
    }

    @Test fun `trunfo bate qualquer carta e a mais alta do naipe ganha`() {
        assertTrue(winsOver("p2", "eA", "p"))
        assertFalse(winsOver("eA", "p2", "p"))
        assertTrue(winsOver("p7", "p2", "p"))
        assertFalse(winsOver("cA", "e7", "p"))
        assertEquals(0, trickWinner(listOf("eA", "e7", "eR", "eV"), "c"))
        assertEquals(2, trickWinner(listOf("eA", "e7", "p2", "eV"), "p"))
    }

    @Test fun `distribuicao - 10 cada, trunfo da ultima carta, abre a direita`() {
        val dealer = 1
        val g = createHand(Rules(), dealer, fixedRng(0.5))
        assertEquals(listOf(10, 10, 10, 10), g.hands.map { it.size })
        assertEquals(40, g.hands.sumOf { it.size })
        assertEquals(g.trump, suitOf(g.turned!!))
        assertTrue(g.hands[dealer].contains(g.turned))
        assertEquals(nextSeat(dealer), g.leader)
        assertEquals(g.leader, g.turn)
        assertEquals(TOTAL_POINTS, pointsIn(g.hands.flatten()))
    }

    @Test fun `mao ordenada por naipe e da mais alta para a mais baixa`() {
        val g = createHand(Rules(), 0, fixedRng(0.42))
        for (h in g.hands) {
            var lastSuit = -1
            for (c in h) {
                val s = listOf("e", "c", "o", "p").indexOf(suitOf(c))
                assertTrue("naipes agrupados", s >= lastSuit)
                if (s != lastSuit) lastSuit = s
            }
        }
    }

    @Test fun `e obrigatorio assistir ao naipe de saida`() {
        val g = createHand(Rules(), 0, fixedRng(0.1))
        setHand(g, 0, listOf("eA", "e2", "c5", "p7"))
        setHand(g, 1, listOf("eR", "e4", "c5", "p2"))
        g.trick.clear(); g.turn = 0; g.turned = "eA"; g.trump = "o"
        assertTrue(playCard(g, 0, "eA") is PlayResult.Pending)
        assertEquals(1, g.turn)
        assertEquals(listOf("e4", "eR"), legalPlays(g, 1).sorted())
        assertTrue(playCard(g, 1, "p2").let { it is PlayResult.Error && it.message == "Tens de assistir ao naipe de saída." })
        assertTrue(playCard(g, 1, "eR") is PlayResult.Pending)
    }

    @Test fun `sem o naipe de saida pode baldar-se`() {
        val g = createHand(Rules(), 0, fixedRng(0.1))
        g.trump = "o"
        setHand(g, 1, listOf("cR", "o3", "p2"))
        g.trick.clear(); g.trick.add(Play(0, "eA")); g.turn = 1
        assertEquals(3, legalPlays(g, 1).size)
        assertTrue(legalPlays(g, 1).contains("p2"))
    }

    @Test fun `regra da casa obrigar a trunfar`() {
        val g = createHand(Rules(trunfarSeVazio = true), 0, fixedRng(0.1))
        g.trump = "o"
        setHand(g, 1, listOf("cR", "o3", "p2"))
        g.trick.clear(); g.trick.add(Play(0, "eA")); g.turn = 1
        assertEquals(listOf("o3"), legalPlays(g, 1))
    }

    @Test fun `regra da casa obrigar a subir`() {
        val g = createHand(Rules(subirSempre = true), 0, fixedRng(0.1))
        g.trump = "p"
        setHand(g, 1, listOf("cD", "c5", "c2"))
        g.trick.clear(); g.trick.add(Play(0, "c6")); g.turn = 1
        assertEquals(listOf("cD"), legalPlays(g, 1))
        setHand(g, 1, listOf("c5", "c2", "o3"))
        assertEquals(listOf("c2", "c5"), legalPlays(g, 1).sorted())
    }

    @Test fun `uma mao completa - 10 vazas 120 pontos sem cartas a sobrar`() {
        val g = createHand(Rules(), 2, fixedRng(0.77))
        var guard = 0
        while (!g.over && guard++ < 200) {
            val seat = g.turn
            val card = chooseCard(g, seat)
            assertNotNull("o bot tem de ter jogada", card)
            assertTrue("o bot só joga legais", card!! in legalPlays(g, seat))
            assertTrue(playCard(g, seat, card) !is PlayResult.Error)
        }
        assertTrue(guard < 200)
        assertEquals(10, g.history.size)
        assertEquals(TOTAL_POINTS, g.points.a + g.points.b)
        assertEquals(10, g.tricks.a + g.tricks.b)
        assertEquals(listOf(0, 0, 0, 0), g.hands.map { it.size })
        assertNotNull(g.result)
    }

    @Test fun `marcacao de uma mao`() {
        val r = Rules()
        assertEquals(1, scoreHand(61, 59, r, 0, 0).gamesA)
        assertEquals(0, scoreHand(60, 60, r, 0, 0).gamesA)
        assertNull(scoreHand(60, 60, r, 0, 0).winner)
        assertEquals("a", scoreHand(65, 55, r, 0, 0).winner)
        assertEquals(1, scoreHand(90, 30, r, 0, 0).gamesA)
        assertEquals(2, scoreHand(91, 29, r, 0, 0).gamesA)
        assertEquals(4, scoreHand(0, 120, r, 0, 0).gamesB)
        assertTrue(scoreHand(0, 120, r, 0, 0).bandeira)
    }

    @Test fun `equipas sao sempre alternadas`() {
        assertEquals("a", teamOfSeat(0)); assertEquals("b", teamOfSeat(1))
        assertEquals("a", teamOfSeat(2)); assertEquals("b", teamOfSeat(3))
    }

    @Test fun `partida fecha aos 4 jogos`() {
        val r = Rules(jogosParaGanhar = 4)
        var m = newMatch()
        m = applyHand(m, scoreHand(70, 50, r, 0, 0), r); assertNull(m.winner)
        m = applyHand(m, scoreHand(95, 25, r, 0, 0), r)
        assertEquals(3, m.gamesA); assertNull(m.winner)
        m = applyHand(m, scoreHand(62, 58, r, 0, 0), r)
        assertEquals(4, m.gamesA)
        assertEquals("a", m.winner); assertEquals("jogos", m.via)
    }

    @Test fun `bandeira fecha a partida a qualquer momento`() {
        val r = Rules()
        var m = applyHand(newMatch(), scoreHand(20, 100, r, 0, 0), r)
        m = applyHand(m, scoreHand(30, 90, r, 0, 0), r)
        assertNull(m.winner)
        m = applyHand(m, scoreHand(120, 0, r, 0, 0), r)
        assertEquals("a", m.winner); assertEquals("bandeira", m.via)
        val r2 = Rules(bandeiraFecha = false)
        val m2 = applyHand(newMatch(), scoreHand(120, 0, r2, 0, 0), r2)
        assertEquals("a", m2.winner)
    }

    @Test fun `trunfo as cegas quando a casa nao vira carta`() {
        val g = createHand(Rules(virarTrunfo = false), 0, fixedRng(0.99))
        assertNull(g.turned)
        assertTrue(listOf("e", "c", "o", "p").contains(g.trump))
        assertFalse(g.turnedLive)
    }

    // mão armada: espadas trunfo, virada 2♠ na mão de quem dá
    private fun trumpHand(): GameState {
        val g = createHand(Rules(), 0, fixedRng(0.3))
        g.trump = "e"; g.turned = "e2"; g.turnedSeat = 0; g.turnedLive = true
        setHand(g, 0, listOf("eA", "e7", "eR", "eV", "eD", "e6", "e5", "e4", "e3", "e2"))
        setHand(g, 1, listOf("cA", "c7", "cR", "cV", "cD", "c6", "c5", "c4", "c3", "c2"))
        setHand(g, 2, listOf("oA", "o7", "oR", "oV", "oD", "o6", "o5", "o4", "o3", "o2"))
        setHand(g, 3, listOf("pA", "p7", "pR", "pV", "pD", "p6", "p5", "p4", "p3", "p2"))
        g.leader = 1; g.turn = 1; g.trick.clear(); g.history.clear()
        return g
    }

    @Test fun `carta virada sai da mostra ao fechar a terceira vaza`() {
        val g = trumpHand()
        val vazas = listOf(
            listOf("cA", "o2", "p2", "eD"),
            listOf("eR", "c7", "o7", "p7"),
            listOf("eV", "c6", "o6", "p6"),
        )
        for ((n, cartas) in vazas.withIndex()) {
            for (c in cartas) playCard(g, g.turn, c)
            assertEquals(n + 1, g.history.size)
            assertEquals("vaza ${n + 1}", n + 1 < TURNED_TRICKS, g.turnedLive)
        }
        assertTrue(g.hands[0].contains("e2"))
        playCard(g, g.turn, "eA")
        assertFalse(g.turnedLive)
    }

    @Test fun `jogar a carta virada antes da terceira vaza tambem a recolhe`() {
        val g = trumpHand()
        for (c in listOf("cA", "o2", "p2", "e2")) playCard(g, g.turn, c)
        assertFalse(g.turnedLive)
        assertFalse(g.hands[0].contains("e2"))
    }

    private fun setHand(g: GameState, seat: Int, cards: List<String>) {
        g.hands[seat].clear(); g.hands[seat].addAll(cards)
    }
}
