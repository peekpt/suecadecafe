// "Sueca no Café" — anunciador de mesas e árbitro do jogo para a rede Wi-Fi local.
// Uso: node server.mjs   (porta 8743; muda com PORT=...)
// Uma máquina da rede corre isto; os outros abrem http://<ip>:8743, escolhem mesa
// e equipa. O servidor distribui as cartas e valida cada jogada — cada telemóvel
// só vê a própria mão.
import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, normalize, sep } from 'node:path';
import { networkInterfaces } from 'node:os';
import { fileURLToPath } from 'node:url';
import {
  DEFAULT_RULES, applyHand, createHand, legalPlays, newMatch, playCard, teamOfSeat,
} from './web/js/engine.js';
import { botDelay, chooseCard } from './web/js/bots.js';

const ROOT = join(fileURLToPath(new URL('.', import.meta.url)), 'web');
const PORT = Number(process.env.PORT || 8743);
const ROOM_TTL = 60 * 60 * 1000;      // uma mesa sem ninguém durante uma hora morre
const LOBBY_TTL = 45 * 1000;          // lugar na sala de espera sem sinais → liberta a cadeira
const GAME_TTL = 150 * 1000;          // em jogo, quem desaparece é tapado pelo parceiro automático
const NEXT_HAND_MS = 14 * 1000;       // pausa entre mãos
const MAX_ROOMS = 32;
const FEED_MAX = 80;

const rooms = new Map();
const CODE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
function newId() {
  let id;
  do { id = Array.from({ length: 4 }, () => CODE_CHARS[(Math.random() * CODE_CHARS.length) | 0]).join(''); }
  while (rooms.has(id));
  return id;
}
const newToken = () => Math.random().toString(36).slice(2, 10);

function lanIp() {
  for (const list of Object.values(networkInterfaces()))
    for (const n of list || [])
      if (n.family === 'IPv4' && !n.internal) return n.address;
  return '127.0.0.1';
}

const clean = (s, max) => String(s ?? '').replace(/\s+/g, ' ').trim().slice(0, max);
const bool = (v, dflt) => (v === undefined || v === null ? dflt : !!v);

const MIME = {
  '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8', '.mjs': 'text/javascript; charset=utf-8',
  '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.woff2': 'font/woff2', '.ico': 'image/x-icon',
};
const CORS = {
  'access-control-allow-origin': '*',
  'access-control-allow-methods': 'GET, POST, OPTIONS',
  'access-control-allow-headers': 'content-type',
};
function sendJson(res, code, obj) {
  res.writeHead(code, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', ...CORS });
  res.end(JSON.stringify(obj));
}
function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', (c) => { data += c; if (data.length > 100000) { reject(new Error('body grande')); req.destroy(); } });
    req.on('end', () => { try { resolve(data ? JSON.parse(data) : {}); } catch { reject(new Error('json inválido')); } });
    req.on('error', reject);
  });
}

// ---------- mesas ----------
function feed(room, ev) {
  room.seq++;
  room.feed.push({ seq: room.seq, at: Date.now(), ...ev });
  if (room.feed.length > FEED_MAX) room.feed.splice(0, room.feed.length - FEED_MAX);
}

function makeRoom({ name, player }) {
  const now = Date.now();
  const room = {
    id: newId(),
    name: clean(name, 24) || 'Mesa de café',
    status: 'lobby',                 // lobby | playing | over | closed
    host: 0,
    seats: [null, null, null, null], // lugar 0 e 2 = Equipa Bica · 1 e 3 = Equipa Galão
    rules: { ...DEFAULT_RULES },
    fillBots: true,
    hand: null,
    match: newMatch(),
    dealer: 0,
    handNo: 0,
    nextHandAt: 0,
    botAt: 0,
    feed: [],
    seq: 0,
    created: now,
    startedAt: 0,
  };
  addSeat(room, { ...player, team: 'a' });
  return room;
}

// Lugares por equipa: o anfitrião fica no 0 (em baixo, para ele); o parceiro
// entra no 2 (frente a frente) e os adversários nos 1 e 3.
function freeSeatFor(room, team) {
  const wanted = team === 'b' ? [1, 3] : [0, 2];
  return wanted.find((s) => !room.seats[s]) ?? -1;
}
function addSeat(room, player) {
  const want = player.team === 'b' ? 'b' : 'a';
  const seat = freeSeatFor(room, want);
  if (seat < 0) return null;
  const team = teamOfSeat(seat);   // a equipa resulta do lugar: 0/2 contra 1/3
  room.seats[seat] = {
    seat, team,
    name: clean(player.name, 14) || 'Jogador',
    avatar: clean(player.avatar, 12) || 'cafe',
    bot: !!player.bot,
    tk: player.bot ? null : newToken(),
    online: !player.bot,
    away: false,                   // humano que saiu: a cadeira e a mão ficam à espera dele
    ready: !!player.bot,
    lastSeen: Date.now(),
  };
  return room.seats[seat];
}

