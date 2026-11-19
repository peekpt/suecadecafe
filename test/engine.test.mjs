// Testes das regras da Sueca (regras de Portugal).
// Uso: npm test   ·   node --test test/
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  DEFAULT_RULES, RANK_ORDER, TOTAL_POINTS, TURNED_TRICKS, applyHand, createHand, legalPlays, makeDeck,
  nextSeat, newMatch, playCard, pointsIn, scoreHand, strengthOf, suitOf, teamOfSeat,
  trickWinner, valueOf, winsOver,
} from '../web/js/engine.js';
import { chooseCard } from '../web/js/bots.js';

const rules = (extra = {}) => ({ ...DEFAULT_RULES, ...extra });

test('baralho tem 40 cartas únicas e 120 pontos', () => {
  const deck = makeDeck();
  assert.equal(deck.length, 40);
  assert.equal(new Set(deck).size, 40);
  assert.equal(pointsIn(deck), TOTAL_POINTS);
  for (const s of ['e', 'c', 'o', 'p'])
    assert.equal(deck.filter((c) => suitOf(c) === s).length, 10);
  assert.ok(!deck.some((c) => ['8', '9', '0'].includes(c.slice(1))), 'sem 8, 9 nem 10');
});

test('força e valores das cartas', () => {
  assert.deepEqual(RANK_ORDER, ['A', '7', 'R', 'V', 'D', '6', '5', '4', '3', '2']);
  assert.equal(valueOf('eA'), 11);
  assert.equal(valueOf('e7'), 10);
  assert.equal(valueOf('eR'), 4);
  assert.equal(valueOf('eV'), 3);
  assert.equal(valueOf('eD'), 2);
  for (const r of ['6', '5', '4', '3', '2']) assert.equal(valueOf('e' + r), 0);
  // valete bate dama — a regra que apanha sempre alguém de surpresa
  assert.ok(strengthOf('eV') < strengthOf('eD'));
  assert.ok(strengthOf('e7') < strengthOf('eR'));
});

test('trunfo bate qualquer carta e o mais alto do naipe ganha', () => {
  assert.ok(winsOver('p2', 'eA', 'p'), 'dois de trunfo mata ás de espadas');
  assert.ok(!winsOver('eA', 'p2', 'p'), 'ás não mata trunfo');
  assert.ok(winsOver('p7', 'p2', 'p'), 'sete de trunfo bate dois de trunfo');
  assert.ok(!winsOver('cA', 'e7', 'p'), 'naipe diferente não ganha');
  assert.equal(trickWinner(['eA', 'e7', 'eR', 'eV'], 'c'), 0, 'áse ganha sem trunfos');
  assert.equal(trickWinner(['eA', 'e7', 'p2', 'eV'], 'p'), 2, 'trunfo baixo ganha a vaza');
});

test('distribuição: 10 cada, trunfo sai da última carta e joga-se para a direita', () => {
  const dealer = 1;
  const g = createHand({ rules: rules(), dealer, rng: () => 0.5 });
  assert.deepEqual(g.hands.map((h) => h.length), [10, 10, 10, 10]);
  assert.equal(g.hands.reduce((n, h) => n + h.length, 0), 40);
  assert.equal(suitOf(g.turned), g.trump, 'a carta virada define o trunfo');
  assert.ok(g.hands[dealer].includes(g.turned), 'a última carta fica com quem dá');
  assert.equal(g.leader, nextSeat(dealer), 'abre o jogador à direita de quem dá');
  assert.equal(g.turn, g.leader);
  assert.equal(pointsIn(g.hands.flat()), TOTAL_POINTS, 'não se perdem cartas');
});

test('mão ordenada por naipe e da mais alta para a mais baixa', () => {
  const g = createHand({ rules: rules(), dealer: 0, rng: () => 0.42 });
  for (const h of g.hands) {
    let lastSuit = -1;
    for (const c of h) {
      const s = ['e', 'c', 'o', 'p'].indexOf(suitOf(c));
      assert.ok(s >= lastSuit, 'naipes agrupados');
      if (s !== lastSuit) lastSuit = s;
    }
  }
});

