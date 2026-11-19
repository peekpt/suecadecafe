// Sueca — regras de Portugal. Motor puro (sem DOM): o servidor usa-o para
// distribuir e validar, os testes usam-no para verificar as regras, o browser
// usa só as etiquetas para desenhar as cartas.
//
// · Baralho de 40 cartas (sem 8, 9, 10): Ás, 7, Rei, Valete, Dama, 6, 5, 4, 3, 2.
// · Força dentro do naipe: Ás > 7 > Rei > Valete > Dama > 6 > 5 > 4 > 3 > 2.
// · Pontos: Ás 11, 7 10, Rei 4, Valete 3, Dama 2; as outras não contam. Total = 120.
// · Quatro jogadores em duas equipas: lugares 0 e 2 contra lugares 1 e 3, sempre
//   alternados. Joga-se no sentido contrário ao dos ponteiros do relógio.
// · Dão-se 10 cartas a cada jogador (dois lotes de 5, começando no parceiro da
//   direita de quem dá). A última carta fica virada para cima e marca o trunfo.
// · Quem abre a vaza define o naipe; os outros têm de assistir se tiverem cartas
//   desse naipe. Sem o naipe, pode trunfar ou baldar.
// · Ganha a vaza o trunfo mais alto ou, sem trunfos, a carta mais alta do naipe de saída.
// · Pontos de uma mão passam para a equipa que ganha a vaza. 61-90 = 1 jogo,
//   91-119 = 2 jogos, 120 = bandeira (4 jogos e fecha a partida), 60-60 ninguém marca.

export const TOTAL_POINTS = 120;

// Regra da casa: a carta virada deixa de estar em mostra quando se fecham três
// vazas — a essa altura quase todos já decoraram o trunfo e ela só atrapalha o
// centro da mesa. Se for jogada antes, recolhe-se nessa altura.
export const TURNED_TRICKS = 3;

export const SUIT_KEYS = ['e', 'c', 'o', 'p'];

export const SUIT_INFO = {
  e: { name: 'Espadas', glyph: '♠', dark: true },
  c: { name: 'Copas', glyph: '♥', dark: false },
  o: { name: 'Ouros', glyph: '♦', dark: false },
  p: { name: 'Paus', glyph: '♣', dark: true },
};

// ordem de força — índice menor bate índice maior
export const RANK_ORDER = ['A', '7', 'R', 'V', 'D', '6', '5', '4', '3', '2'];

export const RANK_INFO = {
  A: { name: 'Ás', label: 'A', value: 11 },
  '7': { name: 'Sete', label: '7', value: 10 },
  R: { name: 'Rei', label: 'R', value: 4 },
  V: { name: 'Valete', label: 'V', value: 3 },
  D: { name: 'Dama', label: 'D', value: 2 },
  '6': { name: 'Seis', label: '6', value: 0 },
  '5': { name: 'Cinco', label: '5', value: 0 },
  '4': { name: 'Quatro', label: '4', value: 0 },
  '3': { name: 'Três', label: '3', value: 0 },
  '2': { name: 'Dois', label: '2', value: 0 },
};

export const TEAMS = {
  a: { key: 'a', name: 'Bica', seats: [0, 2], color: '#f0a63c' },
  b: { key: 'b', name: 'Galão', seats: [1, 3], color: '#5b8bd9' },
};

export const DEFAULT_RULES = {
  virarTrunfo: true,   // a última carta distribuída vai para o centro e marca o trunfo
  trunfarSeVazio: false, // regra da casa: quem não assista é obrigado a trunfar se puder
  subirSempre: false,  // regra da casa: é obrigado a tentar ganhar a vaza quando pode
  jogosParaGanhar: 4,  // partida a 4 jogos
  bandeiraFecha: true, // a bandeira fecha a partida seja qual for o marcador
};

export const suitOf = (card) => card[0];
export const rankOf = (card) => card.slice(1);
export const valueOf = (card) => RANK_INFO[rankOf(card)].value;
export const strengthOf = (card) => RANK_ORDER.indexOf(rankOf(card)); // 0 = mais forte
export const teamOfSeat = (seat) => (seat % 2 === 0 ? 'a' : 'b');
export const nextSeat = (seat) => (seat + 1) % 4;
export const seatFrom = (seat, from) => (seat - from + 4) % 4;

export function cardName(card) {
  return `${RANK_INFO[rankOf(card)].name} de ${SUIT_INFO[suitOf(card)].name.toLowerCase()}`;
}