function startHand(room) {
  room.handNo++;
  room.hand = createHand({ rules: room.rules, dealer: room.dealer });
  room.status = 'playing';
  room.nextHandAt = 0;
  room.botAt = 0;
  feed(room, { t: 'deal', handNo: room.handNo, dealer: room.dealer, trump: room.hand.trump, turned: room.hand.turned });
}

// Fecho de uma mão: marca jogos, decide a partida e põe o relógio da mão seguinte.
function settleHand(room) {
  const g = room.hand;
  room.match = applyHand(room.match, g.result, room.rules);
  if (room.match.winner) {
    room.status = 'over';
    room.nextHandAt = 0;
  } else {
    room.nextHandAt = Date.now() + NEXT_HAND_MS;
  }
  feed(room, {
    t: 'hand', result: g.result, match: room.match.games,
    winner: room.match.winner, via: room.match.via, at: room.nextHandAt,
  });
}

function closeRoom(room, reason = '') {
  if (room.status === 'closed') return;
  room.status = 'closed';
  room.closedAt = Date.now();
  feed(room, { t: 'closed', reason });
}

function humanOnline(room) {
  return room.seats.some((s) => s && !s.bot && s.online);
}

// Quem sai (ou desaparece) a meio deixa a cadeira guardada: o parceiro automático
// joga por ele, a mão continua lá, e o mesmo token volta a tomar o lugar. Se era o
// anfitrião, a coroa passa para outro humano — senão ninguém podia decidir a mesa.
function handToBot(room, seat, timeout = false) {
  const s = room.seats[seat];
  if (!s || s.bot) return null;
  s.online = false;
  s.bot = true;
  s.away = true;
  if (room.host === seat) {
    const next = room.seats.find((x) => x && !x.bot && x.seat !== seat);
    if (next) {
      room.host = next.seat;
      feed(room, { t: 'host', seat: next.seat, name: next.name });
    }
  }
  feed(room, { t: 'gone', seat, name: s.name, timeout, host: room.host });
  feed(room, { t: 'botIn', seat, timeout });
  return s;
}

function botsNeeded(room) {
  return room.seats.some((s) => !s);
}

function startMatch(room) {
  for (let s = 0; s < 4; s++) {
    if (!room.seats[s]) {
      const team = teamOfSeat(s);
      addSeat(room, { seat: s, team, name: `Parceiro ${s + 1}`, avatar: 'd0:0', bot: true });
      feed(room, { t: 'botIn', seat: s });
    }
  }
  room.match = newMatch();
  room.handNo = 0;
  room.hand = null;
  room.startedAt = Date.now();
  startHand(room);
}

// ---------- estado visto por um lugar ----------
function seatSummary(room, seat) {
  const s = room.seats[seat];
  if (!s) return null;
  return {
    seat: s.seat, team: s.team, name: s.name, avatar: s.avatar,
    bot: s.bot, online: s.online, ready: s.ready, away: !!s.away,
    cards: room.hand ? room.hand.hands[seat].length : 0,
    host: room.host === seat,
  };
}

function viewFor(room, seat) {
  const g = room.hand;
  const you = room.seats[seat];
  const out = {
    app: 'sueca-no-cafe',
    id: room.id,
    name: room.name,
    status: room.status,
    rules: room.rules,
    fillBots: room.fillBots,
    seat,
    you: you ? { seat, name: you.name, avatar: you.avatar, team: you.team, bot: you.bot } : null,
    seats: room.seats.map((s, i) => seatSummary(room, i)),
    match: { games: room.match.games, winner: room.match.winner, via: room.match.via, hands: room.match.hands.length },
    handNo: room.handNo,
    host: room.host,
  };
  if (!g) return out;

  const isWatcher = !you;
  out.game = {
    dealer: g.dealer,
    turn: g.turn,
    leader: g.leader,
    trump: g.trump,
    turned: g.turned,
    turnedLive: g.turnedLive,
    turnedSeat: g.turnedSeat,
    trick: g.trick.map((p) => ({ seat: p.seat, card: p.card })),
    lastTrick: g.history.length ? g.history[g.history.length - 1] : null,
    points: g.points,
    tricks: g.tricks,
    over: g.over,
    result: g.over ? g.result : null,
    left: g.hands.map((h) => h.length),
    yourHand: isWatcher ? [] : g.hands[seat],
    legal: isWatcher || g.over ? [] : legalPlays(g, seat),
    yourTurn: !isWatcher && !g.over && g.turn === seat,
    // só informação pública: as cartas já jogadas são de todos
    history: g.history.map((h) => ({ winner: h.winner, team: h.team, points: h.points, plays: h.plays })),
  };
  return out;
}