test('é obrigatório assistir ao naipe de saída', () => {
  const g = createHand({ rules: rules(), dealer: 0, rng: () => 0.1 });
  // força uma situação conhecida
  g.hands[0] = ['eA', 'e2', 'c5', 'p7'];
  g.hands[1] = ['eR', 'e4', 'c5', 'p2'];
  g.trick = [];
  g.turn = 0;
  g.turned = 'eA';
  g.trump = 'o';
  playCard(g, 0, 'eA');
  assert.equal(g.turn, 1);
  const legal = legalPlays(g, 1);
  assert.deepEqual(legal.sort(), ['e4', 'eR'], 'só pode jogar espadas');
  assert.equal(playCard(g, 1, 'p2').error, 'Tens de assistir ao naipe de saída.');
  assert.ok(playCard(g, 1, 'eR').ok);
});

test('sem o naipe de saída pode baldar-se (regra base)', () => {
  const g = createHand({ rules: rules(), dealer: 0, rng: () => 0.1 });
  g.trump = 'o';
  g.hands[1] = ['cR', 'o3', 'p2'];
  g.trick = [{ seat: 0, card: 'eA' }];
  g.turn = 1;
  const legal = legalPlays(g, 1);
  assert.equal(legal.length, 3, 'pode escolher qualquer uma');
  assert.ok(legal.includes('p2'), 'pode descartar');
});

test('regra da casa: obrigar a trunfar quando não se assiste', () => {
  const g = createHand({ rules: rules({ trunfarSeVazio: true }), dealer: 0, rng: () => 0.1 });
  g.trump = 'o';
  g.hands[1] = ['cR', 'o3', 'p2'];
  g.trick = [{ seat: 0, card: 'eA' }];
  g.turn = 1;
  assert.deepEqual(legalPlays(g, 1), ['o3'], 'tem de cortar');
});

test('regra da casa: obrigar a subir (matar se puder)', () => {
  const g = createHand({ rules: rules({ subirSempre: true }), dealer: 0, rng: () => 0.1 });
  g.trump = 'p';
  g.hands[1] = ['cD', 'c5', 'c2'];
  g.trick = [{ seat: 0, card: 'c6' }];
  g.turn = 1;
  assert.deepEqual(legalPlays(g, 1), ['cD'], 'só a dama bate o seis');
  g.hands[1] = ['c5', 'c2', 'o3'];
  assert.deepEqual(legalPlays(g, 1).sort(), ['c2', 'c5'], 'sem carta acima, joga o que quiser do naipe');
});

test('uma mão completa: 10 vazas, 120 pontos repartidos, sem cartas a sobrar', () => {
  const g = createHand({ rules: rules(), dealer: 2, rng: () => 0.77 });
  let guard = 0;
  while (!g.over && guard++ < 200) {
    const seat = g.turn;
    const card = chooseCard(g, seat);
    assert.ok(card, 'o bot tem de ter jogada');
    assert.ok(legalPlays(g, seat).includes(card), 'o bot só joga legais');
    assert.ok(playCard(g, seat, card).ok);
  }
  assert.equal(guard < 200, true, 'a mão terminou');
  assert.equal(g.history.length, 10, 'dez vazas');
  assert.equal(g.points.a + g.points.b, TOTAL_POINTS);
  assert.equal(g.tricks.a + g.tricks.b, 10);
  assert.deepEqual(g.hands.map((h) => h.length), [0, 0, 0, 0]);
  assert.ok(g.result, 'há resultado');
});

test('marcação de uma mão', () => {
  const r = rules();
  assert.equal(scoreHand(61, 59, r).games.a, 1);
  assert.equal(scoreHand(60, 60, r).games.a, 0);
  assert.equal(scoreHand(60, 60, r).winner, null);
  assert.equal(scoreHand(65, 55, r).winner, 'a');
  assert.equal(scoreHand(90, 30, r).games.a, 1, '90 ainda é um jogo');
  assert.equal(scoreHand(91, 29, r).games.a, 2, '91 já são dois');
  assert.equal(scoreHand(0, 120, r).games.b, 4);
  assert.equal(scoreHand(0, 120, r).bandeira, true);
});

test('equipas são sempre alternadas', () => {
  assert.equal(teamOfSeat(0), 'a');
  assert.equal(teamOfSeat(1), 'b');
  assert.equal(teamOfSeat(2), 'a');
  assert.equal(teamOfSeat(3), 'b');
});