export function makeDeck() {
  const deck = [];
  for (const s of SUIT_KEYS) for (const r of RANK_ORDER) deck.push(s + r);
  return deck;
}

export function shuffle(list, rng = Math.random) {
  const a = list.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

// Ordena a mão por naipe e, dentro do naipe, da carta mais alta para a mais baixa.
const SUIT_DISPLAY = { e: 0, c: 1, o: 2, p: 3 };
export function sortHand(hand) {
  return hand.slice().sort((x, y) =>
    SUIT_DISPLAY[suitOf(x)] - SUIT_DISPLAY[suitOf(y)] || strengthOf(x) - strengthOf(y));
}

// a) a carta vence mesmo? b) quem ganha a vaza?
export function winsOver(card, best, trump) {
  if (!best) return true;
  const tA = suitOf(card) === trump;
  const tB = suitOf(best) === trump;
  if (tA !== tB) return tA;
  if (tA) return strengthOf(card) < strengthOf(best);
  return suitOf(card) === suitOf(best) && strengthOf(card) < strengthOf(best);
}

export function trickWinner(plays, trump) {
  let w = 0;
  for (let i = 1; i < plays.length; i++) {
    const card = plays[i] && plays[i].card !== undefined ? plays[i].card : plays[i];
    const best = plays[w] && plays[w].card !== undefined ? plays[w].card : plays[w];
    if (card && winsOver(card, best, trump)) w = i;
  }
  return w;
}

export const pointsIn = (cards) => cards.reduce((s, c) => s + valueOf(c), 0);

// ---------- uma mão (10 vazas) ----------
export function createHand({ rules = DEFAULT_RULES, dealer = 0, rng = Math.random } = {}) {
  const deck = shuffle(makeDeck(), rng);
  const hands = [[], [], [], []];
  // dois lotes de 5, começando no jogador à direita de quem dá
  const order = [nextSeat(dealer), nextSeat(nextSeat(dealer)), nextSeat(nextSeat(nextSeat(dealer))), dealer];
  let i = 0;
  for (let batch = 0; batch < 2; batch++)
    for (const seat of order)
      for (let k = 0; k < 5; k++) hands[seat].push(deck[i++]);

  let trump;
  let turned = null;
  if (rules.virarTrunfo) {
    turned = deck[deck.length - 1];  // a última carta é sempre de quem dá
    trump = suitOf(turned);
  } else {
    trump = SUIT_KEYS[Math.floor(rng() * 4) % 4];
  }

  const leader = nextSeat(dealer);
  return {
    rules: { ...DEFAULT_RULES, ...rules },
    dealer,
    leader,
    turn: leader,
    trump,
    turned,
    turnedSeat: turned ? dealer : null,
    turnedLive: !!turned,           // fica na mesa até quem a tem a jogar
    hands: hands.map(sortHand),
    trick: [],                      // [{ seat, card }]
    points: { a: 0, b: 0 },
    tricks: { a: 0, b: 0 },
    history: [],                    // vazas fechadas
    over: false,
    result: null,
    events: [],                     // para animações e para o registo da mão
  };
}

export function cardsLeft(g, seat) {
  return g.hands[seat] ? g.hands[seat].length : 0;
}

export function leadSuit(g) {
  return g.trick.length ? suitOf(g.trick[0].card) : null;
}

// Carta mais forte jogada até agora nesta vaza.
function bestPlay(g) {
  if (!g.trick.length) return null;
  return g.trick[trickWinner(g.trick, g.trump)];
}

// Cartas que o lugar pode legalmente jogar agora.
export function legalPlays(g, seat) {
  if (g.over || seat !== g.turn) return [];
  const hand = g.hands[seat];
  if (!g.trick.length) return hand.slice();

  const lead = suitOf(g.trick[0].card);
  const matching = hand.filter((c) => suitOf(c) === lead);
  let pool = matching;
  if (!pool.length) {
    pool = hand.slice();
    if (g.rules.trunfarSeVazio) {
      const trumps = pool.filter((c) => suitOf(c) === g.trump);
      if (trumps.length) pool = trumps;
    }
  }
  if (g.rules.subirSempre) {
    const best = bestPlay(g);
    const beating = pool.filter((c) => winsOver(c, best.card, g.trump));
    if (beating.length) pool = beating;
  }
  return pool;
}

export function playCard(g, seat, card) {
  if (g.over) return { error: 'A mão já terminou.' };
  if (seat !== g.turn) return { error: 'Ainda não é a tua vez.' };
  if (!g.hands[seat].includes(card)) return { error: 'Essa carta não está na tua mão.' };
  if (!legalPlays(g, seat).includes(card)) return { error: 'Tens de assistir ao naipe de saída.' };

  const rules = g.rules;
  g.hands[seat] = g.hands[seat].filter((c) => c !== card);
  g.trick.push({ seat, card });

  if (g.turnedLive && card === g.turned) {
    g.turnedLive = false;
    g.events.push({ t: 'turnedPlayed', seat, card });
  }

  if (g.trick.length < 4) {
    g.turn = nextSeat(seat);
    g.events.push({ t: 'play', seat, card });
    return { ok: true, pending: true };
  }

  const wIdx = trickWinner(g.trick.map((p) => p.card), g.trump);
  const wSeat = g.trick[wIdx].seat;
  const team = teamOfSeat(wSeat);
  const cards = g.trick.map((p) => p.card);
  const pts = pointsIn(cards);
  g.points[team] += pts;
  g.tricks[team] += 1;
  g.history.push({ winner: wSeat, team, cards, points: pts, plays: g.trick.map((p) => ({ seat: p.seat, card: p.card })) });
  g.events.push({ t: 'trick', winner: wSeat, team, points: pts, cards });
  g.trick = [];

  if (g.turnedLive && g.history.length >= TURNED_TRICKS) {
    g.turnedLive = false;
    g.events.push({ t: 'turnedTaken', seat: g.turnedSeat, trick: g.history.length });
  }

  if (g.hands.every((h) => h.length === 0)) {
    g.over = true;
    g.result = scoreHand(g.points.a, g.points.b, rules, g.tricks);
    g.events.push({ t: 'hand', result: g.result });
    return { ok: true, finished: true, result: g.result };
  }
  g.leader = wSeat;
  g.turn = wSeat;
  return { ok: true, finishedTrick: true, winner: wSeat, points: pts };
}

// ---------- marcação ----------
export function scoreHand(pa, pb, rules = DEFAULT_RULES, tricks = { a: 0, b: 0 }) {
  const games = { a: 0, b: 0 };
  const leader = pa === pb ? null : pa > pb ? 'a' : 'b';
  const winnerPoints = leader ? (leader === 'a' ? pa : pb) : 0;
  let label = 'Empate a 60 — ninguém marca';
  let kind = 'empate';
  if (leader) {
    if (winnerPoints === TOTAL_POINTS) {
      games[leader] = 4;
      label = rules.bandeiraFecha ? 'Bandeira — fecha a partida' : 'Bandeira — 4 jogos';
      kind = 'bandeira';
    } else if (winnerPoints >= 91) {
      games[leader] = 2;
      label = `${winnerPoints} pontos — 2 jogos`;
      kind = 'dois jogos';
    } else {
      games[leader] = 1;
      label = `${winnerPoints} pontos — 1 jogo`;
      kind = 'um jogo';
    }
  }
  return {
    points: { a: pa, b: pb },
    tricks,
    games,
    winner: leader,
    kind,
    label,
    bandeira: kind === 'bandeira',
  };
}

export function newMatch() {
  return { games: { a: 0, b: 0 }, hands: [], winner: null, via: null };
}

export function applyHand(match, result, rules = DEFAULT_RULES) {
  const next = {
    games: { a: match.games.a + result.games.a, b: match.games.b + result.games.b },
    hands: [...match.hands, result],
    winner: null,
    via: null,
  };
  const target = rules.jogosParaGanhar;
  if (result.bandeira && rules.bandeiraFecha && result.winner) {
    next.winner = result.winner;
    next.via = 'bandeira';
  } else if (next.games.a >= target && next.games.a > next.games.b) {
    next.winner = 'a';
    next.via = 'jogos';
  } else if (next.games.b >= target && next.games.b > next.games.a) {
    next.winner = 'b';
    next.via = 'jogos';
  }
  return next;
}

// ---------- texto para o registo ----------
export function describeTrick(hand, trump) {
  const w = trickWinner(hand.map((p) => p.card), trump);
  return `${hand[w].card ? cardName(hand[w].card) : ''} ganhou`;
}

export function trumpName(trump) {
  return SUIT_INFO[trump].name;
}