// ---------- ações ----------
function action(room, seat, body) {
  const s = room.seats[seat];
  if (!s) return { error: 'Lugar livre — entra na mesa primeiro.' };
  const type = body.type;

  if (type === 'ready') { s.ready = bool(body.value, !s.ready); feed(room, { t: 'ready', seat, value: s.ready }); return { ok: true }; }
  if (type === 'team') {
    if (room.status !== 'lobby') return { error: 'A partida já começou.' };
    if (s.ready) return { error: 'Tira o "estou dentro" primeiro.' };
    const want = body.value === 'b' ? 'b' : 'a';
    if (teamOfSeat(seat) === want) return { ok: true };
    const free = freeSeatFor(room, want);
    if (free < 0) return { error: 'Essa equipa já tem os dois jogadores.' };
    room.seats[seat] = null;
    room.seats[free] = { ...s, seat: free, team: teamOfSeat(free) };
    if (room.host === seat) room.host = free;
    feed(room, { t: 'team', seat: free });
    return { ok: true, seat: free };
  }
  if (type === 'chat' || type === 'emote') {
    const text = clean(body.text, 60);
    if (!text && type === 'chat') return { error: 'vazio' };
    feed(room, { t: type, seat, text });
    return { ok: true };
  }
  if (type === 'rules') {
    if (room.host !== seat) return { error: 'Só o anfitrião muda as regras.' };
    if (room.status !== 'lobby') return { error: 'As regras só se combinam antes de começar.' };
    const key = body.key;
    if (key === 'jogosParaGanhar') room.rules[key] = [1, 2, 4, 6, 10].includes(Number(body.value)) ? Number(body.value) : 4;
    else if (key === 'fillBots') room.fillBots = bool(body.value, true);
    else if (['virarTrunfo', 'trunfarSeVazio', 'subirSempre', 'bandeiraFecha'].includes(key)) room.rules[key] = bool(body.value, room.rules[key]);
    else return { error: 'regra desconhecida' };
    feed(room, { t: 'rules', key });
    return { ok: true, rules: room.rules, fillBots: room.fillBots };
  }
  if (type === 'start') {
    if (room.host !== seat) return { error: 'Só o anfitrião começa o jogo.' };
    if (room.status !== 'lobby') return { error: 'A mesa já está em jogo.' };
    // com os parceiros automáticos ligados pode jogar-se sozinho contra a mesa toda
    if (!room.fillBots && botsNeeded(room)) return { error: 'Faltam jogadores para as duas equipas.' };
    startMatch(room);
    feed(room, { t: 'start' });
    return { ok: true };
  }
  if (type === 'next') {
    if (!room.hand || !room.hand.over) return { error: 'Ainda não acabaste esta mão.' };
    if (room.match.winner) return { error: 'A partida já acabou.' };
    room.dealer = (room.dealer + 1) % 4;
    startHand(room);
    return { ok: true };
  }
  if (type === 'rematch') {
    if (room.host !== seat) return { error: 'Só o anfitrião reinicia.' };
    room.dealer = (room.dealer + 1) % 4;
    startMatch(room);
    feed(room, { t: 'start', rematch: true });
    return { ok: true };
  }
  if (type === 'play') {
    const g = room.hand;
    if (!g || g.over) return { error: 'Não há mão a decorrer.' };
    if (s.bot) return { error: 'Esse lugar é automático.' };
    const res = playCard(g, seat, String(body.card || ''));
    if (res.error) return { error: res.error };
    feed(room, { t: 'play', seat, card: body.card, trump: g.trump });
    if (res.finished) settleHand(room);
    return { ok: true };
  }
  if (type === 'leave') {
    if (room.status === 'lobby') {
      // na sala de espera a cadeira fica logo livre para quem quiser entrar
      feed(room, { t: 'left', seat });
      if (room.host === seat) {
        const next = room.seats.find((x) => x && !x.bot && x.seat !== seat);
        room.host = next ? next.seat : seat;
      }
      room.seats[seat] = null;
    } else {
      handToBot(room, seat);
    }
    if (!humanOnline(room)) closeRoom(room);
    return { ok: true };
  }
  if (type === 'cancel') {
    if (room.host !== seat) return { error: 'Só o anfitrião fecha a mesa.' };
    closeRoom(room, 'cancel');
    return { ok: true };
  }
  return { error: 'ação desconhecida' };
}