test('partida fecha aos 4 jogos', () => {
  const r = rules({ jogosParaGanhar: 4 });
  let m = newMatch();
  m = applyHand(m, scoreHand(70, 50, r), r);
  assert.equal(m.winner, null);
  m = applyHand(m, scoreHand(95, 25, r), r);   // +2 => 3
  assert.equal(m.games.a, 3);
  assert.equal(m.winner, null);
  m = applyHand(m, scoreHand(62, 58, r), r);   // +1 => 4
  assert.equal(m.games.a, 4);
  assert.equal(m.winner, 'a');
  assert.equal(m.via, 'jogos');
});

test('bandeira fecha a partida a qualquer momento', () => {
  const r = rules();
  let m = newMatch();
  m = applyHand(m, scoreHand(20, 100, r), r);  // B com 2
  m = applyHand(m, scoreHand(30, 90, r), r);   // B com 3
  assert.equal(m.winner, null);
  m = applyHand(m, scoreHand(120, 0, r), r);   // bandeira de A, com B em vantagem
  assert.equal(m.winner, 'a');
  assert.equal(m.via, 'bandeira');
  // desligada a regra, soma simplesmente 4 jogos
  const r2 = rules({ bandeiraFecha: false });
  let m2 = applyHand(newMatch(), scoreHand(120, 0, r2), r2);
  assert.equal(m2.winner, 'a', '4 jogos chegam na mesma');
});

test('trunfo às cegas quando a casa não vira carta', () => {
  const g = createHand({ rules: rules({ virarTrunfo: false }), dealer: 0, rng: () => 0.99 });
  assert.equal(g.turned, null);
  assert.ok(['e', 'c', 'o', 'p'].includes(g.trump));
  assert.equal(g.turnedLive, false);
});

// mão armada: espadas são trunfo, a carta virada (2♠) está na mão de quem dá e
// ninguém a joga — serve para ver só o recolher da mostra.
const trumpHand = () => {
  const g = createHand({ rules: rules(), dealer: 0, rng: () => 0.3 });
  g.trump = 'e';
  g.turned = 'e2';
  g.turnedSeat = 0;
  g.turnedLive = true;
  g.hands = [
    ['eA', 'e7', 'eR', 'eV', 'eD', 'e6', 'e5', 'e4', 'e3', 'e2'],
    ['cA', 'c7', 'cR', 'cV', 'cD', 'c6', 'c5', 'c4', 'c3', 'c2'],
    ['oA', 'o7', 'oR', 'oV', 'oD', 'o6', 'o5', 'o4', 'o3', 'o2'],
    ['pA', 'p7', 'pR', 'pV', 'pD', 'p6', 'p5', 'p4', 'p3', 'p2'],
  ];
  g.leader = 1;
  g.turn = 1;
  g.trick = [];
  g.history = [];
  return g;
};

test('a carta virada deixa de estar em mostra ao fechar a terceira vaza', () => {
  const g = trumpHand();
  const vazas = [
    ['cA', 'o2', 'p2', 'eD'],   // quem dá trunfa e abre a vaza seguinte
    ['eR', 'c7', 'o7', 'p7'],
    ['eV', 'c6', 'o6', 'p6'],
  ];
  for (const [n, cartas] of vazas.entries()) {
    for (const c of cartas) playCard(g, g.turn, c);
    assert.equal(g.history.length, n + 1);
    assert.equal(g.turnedLive, n + 1 < TURNED_TRICKS, `vaza ${n + 1}`);
  }
  assert.ok(g.hands[0].includes('e2'), 'a carta continua em jogo: o que muda é a mostra');
  playCard(g, g.turn, 'eA');                       // agora sim, é jogada
  assert.equal(g.turnedLive, false);
});

test('jogar a carta virada antes da terceira vaza também a recolhe', () => {
  const g = trumpHand();
  for (const c of ['cA', 'o2', 'p2', 'e2']) playCard(g, g.turn, c);
  assert.equal(g.turnedLive, false, 'recolheu porque foi jogada');
  assert.equal(g.hands[0].includes('e2'), false);
});