// ---------- relógio: bots, abandonos, mãos seguintes ----------
setInterval(() => {
  const now = Date.now();
  for (const [id, room] of rooms) {
    if (room.status === 'closed') {
      if (now - room.closedAt > 60 * 1000) rooms.delete(id);
      continue;
    }
    if (now - room.created > ROOM_TTL && !humanOnline(room)) { closeRoom(room); continue; }

    for (const s of room.seats) {
      if (!s || s.bot) continue;
      const ttl = room.status === 'lobby' ? LOBBY_TTL : GAME_TTL;
      if (now - s.lastSeen > ttl) {
        if (room.status === 'lobby') {
          feed(room, { t: 'left', seat: s.seat });
          if (room.host === s.seat) {
            const next = room.seats.find((x) => x && !x.bot && x.seat !== s.seat);
            room.host = next ? next.seat : s.seat;
          }
          room.seats[s.seat] = null;
          if (!room.seats.some((x) => x && !x.bot)) closeRoom(room);
        } else if (s.online) {
          handToBot(room, s.seat, true);      // o parceiro automático pega-lhe nas cartas
        }
      }
    }

    const g = room.hand;
    if (!g) continue;

    if (g.over) {
      if (room.match.winner) { room.status = 'over'; continue; }
      if (room.nextHandAt && now >= room.nextHandAt) {
        room.dealer = (room.dealer + 1) % 4;
        startHand(room);
      }
      continue;
    }

    const cur = room.seats[g.turn];
    if (cur && cur.bot) {
      if (!room.botAt) room.botAt = now + botDelay();
      else if (now >= room.botAt) {
        room.botAt = 0;
        const who = g.turn;
        const card = chooseCard(g, who);
        if (card) {
          let jogada = card;
          if (playCard(g, who, card).error) {   // rede de segurança: joga a primeira legal
            jogada = legalPlays(g, who)[0];
            if (jogada) playCard(g, who, jogada);
          }
          // o lugar é o de quem jogou, guardado antes de playCard mudar a vez:
          // depois já g.turn aponta para o seguinte (ou para quem levou a vaza)
          if (jogada) feed(room, { t: 'play', seat: who, card: jogada, trump: g.trump, bot: true });
          if (g.over) settleHand(room);
        }
      }
    } else room.botAt = 0;
  }
}, 250).unref();

const publicRoom = (r) => ({
  id: r.id, name: r.name, status: r.status, handNo: r.handNo,
  seats: r.seats.map((s, i) => (s ? { seat: i, name: s.name, team: s.team, bot: s.bot, online: s.online, away: !!s.away } : null)),
  taken: r.seats.filter(Boolean).length,
  games: r.match.games,
  created: r.created,
});

// ---------- HTTP ----------
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  const path = url.pathname;
  try {
    if (req.method === 'OPTIONS') { res.writeHead(204, CORS); res.end(); return; }

    if (path === '/api/info')
      return sendJson(res, 200, { app: 'sueca-no-cafe', port: PORT, lanIp: lanIp(), players: 4 });

    if (path === '/api/rooms' && req.method === 'GET')
      return sendJson(res, 200, { rooms: [...rooms.values()].filter((r) => r.status !== 'closed').map(publicRoom) });

    if (path === '/api/rooms' && req.method === 'POST') {
      if (rooms.size >= MAX_ROOMS) return sendJson(res, 503, { error: 'Demasiadas mesas abertas.' });
      const b = await readBody(req);
      const room = makeRoom({ name: b.name, player: b.player || {} });
      rooms.set(room.id, room);
      const you = room.seats[0];
      return sendJson(res, 200, { id: room.id, seat: you.seat, tk: you.tk, room: viewFor(room, 0) });
    }

    const m = path.match(/^\/api\/rooms\/([A-Z0-9]{4})\/(join|state|action)$/);
    if (m) {
      const room = rooms.get(m[1]);
      const what = m[2];
      if (!room || room.status === 'closed') return sendJson(res, 404, { error: 'Essa mesa já não existe.' });

      if (what === 'join' && req.method === 'POST') {
        const b = await readBody(req);
        if (room.status === 'closed') return sendJson(res, 404, { error: 'Essa mesa já não existe.' });
        if (room.status !== 'lobby') {
          // mesa em jogo: só há lugar se alguém saiu e deixou a cadeira guardada
          const openSeat = room.seats.find((x) => x && x.away);
          if (!openSeat) return sendJson(res, 409, { error: 'Essa mesa já está a jogar.' });
          openSeat.name = clean(b.name, 14) || 'Jogador';
          openSeat.avatar = clean(b.avatar, 12) || 'cafe';
          openSeat.tk = newToken();          // quem saiu perde o lugar: passou para o novo
          openSeat.bot = false;
          openSeat.online = true;
          openSeat.away = false;
          openSeat.ready = true;
          openSeat.lastSeen = Date.now();
          feed(room, { t: 'took', seat: openSeat.seat, name: openSeat.name });
          return sendJson(res, 200, { seat: openSeat.seat, tk: openSeat.tk, room: viewFor(room, openSeat.seat) });
        }
        const s = addSeat(room, b);
        if (!s) return sendJson(res, 409, { error: 'Essa equipa já tem os dois jogadores.' });
        feed(room, { t: 'joined', seat: s.seat });
        return sendJson(res, 200, { seat: s.seat, tk: s.tk, room: viewFor(room, s.seat) });
      }

      const seat = Number(url.searchParams.get('seat'));
      if (what === 'state' && req.method === 'GET') {
        const tk = url.searchParams.get('tk');
        const s = Number.isInteger(seat) ? room.seats[seat] : null;
        if (!s) return sendJson(res, 409, { error: 'Saíste da mesa.' });
        const dono = !!s.tk && s.tk === tk;   // o dono retoma a cadeira que ficou à espera dele
        if (!s.bot && !dono) return sendJson(res, 403, { error: 'Credencial errada.' });
        if (dono) {
          s.lastSeen = Date.now();
          if (s.bot || !s.online) {
            s.bot = false; s.online = true; s.away = false;
            feed(room, { t: 'back', seat, name: s.name });
          }
        }
        const since = Number(url.searchParams.get('since') || 0);
        return sendJson(res, 200, { view: viewFor(room, seat), events: room.feed.filter((e) => e.seq > since) });
      }

      if (what === 'action' && req.method === 'POST') {
        const b = await readBody(req);
        const who = Number(b.seat);
        const s = Number.isInteger(who) && who >= 0 && who < 4 ? room.seats[who] : null;
        if (!s) return sendJson(res, 409, { error: 'Saíste da mesa.' });
        if (s.tk && s.tk !== b.tk) return sendJson(res, 403, { error: 'Credencial errada.' });
        s.lastSeen = Date.now();
        s.online = true;
        const r = action(room, who, b);
        if (r.error) return sendJson(res, 400, { error: r.error });
        // mudar de equipa troca o lugar: a vista nova é a do lugar novo
        const at = Number.isInteger(r.seat) ? r.seat : who;
        return sendJson(res, 200, { ...r, view: viewFor(room, at) });
      }
    }

    if (path.startsWith('/api/')) return sendJson(res, 404, { error: 'não encontrado' });

    let file = normalize(join(ROOT, decodeURIComponent(path === '/' ? '/index.html' : path)));
    if (!file.startsWith(ROOT + sep) && file !== ROOT) { res.writeHead(403); res.end(); return; }
    try {
      const data = await readFile(file);
      res.writeHead(200, { 'content-type': MIME[extname(file)] || 'application/octet-stream', 'cache-control': 'no-cache' });
      res.end(data);
    } catch {
      res.writeHead(404, { 'content-type': 'text/plain; charset=utf-8' });
      res.end('404');
    }
  } catch (e) {
    sendJson(res, 400, { error: e.message || 'pedido inválido' });
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log('☕ Sueca no Café — mesas de 4, regras de Portugal');
  console.log(`   Nesta máquina:  http://localhost:${PORT}`);
  console.log(`   Na rede Wi-Fi:  http://${lanIp()}:${PORT}`);
  console.log('   Os outros jogadores abrem o endereço da rede e escolhem a mesa e a equipa.');
});
