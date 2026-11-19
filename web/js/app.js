import { RANK_INFO, SUIT_INFO, TEAMS, seatFrom, suitOf, teamOfSeat } from './engine.js';
import { createRoom, fetchRooms, joinRoom, Mesa, parseMesaInput, scanNetwork } from './net.js';
import { cardHTML, trumpChipHTML } from './cards.js';
import { isMuted, setMuted, sfx } from './audio.js';
import { cardDrop, dealSteam, trickWin, winConfetti } from './fx.js';
import { GROUP_LABEL, HYPOTHESES, avatarHTML, parseToken, tokenFor } from './avatars.js';

const $ = (s) => document.querySelector(s);
const PORT = Number(location.port) || 8743;
// posição no ecrã de quem vê: 0 em baixo (tu), 1 direita, 2 fundo, 3 esquerda
const POS_NAME = ['bottom', 'right', 'top', 'left'];
const POS_EL = ['#seatBottom', '#seatRight', '#seatTop', '#seatLeft'];
const SLOT_EL = ['#tsBottom', '#tsRight', '#tsTop', '#tsLeft'];
const teamOf = (t) => TEAMS[t] || TEAMS.a;

const profile = {
  name: localStorage.getItem('snc-name') || '',
  avatar: localStorage.getItem('snc-avatar') || 'd0:0',
};

const S = {
  mesa: null, view: null, mySeat: 0, wantTeam: 'a',
  seenTricks: 0, trickOn: 0, holdUntil: 0, holdTimer: 0, resultShownFor: '', handSig: '', seatsSig: '', logSig: '',
  logMode: localStorage.getItem('snc-logMode') === 'all' ? 'all' : 'last',
  countdownTimer: 0,
};

const G = () => S.view?.game || null;
const posOf = (seat) => seatFrom(seat, S.mySeat);
const myTeam = () => teamOfSeat(S.mySeat);

/* ─────────── utilidades ─────────── */
function show(name) {
  document.querySelectorAll('.screen').forEach((s) => s.classList.add('hidden'));
  $('#screen-' + name).classList.remove('hidden');
  window.scrollTo({ top: 0 });
}
function toast(msg, type = '') {
  const t = document.createElement('div');
  t.className = 'toast ' + type;
  t.textContent = msg;
  $('#toasts').appendChild(t);
  setTimeout(() => { t.classList.add('out'); setTimeout(() => t.remove(), 260); }, 2800);
}
function openModal(html, dismissable = true) {
  $('#modalCard').innerHTML = html;
  $('#modal').classList.remove('hidden');
  $('#modal').dataset.dismissable = dismissable ? '1' : '';
}
function closeModal() {
  $('#modal').classList.add('hidden');
  clearInterval(S.countdownTimer);
}
/* Cópias alojadas fora do café (ex.: Netlify) só têm o interface: sem o
   anunciador no mesmo endereço, as mesas não podem existir. Em vez de um
   404 nu, leva a pessoa para a máquina que corre o server.mjs. */
function openCafeGuide() {
  openModal(`
    <h2>📡 Sem anunciador aqui</h2>
    <p>Esta cópia do jogo está na Netlify, que só serve o interface. As mesas vivem no anunciador — o <code>node server.mjs</code> que corre numa máquina do teu Wi-Fi.</p>
    <p>Ligou o anunciador no café? Então abre o jogo a partir do endereço dessa máquina: é aí que as mesas acontecem.</p>
    <label class="lbl" for="cafeAddr">Endereço do café</label>
    <input id="cafeAddr" class="input" placeholder="ex.: 192.168.100.2:8743" autocomplete="off" spellcheck="false"
      value="${escapeHtml(localStorage.getItem('snc-cafe') || '')}">
    <button class="btn btn-primary full mt" id="cafeGo">Ir para o café →</button>
    <button class="btn btn-ghost full mt" id="cafeClose">Ficar aqui</button>`);
  $('#cafeGo').addEventListener('click', () => {
    const raw = $('#cafeAddr').value.trim();
    if (!raw) { toast('Escreve o endereço da máquina do café.', 'warn'); return; }
    const addr = raw.replace(/^https?:\/\//, '').replace(/\/+$/, '');
    localStorage.setItem('snc-cafe', addr);
    location.href = `http://${addr}/`;
  });
  $('#cafeClose').addEventListener('click', () => { sfx.click(); closeModal(); });
}
$('#modal').addEventListener('click', (e) => {
  if (e.target.id === 'modal' && $('#modal').dataset.dismissable) closeModal();
});
const escapeHtml = (s) => String(s ?? '').replace(/[&<>"']/g, (c) =>
  ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
function setChip(text, cls = '') {
  const c = $('#connChip');
  if (!text) { c.classList.add('hidden'); return; }
  c.classList.remove('hidden', 'warn', 'bad');
  if (cls) c.classList.add(cls);
  c.textContent = text;
}
const myName = () => profile.name || 'Jogador';

/* ─────────── som ─────────── */
function paintSound() {
  const m = isMuted();
  $('#soundBtn').setAttribute('aria-pressed', m ? 'false' : 'true');
  $('#soundBtn').setAttribute('aria-label', m ? 'Som desligado' : 'Som ligado');
  $('#soundOnIcon').classList.toggle('hidden', m);
  $('#soundOffIcon').classList.toggle('hidden', !m);
}
$('#soundBtn').addEventListener('click', () => { setMuted(!isMuted()); paintSound(); sfx.click(); });
paintSound();

/* ─────────── nome e avatar ─────────── */
// Uma fila de oito bonecos, todos nascidos do nome: mudar o nome redrawna
// a fila e a escolha continua a ser a mesma posição.
function paintPreview() {
  $('#avPreview').innerHTML = avatarHTML(profile.avatar, myName());
  $('#avPreviewName').textContent = myName();
  const p = parseToken(profile.avatar);
  $('#avPreviewTag').textContent = p
    ? `${GROUP_LABEL} · hipótese ${p.v + 1}`
    : 'Café · o emoji de sempre';
}
function renderAvatarPicker() {
  const wrap = $('#avatarGroups');
  const name = myName();
  wrap.innerHTML = `
    <div class="av-row" role="listbox" aria-label="Avatares ${GROUP_LABEL}">
      ${Array.from({ length: HYPOTHESES }, (_, v) => {
        const tok = tokenFor(0, v);
        const sel = parseToken(profile.avatar)?.v === v;
        return `<button type="button" role="option" aria-selected="${sel}"
          class="avatar-opt av-cell${sel ? ' sel' : ''}" data-v="${v}"
          aria-label="${GROUP_LABEL}, hipótese ${v + 1}">${avatarHTML(tok, name)}</button>`;
      }).join('')}
    </div>`;
  wrap.querySelectorAll('.av-cell').forEach((b) => b.addEventListener('click', () => {
    profile.avatar = tokenFor(0, +b.dataset.v);
    localStorage.setItem('snc-avatar', profile.avatar);
    sfx.click();
    renderAvatarPicker();
  }));
  paintPreview();
}
renderAvatarPicker();
$('#btnAvRandom').addEventListener('click', () => {
  profile.avatar = tokenFor(0, Math.floor(Math.random() * HYPOTHESES));
  localStorage.setItem('snc-avatar', profile.avatar);
  sfx.click();
  renderAvatarPicker();
});
$('#nameInput').value = profile.name;
let nameTimer = 0;
$('#nameInput').addEventListener('input', () => {
  profile.name = $('#nameInput').value.trim();
  localStorage.setItem('snc-name', profile.name);
  clearTimeout(nameTimer);
  nameTimer = setTimeout(renderAvatarPicker, 180);
});

/* ─────────── regras ─────────── */
const RULES_HTML = `
  <h2>📜 Regras de Portugal</h2>
  <h3>O baralho</h3>
  <ul>
    <li><b>40 cartas</b>: ficam de fora o 8, o 9 e o 10. Em cada naipe a ordem é <b>Ás, 7, Rei, Valete, Dama, 6, 5, 4, 3, 2</b>.</li>
    <li>O <b>Valete bate a Dama</b> — é a regra que apanha sempre alguém de surpresa.</li>
    <li>Pontos: <b>Ás 11</b> · <b>7 10</b> · <b>Rei 4</b> · <b>Valete 3</b> · <b>Dama 2</b>. As outras não valem nada. No baralho há <b>120 pontos</b>.</li>
  </ul>
  <h3>A mesa</h3>
  <ul>
    <li>Quatro jogadores em <b>duas equipas</b>: quem está defronte de ti é o teu parceiro.</li>
    <li>Joga-se para a <b>direita</b>, no sentido contrário ao dos ponteiros do relógio.</li>
    <li>Dão-se <b>10 cartas</b> a cada um, em dois lotes de cinco. Quem dá guarda a última carta, <b>virada para cima</b> encostada à sua cadeira: é ela que marca o <b>trunfo</b> e recolhe-se à <b>terceira vaza</b> — ou antes, se for jogada.</li>
  </ul>
  <h3>A vaza</h3>
  <ul>
    <li>Quem abre define o naipe e <b>os outros assistem</b> se tiverem cartas desse naipe.</li>
    <li>Sem o naipe, podes <b>trunfar</b> ou <b>baldar-te</b> — não és obrigado a matar.</li>
    <li>Ganha a vaza o <b>trunfo mais alto</b> ou, sem trunfos, a <b>carta mais alta do naipe de saída</b>. Quem ganha abre a vaza seguinte.</li>
    <li>Todos os pontos das cartas da vaza vão para a equipa que a ganhou.</li>
    <li>Por baixo da mão escolhes se o registo mostra só a <b>última vaza</b> ou <b>as vazas todas</b> da mão.</li>
  </ul>
  <h3>Marcar jogos</h3>
  <ul>
    <li><b>61 a 90</b> pontos = 1 jogo · <b>91 a 119</b> = 2 jogos · <b>120</b> = <b>bandeira</b> (4 jogos e fecha a partida) · <b>60-60</b> ninguém marca.</li>
    <li>Ganha a partida a primeira equipa a chegar aos <b>4 jogos</b> — o alvo combina-se na sala.</li>
    <li>Regras da casa à escolha: obrigar a trunfar, obrigar a subir, virar ou não virar o trunfo.</li>
  </ul>
  <h3>Quem sai do lugar</h3>
  <ul>
    <li>A mão não pára: o <b>parceiro automático</b> joga por essa cadeira, que fica <b>guardada</b>.</li>
    <li>O <b>anfitrião</b> é avisado e decide: <b>aguardar</b> que alguém entre, ou <b>fechar a mesa</b>.</li>
    <li>Quem saiu pode <b>retomar as suas cartas</b> abrindo o mesmo link; se entrar outra pessoa, essa pessoa continua a mão naquele lugar.</li>
  </ul>
  <h3>Etiqueta</h3>
  <ul>
    <li>Entre parceiros diz-se só o essencial, como <b>qual é o trunfo</b> quando alguém pergunta. Dica de carta é renúncia a sério.</li>
    <li>Aqui o servidor não deixa jogar carta ilegal, por isso <b>renúncia é impossível</b>.</li>
  </ul>
  <button class="btn btn-primary full" id="rulesClose">Fechar</button>`;
function openRules() {
  sfx.click();
  openModal(RULES_HTML);
  $('#rulesClose').addEventListener('click', () => { sfx.click(); closeModal(); });
}
$('#btnRulesHome').addEventListener('click', openRules);
$('#btnRulesGame').addEventListener('click', openRules);

/* ─────────── sair ─────────── */
function resetToHome() {
  S.mesa?.close();
  S.mesa = null; S.view = null; S.seenTricks = 0; S.resultShownFor = '';
  S.trickOn = 0; S.holdUntil = 0; clearTimeout(S.holdTimer);
  S.handSig = S.seatsSig = S.logSig = '';
  sessionStorage.removeItem('snc-session');
  closeModal();
  setChip(null);
  show('home');
}
document.querySelectorAll('.back-home').forEach((b) => b.addEventListener('click', () => { sfx.click(); resetToHome(); }));
document.querySelectorAll('.leave-room').forEach((b) => b.addEventListener('click', () => { sfx.click(); resetToHome(); }));
// no meio de uma mão é preciso confirmar: ficas é o teu parceiro que fica a menos um
$('#btnLeave').addEventListener('click', () => {
  sfx.click();
  const g = G();
  if (!g || g.over) { resetToHome(); return; }
  openModal(`<h2 class="display sm">Sair da mesa?</h2>
    <p class="help">A mão está a meio: o parceiro automático joga por ti e podes voltar para retomar o lugar.</p>
    <button class="btn btn-danger full" id="leaveYes">Sair mesmo</button>
    <button class="btn btn-ghost full mt" id="leaveNo">Continuar a jogar</button>`);
  $('#leaveYes').addEventListener('click', () => { sfx.click(); resetToHome(); });
  $('#leaveNo').addEventListener('click', () => { sfx.click(); closeModal(); });
});

$('#btnCreate').addEventListener('click', () => {
  sfx.click();
  $('#roomNameInput').value = `Mesa de ${myName()}`;
  $('#hostWaiting').classList.add('hidden');
  const btn = $('#btnOpenRoom');
  btn.disabled = false;
  btn.textContent = 'Anunciar mesa na rede';
  show('create');
});
$('#btnJoin').addEventListener('click', () => { sfx.click(); show('join'); loadRooms(); });

/* ─────────── ligação à mesa ─────────── */
function wireMesa(mesa) {
  mesa.onState = (st) => {
    if (st === 'connected') setChip(null);
    else if (st === 'weak') setChip('ligação fraca', 'warn');
    else if (st === 'offline') setChip('sem servidor', 'bad');
    else if (st === 'connecting') setChip('a ligar…');
  };
  mesa.onView = (view) => {
    const wasLobby = S.view?.status === 'lobby';
    S.view = view;
    S.mySeat = view.seat;
    if (view.status === 'closed') { toast('A mesa fechou.'); resetToHome(); return; }
    const screen = view.status === 'lobby' ? 'lobby' : 'game';
    if (screen === 'game' && wasLobby) { closeModal(); sfx.notify(); }
    show(screen);
    render();
  };
  mesa.onEvents = (events) => events.forEach(handleEvent);
  mesa.onClose = (reason) => {
    toast(/não existe|fech/i.test(String(reason)) ? 'A mesa fechou.' : String(reason || 'Saíste da mesa.'), 'warn');
    resetToHome();
  };
  mesa.start();
  S.mesa = mesa;
  sessionStorage.setItem('snc-session', JSON.stringify({ base: mesa.base, id: mesa.id, seat: mesa.seat, tk: mesa.tk }));
}

$('#btnOpenRoom').addEventListener('click', async () => {
  sfx.click();
  const btn = $('#btnOpenRoom');
  btn.disabled = true;
  try {
    const d = await createRoom('', $('#roomNameInput').value, { name: myName(), avatar: profile.avatar });
    S.wantTeam = 'a';
    wireMesa(new Mesa('', d.id, d.seat, d.tk));
    $('#hostWaiting').classList.remove('hidden');
    $('#mesaBadge').innerHTML = `<b>${d.id}</b>`;
    $('#shareUrl').textContent = `${location.origin}/?mesa=${d.id}`;
    btn.textContent = 'Mesa anunciada ✓';
  } catch (e) {
    if (/404/.test(String(e.message))) openCafeGuide();
    else toast(e.message || 'Não consegui abrir a mesa.', 'warn');
    btn.disabled = false;
  }
});

$('#btnCopyUrl').addEventListener('click', async (e) => {
  const txt = $('#shareUrl').textContent;
  try {
    await navigator.clipboard.writeText(txt);
    e.target.textContent = 'Copiado ✓';
  } catch { e.target.textContent = txt; }
  sfx.click();
  setTimeout(() => { e.target.textContent = 'Copiar endereço'; }, 1800);
});

/* ─────────── mesas na rede ─────────── */
const roomCache = new Map();
function renderRoomList() {
  const list = $('#roomList');
  const rooms = [...roomCache.values()];
  if (!rooms.length) {
    list.innerHTML = '<p class="empty-list">Ainda ninguém anunciou mesas aqui. Pede ao anfitrião o código e escreve-o em cima.</p>';
    return;
  }
  list.innerHTML = '';
  for (const r of rooms) {
    const seats = r.info.seats || [];
    const dots = [0, 1, 2, 3].map((i) => {
      const s = seats[i];
      const color = !s ? 'var(--cream)' : teamOfSeat(i) === 'a' ? 'var(--amber)' : 'var(--blue)';
      return `<span class="tp-dot" style="background:${color}"></span>`;
    }).join('');
    const taken = seats.filter(Boolean).length;
    const guardadas = seats.filter((s) => s && s.away).length;
    const row = document.createElement('div');
    row.className = 'room-row';
    row.innerHTML = `<span class="room-avatar">🃏</span>
      <div class="room-info"><b>${escapeHtml(r.info.name)}</b>
        <span class="room-meta">${r.info.id} · ${taken}/4 ${dots} ·
          ${r.info.status === 'lobby' ? 'à espera' : r.info.status === 'over' ? 'acabou' : `a jogar (mão ${r.info.handNo})`}${
          guardadas ? ` · 🚪 ${guardadas} guardada(s)` : ''}</span></div>`;
    const b = document.createElement('button');
    const free = r.info.status === 'lobby' && taken < 4;
    const away = seats.filter((s) => s && s.away).length;
    const rescue = !free && r.info.status === 'playing' && away > 0;
    b.className = 'btn btn-small' + (free ? ' btn-primary' : rescue ? ' btn-blue' : '');
    b.textContent = free ? 'Entrar' : rescue ? 'Substituir' : 'Cheia';
    b.disabled = !(free || rescue);
    b.addEventListener('click', () => joinEntry(r.base, r.info.id, r.info));
    row.appendChild(b);
    list.appendChild(row);
  }
}

async function loadRooms() {
  roomCache.clear();
  try {
    for (const r of await fetchRooms('')) roomCache.set(r.id, { base: '', info: r });
  } catch { /* o anunciador pode estar noutra máquina */ }
  renderRoomList();
}

$('#btnScanNet').addEventListener('click', async () => {
  sfx.click();
  const btn = $('#btnScanNet');
  btn.disabled = true;
  $('#scanStatus').textContent = 'A varrer o Wi-Fi…';
  try {
    const bases = await scanNetwork(PORT, (done, total) =>
      $('#scanStatus').textContent = `A varrer o Wi-Fi… ${Math.round((done / total) * 100)}%`);
    for (const base of bases) {
      try {
        for (const r of await fetchRooms(base)) roomCache.set(base + r.id, { base, info: r });
      } catch { /* não respondeu */ }
    }
    $('#scanStatus').textContent = bases.length
      ? `${bases.length} máquina(s) na rede com mesas.`
      : 'Não encontrei anunciadores na rede. Pede o IP a quem abriu a mesa.';
    if (!bases.length && location.protocol === 'https:') openCafeGuide();
  } catch (e) {
    $('#scanStatus').textContent = e.message || 'Falha na varredura.';
  }
  renderRoomList();
  btn.disabled = false;
});

$('#btnDirect').addEventListener('click', async () => {
  sfx.click();
  const code = parseMesaInput($('#mesaInput').value);
  if (!code) { toast('O código da mesa tem 4 caracteres.', 'warn'); sfx.error(); return; }
  const known = roomCache.get(code);
  if (known) { joinEntry(known.base, code, known.info); return; }
  const here = (await fetchRooms('').catch(() => [])) || [];
  const info = here.find((r) => r.id === code);
  if (info) { roomCache.set(code, { base: '', info }); joinEntry('', code, info); return; }
  $('#scanStatus').textContent = 'A procurar essa mesa noutras máquinas…';
  for (const base of await scanNetwork(PORT).catch(() => [])) {
    const found = ((await fetchRooms(base).catch(() => [])) || []).find((r) => r.id === code);
    if (found) { roomCache.set(base + code, { base, info: found }); joinEntry(base, code, found); return; }
  }
  toast('Não encontrei essa mesa neste Wi-Fi.', 'warn');
  sfx.error();
  if (location.protocol === 'https:') openCafeGuide();
});

async function enterRoom(base, id, team) {
  sfx.click();
  if (team) S.wantTeam = team;
  try {
    const d = await joinRoom(base, id, { name: myName(), avatar: profile.avatar, team: S.wantTeam });
    wireMesa(new Mesa(base, id, d.seat, d.tk));
    return true;
  } catch (e) {
    toast(e.message || 'Não consegui entrar.', 'warn');
    sfx.error();
    return false;
  }
}

// As duas equipas têm ambas lugar? Então pergunta-se com quem se joga.
const freeTeams = (info) => ['a', 'b'].filter((t) =>
  (info.seats || []).filter((s, i) => s && teamOfSeat(i) === t).length < 2);

function joinEntry(base, id, info) {
  const guardadas = (info.seats || []).filter((s) => s && s.away);
  if (info.status !== 'lobby') {
    if (!guardadas.length) { toast('Essa mesa já está a jogar com as quatro cadeiras tomadas.', 'warn'); return; }
    openTakeover(base, id, guardadas);
    return;
  }
  const free = freeTeams(info);
  if (free.length !== 2) return enterRoom(base, id, free[0]);
  openModal(`<h2 class="display sm">Com quem jogas?</h2>
    <p class="help">Escolhe a equipa — o teu parceiro fica defronte de ti.</p>
    ${['a', 'b'].map((t) => {
    const names = (info.seats || []).map((s, i) => (s && teamOfSeat(i) === t ? s.name : null)).filter(Boolean);
    return `<button class="btn full mt ${t === 'a' ? 'btn-primary' : 'btn-blue'} js-team" data-team="${t}">
            ${teamOf(t).name} · ${names.length ? names.join(' e ') : 'ainda sem ninguém'}
          </button>`;
  }).join('')}
    <button class="btn btn-ghost full mt" id="teamCancel">Voltar</button>`);
  document.querySelectorAll('.js-team').forEach((b) =>
    b.addEventListener('click', () => { closeModal(); enterRoom(base, id, b.dataset.team); }));
  $('#teamCancel').addEventListener('click', () => { sfx.click(); closeModal(); });
}

// Cadeira de quem saiu fica guardada: outro pode sentar-se nela e continuar a mão.
function openTakeover(base, id, guardadas) {
  const nomes = guardadas.map((s) => escapeHtml(s.name)).join(' e ');
  openModal(`<h2 class="display sm">🚪 Cadeira guardada</h2>
    <p class="help">${nomes} saiu a meio da mão. Ao sentares-te nessa cadeira <b>continuas o jogo</b> com as cartas que já estão na mesa; quem saiu perde o lugar.</p>
    <button class="btn btn-primary full mt" id="takeYes">Entrar e continuar</button>
    <button class="btn btn-ghost full mt" id="takeNo">Voltar</button>`);
  $('#takeYes').addEventListener('click', () => { closeModal(); enterRoom(base, id); });
  $('#takeNo').addEventListener('click', () => { sfx.click(); closeModal(); });
}

/* ─────────── sala ─────────── */
function renderLobby(view) {
  $('#lobbyRoomName').textContent = view.name;
  $('#lobbyCode').textContent = view.id;
  const isHost = view.host === S.mySeat;
  $('#hostPanel').classList.toggle('hidden', !isHost);

  const mine = teamOfSeat(S.mySeat);
  $('#teamPick').innerHTML = ['a', 'b'].map((t) => {
    const seats = view.seats.filter((s) => s && teamOfSeat(s.seat) === t);
    const full = seats.length >= 2;
    const me = t === mine;
    return `<button class="tp-btn ${me ? 'sel' : ''} ${full && !me ? 'full' : ''}" data-team="${t}" ${full && !me ? 'disabled' : ''}>
      <span class="tp-dot" style="background:${teamOf(t).color}"></span>
      <span class="tp-name">Equipa ${teamOf(t).name}</span>
      <span class="tp-help">${me ? 'a tua equipa' : full ? 'lotação esgotada' : `${2 - seats.length} lugar(es) livre(s)`}</span>
    </button>`;
  }).join('');
  $('#teamPick').querySelectorAll('[data-team]').forEach((b) => b.addEventListener('click', async () => {
    if (b.disabled || b.classList.contains('sel')) return;
    sfx.click();
    const r = await S.mesa.act({ type: 'team', value: b.dataset.team });
    if (r?.error) { toast(r.error, 'warn'); sfx.error(); }
    else if (Number.isInteger(r.seat)) S.mesa.moveTo(r.seat);
  }));

  $('#lobbyTeams').innerHTML = ['a', 'b'].map((t) => {
    const list = view.seats.filter((s) => s && s.team === t);
    const rows = [0, 1].map((k) => {
      const s = list[k];
      if (!s) return '<div class="lt-seat empty">lugar livre</div>';
      return `<div class="lt-seat ${s.seat === S.mySeat ? 'me' : ''}">
        <span class="lp-avatar">${avatarHTML(s.avatar, s.name)}</span>
        <span class="s-name">${escapeHtml(s.name)}</span>
        ${s.bot ? '<span title="parceiro automático">🤖</span>' : ''}
        ${s.seat === view.host ? '<span title="anfitrião">👑</span>' : ''}
        ${s.ready ? '<span title="pronto">✓</span>' : ''}
      </div>`;
    }).join('');
    return `<div class="lt-col"><div class="lt-head"><span class="tp-dot" style="background:${teamOf(t).color}"></span>
      Equipa ${teamOf(t).name}</div>${rows}</div>`;
  }).join('');

  const you = view.seats[S.mySeat];
  const me = $('#btnReady');
  me.textContent = you?.ready ? 'Já não quero ✓' : 'Estou dentro ✓';
  me.className = 'btn btn-big full ' + (you?.ready ? 'btn-ghost' : 'btn-primary');

  const humans = view.seats.filter((s) => s && !s.bot).length;
  const free = 4 - view.seats.filter(Boolean).length;
  const status = $('#lobbyStatus');
  if (!isHost) status.textContent = `${humans} jogador(es) à mesa · à espera que o anfitrião comece`;
  else if (free > 0 && !view.fillBots) status.textContent = `Faltam ${free} para encher as equipas.`;
  else if (free > 0) status.textContent = `Começas com ${free} parceiro(s) automático(s), ou espera por mais gente.`;
  else status.textContent = 'Mesa cheia — vamos a isso!';

  if (isHost) renderHostPanel(view);
}

const RULE_ITEMS = [
  ['virarTrunfo', 'Virar o trunfo', 'a última carta fica encostada à cadeira de quem dá'],
  ['trunfarSeVazio', 'Obrigar a trunfar', 'quem não assista tem de cortar se puder'],
  ['subirSempre', 'Obrigar a subir', 'tens de tentar ganhar a vaza quando podes'],
  ['bandeiraFecha', 'Bandeira fecha', 'os 120 pontos ganham a partida na hora'],
  ['fillBots', 'Parceiros automáticos', 'preenchem os lugares que faltem'],
];
function renderHostPanel(view) {
  const panel = $('#rulesToggles');
  if (!panel.dataset.built) {
    panel.innerHTML = RULE_ITEMS.map(([k, t, h]) =>
      `<button class="rt-row" data-key="${k}"><span>${t}<small>${h}</small></span><span class="sw"></span></button>`).join('');
    panel.querySelectorAll('[data-key]').forEach((b) => b.addEventListener('click', () => {
      const key = b.dataset.key;
      const cur = key === 'fillBots' ? S.view.fillBots : S.view.rules[key];
      sfx.click();
      S.mesa.act({ type: 'rules', key, value: !cur });
    }));
    const target = $('#targetRow');
    target.innerHTML = '<span class="lbl" style="margin:0">Partida a</span>' +
      [2, 4, 6, 10].map((n) => `<button class="tg-chip" data-n="${n}">${n} jogos</button>`).join('');
    target.querySelectorAll('[data-n]').forEach((b) => b.addEventListener('click', () => {
      sfx.click();
      S.mesa.act({ type: 'rules', key: 'jogosParaGanhar', value: Number(b.dataset.n) });
    }));
    panel.dataset.built = '1';
  }
  panel.querySelectorAll('[data-key]').forEach((b) => {
    const key = b.dataset.key;
    b.classList.toggle('on', !!(key === 'fillBots' ? view.fillBots : view.rules[key]));
  });
  $('#targetRow').querySelectorAll('[data-n]').forEach((b) =>
    b.classList.toggle('sel', Number(b.dataset.n) === view.rules.jogosParaGanhar));
}

$('#btnReady').addEventListener('click', async () => {
  sfx.click();
  const you = S.view?.seats?.[S.mySeat];
  const r = await S.mesa.act({ type: 'ready', value: !you?.ready });
  if (r?.error) toast(r.error, 'warn');
});
$('#btnStart').addEventListener('click', async () => {
  sfx.click();
  const r = await S.mesa.act({ type: 'start' });
  if (r?.error) { toast(r.error, 'warn'); sfx.error(); }
});

/* ─────────── mesa: cadeiras ─────────── */
function renderSeats(view) {
  const g = G();
  const sig = JSON.stringify([view.seats.map((s) => s && [s.name, s.bot, s.online, s.away, s.cards, s.seat]), g?.turn]);
  if (sig === S.seatsSig) return;
  S.seatsSig = sig;
  for (let pos = 0; pos < 4; pos++) {
    const el = $(POS_EL[pos]);
    const seat = (pos + S.mySeat) % 4;
    const s = view.seats[seat];
    const classes = ['seat', `seat-${POS_NAME[pos]}`];
    if (!s) {
      el.className = classes.join(' ');
      el.innerHTML = '<span class="s-name">lugar livre</span>';
      el.removeAttribute('aria-label');
      continue;
    }
    classes.push(`team-${s.team}`);
    const mine = !!g && !g.over && g.turn === seat;
    if (mine) classes.push('turn');
    if (s.bot) classes.push('auto');
    else if (!s.online) classes.push('gone');
    el.className = classes.join(' ');
    el.dataset.seat = seat;
    el.setAttribute('aria-label', `${s.name}, equipa ${teamOf(s.team).name}`
      + `${s.bot ? (s.away ? ', saiu — o automático joga por ele' : ', parceiro automático') : ''}`
      + `${mine ? ', é a vez dele' : ''}`);
    el.innerHTML = `<span class="seat-face">${avatarHTML(s.avatar, s.name)}</span>
      <span class="seat-body">
        <span class="s-name">${escapeHtml(s.name)}</span>
        <span class="s-meta">
          <i class="s-tag">${s.bot ? (s.away ? 'saiu' : 'auto') : teamOf(s.team).name}</i>
          ${g && s.cards ? `<i class="s-cards"><i></i>${s.cards}</i>` : ''}
          ${mine ? '<i class="s-turn" title="é a vez dele"></i>' : ''}
        </span>
      </span>
      ${s.seat === view.host ? '<span class="s-host" title="anfitrião">👑</span>' : ''}`;
  }
}

/* ─────────── mesa: vaza e trunfo ─────────── */
// A vaza fechada fica um segundo na mesa antes de limpar. Sem isto a última
// carta mal dá para ser vista: os bots jogam logo a seguir e a mesa esvazia-se.
const TRICK_HOLD = 1100;

function holdTrick(vencedor = null) {
  S.holdUntil = Date.now() + TRICK_HOLD;
  clearTimeout(S.holdTimer);
  S.holdTimer = setTimeout(() => collectTrick(vencedor), TRICK_HOLD);
}

// Findas as vazas, as quatro cartas juntam-se no centro do pano e voam para a
// cadeira de quem as levou.
function collectTrick(winnerSeat) {
  const alvo = winnerSeat == null ? null : $(POS_EL[posOf(winnerSeat)]);
  const felt = $('#tableFelt').getBoundingClientRect();
  const cx = felt.left + felt.width / 2, cy = felt.top + felt.height / 2;
  const a = (alvo || $('#tableFelt')).getBoundingClientRect();
  const ax = a.left + a.width / 2, ay = a.top + a.height / 2;
  const cartas = SLOT_EL.map((s) => $(s).firstElementChild).filter(Boolean);
  if (!cartas.length || reduceMotion()) { limparCasas(); render(); return; }
  S.holdUntil = Date.now() + 640;          // segura as casas enquanto voam
  let faltam = cartas.length;
  const acabou = () => { if (--faltam > 0) return; limparCasas(); render(); };
  cartas.forEach((cc, i) => {
    const r = cc.getBoundingClientRect();
    const x = r.left + r.width / 2, y = r.top + r.height / 2;
    const an = cc.animate([
      { transform: 'translate(0, 0) scale(1) rotate(0deg)', opacity: 1 },
      { transform: `translate(${cx - x}px, ${cy - y}px) scale(.72) rotate(${(i - 1.5) * 4}deg)`, opacity: 1, offset: .45 },
      { transform: `translate(${ax - x}px, ${ay - y}px) scale(.3)`, opacity: 0 },
    ], { duration: 460, delay: i * 40, easing: 'cubic-bezier(.45,.05,.55,.95)', fill: 'forwards' });
    an.onfinish = acabou;
    if (reduceMotion()) acabou();
  });
}

function limparCasas() {
  SLOT_EL.forEach((s) => {
    const el = $(s);
    el.innerHTML = ''; el.dataset.sig = ''; el.classList.remove('pop');
  });
  S.trickOn = 0; S.holdUntil = 0;
}

// Um poll pode chegar já com a vaza fechada e trazer só três das quatro cartas
// pintadas. As que faltam vem do lastTrick, para as quatro serem sempre vistas.
function completaVaza(g) {
  const lt = g.lastTrick;
  if (!lt?.plays?.length || S.trickOn === 0 || S.trickOn >= 4) return;
  const naMesa = SLOT_EL.map((s) => $(s).dataset.sig.split('-')[0]).filter(Boolean);
  if (!naMesa.length || !naMesa.every((c) => lt.cards.includes(c))) return;
  for (const p of lt.plays) {
    const el = $(SLOT_EL[posOf(p.seat)]);
    if (el.dataset.sig) continue;
    el.dataset.sig = `${p.card}-${g.trump}`;
    el.innerHTML = cardHTML(p.card, { trump: g.trump });
    S.trickOn++;
  }
}

// Pintar a carta no instante em que o evento chega. Com bots rápidos o poll
// seguinte já pode trazer a vaza fechada, e então a última carta nunca se via.
function placeOnTable(pos, card, mine = false) {
  const g = G();
  if (!g || pos < 0 || pos > 3 || Date.now() < S.holdUntil) return;
  if (S.trickOn >= 4) { holdTrick(G().lastTrick?.winner ?? null); return; }
  const el = $(SLOT_EL[pos]);
  const sig = `${card}-${g.trump}`;
  if (el.dataset.sig === sig) return;
  const vazia = !el.dataset.sig;
  el.dataset.sig = sig;
  el.innerHTML = cardHTML(card, { trump: g.trump });
  el.classList.remove('pop');
  if (vazia) S.trickOn++;
  landOnTable(el, pos, card, g.trump, mine);
}

// Carta normal: desliza da cadeira até à casa. Trunfo: cai de cima em zoom,
// salta na casa, rebentam grãos de café e ouve-se a pancada na mesa.
function landOnTable(el, pos, card, trump, mine) {
  const cc = el.firstElementChild;
  if (suitOf(card) === trump) {
    el.classList.add('pop');
    cardDrop(el, '#f0a63c');
    zoomDrop(cc);
    sfx.hit();
  } else {
    slideToTable(pos, cc);
    if (!mine) sfx.card();
  }
}

function renderTable(view) {
  const g = G();
  const slots = SLOT_EL.map((s) => $(s));
  if (!g) {
    clearTimeout(S.holdTimer); limparCasas();
    $('#trickCenter').innerHTML = '<span class="tc-turn">À espera…</span>';
    renderTurned(null);
    return;
  }
  const byPos = {};
  g.trick.forEach((p) => { byPos[posOf(p.seat)] = p; });
  const newest = g.trick.length ? posOf(g.trick[g.trick.length - 1].seat) : -1;
  if (!g.trick.length) completaVaza(g);
  // a vaza fechou: ou a vista já a esvaziou, ou já está na seguinte e as
  // quatro cartas foram pintadas pelos eventos — em ambos os casos ficam um
  // segundo na mesa antes de darem lugar à vaza nova
  const fechou = S.trickOn >= 4 && g.trick.length < 4;
  if (fechou) holdTrick(g.lastTrick?.winner ?? null);
  const frozen = Date.now() < S.holdUntil;
  if (!frozen) {
    // o deslize só acontece quando a vaza cresceu de uma carta — assim a
    // reposição da página não põe quatro cartas a voar ao mesmo tempo
    const slid = g.trick.length === S.trickOn + 1;
    slots.forEach((el, pos) => {
      const p = byPos[pos];
      const sig = p ? `${p.card}-${g.trump}` : '';
      if (el.dataset.sig === sig) return;
      el.dataset.sig = sig;
      el.innerHTML = p ? cardHTML(p.card, { trump: g.trump }) : '';
      el.classList.remove('pop');
      if (p && slid && pos === newest) landOnTable(el, pos, p.card, g.trump, pos === posOf(S.mySeat));
    });
  }
  S.trickOn = frozen ? 0 : g.trick.length;
  const center = [];
  // com cartas na mesa o centro é delas: a vez vê-se pela cadeira a pulsar
  const emMesa = g.trick.length > 0 || SLOT_EL.some((s) => $(s).firstElementChild);
  if (g.over) center.push('<span class="tc-turn">fim da mão</span>');
  else if (!emMesa && !g.yourTurn) {
    center.push(`<span class="tc-turn">${escapeHtml(view.seats[g.turn]?.name || '…')}</span><span class="tc-note">a pensar…</span>`);
  }
  const cEl = $('#trickCenter');
  const cSig = center.join('');
  if (cEl.dataset.sig !== cSig) { cEl.dataset.sig = cSig; cEl.innerHTML = cSig; }
  renderTurned(g);
}

// A carta sai da cadeira de quem a jogou e desliza até à sua casa na mesa.
function slideToTable(pos, card) {
  const from = $(POS_EL[pos]);
  if (!card || !from || reduceMotion()) return;
  const a = from.getBoundingClientRect();
  const b = card.getBoundingClientRect();
  const dx = a.left + a.width / 2 - (b.left + b.width / 2);
  const dy = a.top + a.height / 2 - (b.top + b.height / 2);
  card.animate(
    [{ transform: `translate(${dx}px, ${dy}px) rotate(-5deg) scale(.9)`, opacity: .9 },
     { transform: 'translate(0, 0) rotate(0deg) scale(1)', opacity: 1 }],
    { duration: 420, easing: 'cubic-bezier(.22,.61,.36,1)' },
  );
}
const reduceMotion = () => matchMedia('(prefers-reduced-motion: reduce)').matches;

// O trunfo não desliza: aparece grande por cima da casa e encolhe até bater.
function zoomDrop(card) {
  if (!card || reduceMotion()) return;
  card.animate(
    [{ transform: 'scale(2.4)', opacity: .35 }, { transform: 'scale(1)', opacity: 1 }],
    { duration: 260, easing: 'cubic-bezier(.16,.84,.28,1)' },
  );
}

// A carta que virou o trunfo encosta-se à cadeira de quem deu e recolhe-se à
// terceira vaza — a essa altura já todos sabem qual é o naipe.
function renderTurned(g) {
  const el = $('#turnedTag');
  const show = !!g && g.turnedLive && !!g.turned;
  const pos = show ? posOf(g.turnedSeat ?? g.dealer) : -1;
  const sig = show ? `${g.turned}-${g.trump}-${POS_NAME[pos]}` : '';
  if (el.dataset.sig === sig) return;
  el.dataset.sig = sig;
  el.className = show ? `turned on at-${POS_NAME[pos]}` : 'turned';
  el.innerHTML = show ? cardHTML(g.turned, { trump: g.trump, cls: 'mini' }) : '';
  if (show) el.title = `Trunfo virado: ${cardName(g.turned)}`;
}

/* ─────────── mesa: a tua mão ─────────── */
function renderHand(view) {
  const g = G();
  const el = $('#hand');
  if (!g) { el.innerHTML = ''; $('#handHint').textContent = 'À espera da mesa…'; return; }
  const legal = new Set(g.legal || []);
  const sig = JSON.stringify([g.yourHand, g.yourTurn ? [...legal].sort() : [], g.over]);
  if (sig === S.handSig) return;
  const firstPaint = !el.dataset.filled;
  el.dataset.filled = '1';
  S.handSig = sig;
  el.classList.toggle('waiting', !g.yourTurn || g.over);
  el.innerHTML = (g.yourHand || []).map((c, i) => cardHTML(c, {
    btn: true,
    trump: g.trump,
    val: true,
    playable: g.yourTurn && !g.over && legal.has(c),
    dim: g.yourTurn && !g.over && !legal.has(c),
    fresh: firstPaint,
    delay: firstPaint ? Math.min(i * 30, 300) : 0,
  })).join('');
  el.querySelectorAll('.cc').forEach((card) =>
    card.addEventListener('click', () => onCardTap(card.dataset.card, card.classList.contains('playable'), card)));
  if (g.over) $('#handHint').textContent = 'A mão acabou';
  else if (g.yourTurn) $('#handHint').textContent = legal.size === 1 ? 'Só tens uma carta legal' : 'Toca numa carta para a jogar';
  else $('#handHint').textContent = `${view.seats[g.turn]?.name || 'Outro'} está a jogar`;
}

async function onCardTap(card, ok, el) {
  const g = G();
  if (!g || g.over) return;
  if (!g.yourTurn) { toast('Ainda não é a tua vez.', 'warn'); sfx.error(); shake(el); return; }
  if (!ok) { toast('Tens de assistir ao naipe de saída.', 'warn'); sfx.error(); shake(el); return; }
  if (suitOf(card) !== g.trump) sfx.card();   // o trunfo dá o golpe dele ao cair na mesa
  el.classList.add('pick');
  const r = await S.mesa.act({ type: 'play', card });
  if (r?.error) { el.classList.remove('pick'); toast(r.error, 'warn'); sfx.error(); }
}
function shake(el) {
  el?.animate([{ transform: 'translateX(0)' }, { transform: 'translateX(-5px) rotate(-3deg)' },
    { transform: 'translateX(5px) rotate(3deg)' }, { transform: 'translateX(0)' }], { duration: 220 });
}

/* ─────────── mesa: marcador e registo ─────────── */
function renderScore(view) {
  const g = G();
  const me = myTeam();
  const other = me === 'a' ? 'b' : 'a';
  const target = view.rules.jogosParaGanhar;
  const block = (team, right) => {
    const games = view.match.games[team];
    const n = Math.min(target, 6);
    let sticks = '';
    for (let i = 0; i < n; i++) sticks += `<i class="tally${i < games ? ' on ' + team : ''}"></i>`;
    return `<span class="tp-dot" style="background:${teamOf(team).color}"></span>
      <span class="ss-name">${teamOf(team).name}</span>
      <span class="ss-games">${sticks}</span>${games > n ? `<b>${games}</b>` : ''}
      <span class="ss-pts">${g ? g.points[team] : 0}</span>`;
  };
  $('#ssMine').innerHTML = block(me, false);
  $('#ssTheirs').innerHTML = block(other, true);
  $('#ssHand').textContent = `Mão ${view.handNo}`;
  $('#ssNeed').textContent = `a ${target} jogos${view.rules.bandeiraFecha ? ' · bandeira fecha' : ''}`;
  $('#trumpChip').innerHTML = g ? `trunfo ${trumpChipHTML(g.trump)}` : '';
}

function renderLog(view) {
  const g = G();
  if (!g) return;
  const items = g.history.map((h, i) => ({ n: i + 1, h })).reverse();   // mais recente primeiro
  const shown = S.logMode === 'all' ? items : items.slice(0, 1);
  const rows = shown.map(({ n, h }) => {
    const cards = h.plays.map((p) =>
      `<span class="tl-mini s-${p.card[0]}${p.seat === h.winner ? ' win' : ''}">${RANK_INFO[p.card.slice(1)].label}${SUIT_INFO[p.card[0]].glyph}</span>`).join('');
    return `<div class="tl-row"><span class="tl-n">${n}</span><span class="tl-cards">${cards}</span>
      <span class="tl-pts">${h.points} p · ${teamOf(h.team).name}</span></div>`;
  }).join('');
  const sig = `${S.logMode}|${rows}`;
  if (sig === S.logSig) return;
  S.logSig = sig;
  $('#trickLog').innerHTML = rows
    ? `<div class="trick-log${S.logMode === 'all' ? '' : ' solo'}">${rows}</div>`
    : `<div class="trick-log"><div class="tl-row"><span class="empty">${
      S.logMode === 'all' ? 'As vazas desta mão aparecem aqui.' : 'Ainda não fechou nenhuma vaza.'
    }</span></div></div>`;
}

// escolher o registo: só a última vaza, em destaque, ou a mão toda
function paintLogToggle() {
  const all = S.logMode === 'all';
  $('#logLast').classList.toggle('sel', !all);
  $('#logAll').classList.toggle('sel', all);
  $('#logLast').setAttribute('aria-pressed', String(!all));
  $('#logAll').setAttribute('aria-pressed', String(all));
}
function setLogMode(mode) {
  if (S.logMode === mode) return;
  sfx.click();
  S.logMode = mode;
  localStorage.setItem('snc-logMode', mode);
  S.logSig = '';
  paintLogToggle();
  if (S.view && S.view.status !== 'lobby') renderLog(S.view);
}
$('#logLast').addEventListener('click', () => setLogMode('last'));
$('#logAll').addEventListener('click', () => setLogMode('all'));
paintLogToggle();

function renderBanner(view) {
  const g = G();
  const el = $('#turnBanner');
  let text = 'À espera da mesa…', cls = 'turn-banner theirs';
  if (g) {
    if (g.over) {
      text = view.match.winner ? 'Partida terminada' : 'Mão terminada';
      cls = 'turn-banner theirs';
    } else if (g.yourTurn) { text = 'É a tua vez'; cls = 'turn-banner mine'; }
    else { text = `${view.seats[g.turn]?.name || 'Adversário'} a jogar`; cls = 'turn-banner theirs'; }
  }
  if (el.textContent !== text) el.textContent = text;
  el.className = cls;
  $('#btnNextHand').classList.toggle('hidden', !(g && g.over && !view.match.winner));
  $('#btnRematch').classList.toggle('hidden', !(view.match.winner && view.host === S.mySeat));
}

function render() {
  const view = S.view;
  if (!view) return;
  if (view.status === 'lobby') { renderLobby(view); return; }
  renderScore(view);
  renderSeats(view);
  renderTable(view);
  renderHand(view);
  renderLog(view);
  renderBanner(view);
}

$('#btnNextHand').addEventListener('click', async () => { sfx.click(); closeModal(); await S.mesa.act({ type: 'next' }); });
$('#btnRematch').addEventListener('click', async () => { sfx.click(); closeModal(); await S.mesa.act({ type: 'rematch' }); });
document.querySelectorAll('.emote-btn').forEach((b) => b.addEventListener('click', () => {
  sfx.emote();
  flyEmote(b.dataset.emote, $('#seatBottom'));
  S.mesa?.act({ type: 'emote', text: b.dataset.emote });
}));

function flyEmote(text, nearEl) {
  const r = (nearEl || $('#tableFelt')).getBoundingClientRect();
  const d = document.createElement('div');
  d.className = 'fly-emote';
  d.textContent = text;
  d.style.left = `${r.left + r.width / 2 - 16}px`;
  d.style.top = `${r.top + r.height / 2}px`;
  $('#emoteLayer').appendChild(d);
  setTimeout(() => d.remove(), 1600);
}

/* ─────────── eventos do servidor ─────────── */
// O anfitrião decide o que fazer quando alguém larga a mesa a meio.
function openAwayModal(name, timeout) {
  openModal(`<h2 class="display sm">${timeout ? '⌛' : '🚪'} ${escapeHtml(name)} ${timeout ? 'desapareceu' : 'saiu'}</h2>
    <p class="help">A mão não pára: o parceiro automático joga por essa cadeira, que fica <b>guardada</b>. Se a mesma pessoa voltar com o mesmo link, retoma as cartas dela; também pode entrar outra pessoa no lugar.</p>
    <button class="btn btn-primary full mt" id="awayWait">Aguardar por alguém</button>
    <button class="btn btn-danger full mt" id="awayCancel">Cancelar a mesa</button>`);
  sfx.notify();
  $('#awayWait').addEventListener('click', () => { sfx.click(); closeModal(); });
  $('#awayCancel').addEventListener('click', async () => {
    sfx.click();
    closeModal();
    const r = await S.mesa.act({ type: 'cancel' });
    if (r?.error) { toast(r.error, 'warn'); sfx.error(); }
  });
}

function handleEvent(ev) {
  const view = S.view;
  if (!view) return;
  const seatEl = (seat) => $(POS_EL[posOf(seat)]) || $('#tableFelt');
  const nameOf = (i) => view.seats[i]?.name || 'Alguém';
  switch (ev.t) {
    case 'play': {
      placeOnTable(posOf(ev.seat), ev.card, ev.seat === S.mySeat);
      break;
    }
    case 'deal':
      sfx.deal();
      sfx.trump();
      dealSteam($('#tableFelt'));
      clearTimeout(S.holdTimer); limparCasas();
      S.seenTricks = 0;
      S.resultShownFor = '';
      closeModal();
      toast(`Mão ${ev.handNo} · trunfo ${SUIT_INFO[ev.trump].name}${ev.turned ? ` (virou o ${RANK_INFO[ev.turned.slice(1)].label})` : ''}`);
      break;
    case 'hand':
      showHandResult(ev);
      break;
    case 'joined':
      if (ev.seat !== S.mySeat) { toast(`${nameOf(ev.seat)} sentou-se.`); sfx.join(); }
      break;
    case 'team':
      if (ev.seat !== S.mySeat) toast(`${nameOf(ev.seat)} mudou de equipa.`);
      break;
    case 'left':
      toast(`${nameOf(ev.seat)} saiu da mesa.`);
      break;
    case 'gone': {
      if (ev.seat === S.mySeat) break;
      const who = ev.name || nameOf(ev.seat);
      const modalAberto = !$('#modal').classList.contains('hidden');
      if (view.host === S.mySeat && !modalAberto) openAwayModal(who, ev.timeout);
      else toast(`${who} ${ev.timeout ? 'desapareceu' : 'saiu'} — o automático joga por essa cadeira.`, 'warn');
      break;
    }
    case 'took':
      toast(`${ev.name || 'Outro jogador'} entrou na cadeira de ${nameOf(ev.seat)}.`);
      break;
    case 'host':
      if (ev.seat !== S.mySeat) toast(`${ev.name || nameOf(ev.seat)} passou a anfitrião da mesa.`);
      else toast('Ficaste como anfitrião da mesa.', 'warn');
      break;
    case 'botIn':
      // o lugar vazio já se vê na cadeira; só aviso quem tinha as cartas
      if (ev.seat === S.mySeat && ev.timeout) toast('Estavas ausente — o parceiro automático pegou nas tuas cartas.', 'warn');
      break;
    case 'back':
      if (ev.seat !== S.mySeat) toast(`${ev.name || nameOf(ev.seat)} voltou para a mesa.`);
      break;
    case 'emote':
      flyEmote(ev.text, seatEl(ev.seat));
      break;
    case 'start':
      S.resultShownFor = '';
      closeModal();
      show('game');
      sfx.notify();
      break;
    case 'rules':
      if (view.status === 'lobby') toast('O anfitrião mexeu nas regras da casa.');
      break;
    case 'closed':
      toast(ev.reason === 'cancel' ? 'O anfitrião fechou a mesa.' : 'A mesa fechou.');
      resetToHome();
      return;
    default: break;
  }
  celebrateTrick();
}

// o vencedor de cada vaza ganha um rebentamento na sua cadeira
function celebrateTrick() {
  const g = G();
  if (!g || !g.tricks) return;
  const done = g.tricks.a + g.tricks.b;
  if (done <= S.seenTricks) { S.seenTricks = done; return; }
  S.seenTricks = done;
  const last = g.lastTrick;
  if (!last) return;
  const seat = last.winner;
  const ours = teamOfSeat(seat) === myTeam();
  sfx.trick(ours);
  trickWin($(POS_EL[posOf(seat)]) || $('#tableFelt'), teamOf(teamOfSeat(seat)).color);
}

function matchLine(games, target) {
  return `${teamOf('a').name} ${games.a} — ${games.b} ${teamOf('b').name} · a ${target} jogos`;
}

function showHandResult(ev) {
  const res = ev.result;
  const view = S.view;
  const key = `hand-${view.handNo}`;
  if (S.resultShownFor === key) return;
  S.resultShownFor = key;
  const games = ev.match || view.match.games;
  const ours = res.winner === myTeam();
  const score = `<div class="result-score">
      <div class="rs-team"><div class="rs-pts">${res.points.a}</div><div class="rs-label">${teamOf('a').name}</div></div>
      <span class="rs-x">·</span>
      <div class="rs-team"><div class="rs-pts">${res.points.b}</div><div class="rs-label">${teamOf('b').name}</div></div>
    </div>
    <div class="rs-bar"><i style="width:${(res.points.a / 120) * 100}%;background:${teamOf('a').color}"></i>
      <i style="flex:1;background:${teamOf('b').color}"></i></div>`;

  if (ev.winner) {
    const champ = ev.winner === myTeam();
    if (champ) winConfetti();
    champ ? sfx.win() : sfx.lose();
    openModal(`<div class="result-head">${champ ? '🏆' : '☕'}</div>
      <h2>${champ ? 'Ganhaste a partida!' : `${teamOf(ev.winner).name} ganha a partida`}</h2>
      <p class="result-line">${ev.via === 'bandeira' ? 'Com bandeira: 120 pontos de uma vez!' : matchLine(games, view.rules.jogosParaGanhar)}</p>
      ${score}
      <p class="result-line">Jogos: ${games.a} — ${games.b}</p>
      ${view.host === S.mySeat
        ? '<button class="btn btn-primary full" id="mRematch">Nova partida</button>'
        : '<p class="countdown">À espera que o anfitrião abra nova partida…</p>'}
      <button class="btn btn-ghost full mt" id="mClose">Ver a mesa</button>`, true);
    $('#mRematch')?.addEventListener('click', async () => { sfx.click(); closeModal(); await S.mesa.act({ type: 'rematch' }); });
  } else {
    ours ? sfx.win() : sfx.lose();
    openModal(`<div class="result-head">${res.winner === myTeam() ? '🎉' : res.winner ? '😖' : '🤝'}</div>
      <h2>${res.winner ? `${teamOf(res.winner).name} leva a mão ${view.handNo}` : 'Empate a 60'}</h2>
      <p class="result-line">${res.label}</p>
      ${score}
      <p class="result-line">Jogos: ${games.a} — ${games.b} · ${matchLine(games, view.rules.jogosParaGanhar)}</p>
      <p class="countdown" id="nextIn"></p>
      <button class="btn btn-primary full" id="mNext">Mão seguinte</button>
      <button class="btn btn-ghost full mt" id="mClose">Ver a mesa</button>`, true);
    $('#mNext').addEventListener('click', async () => { sfx.click(); closeModal(); await S.mesa.act({ type: 'next' }); });
    const at = ev.at || (Date.now() + 14000);
    const label = $('#nextIn');
    clearInterval(S.countdownTimer);
    const tick = () => {
      const left = Math.max(0, Math.round((at - Date.now()) / 1000));
      label.textContent = left > 0 ? `O servidor baralha outra mão em ${left}s` : 'A baralhar…';
      if (left <= 0) clearInterval(S.countdownTimer);
    };
    tick();
    S.countdownTimer = setInterval(tick, 1000);
  }
  $('#mClose').addEventListener('click', () => { sfx.click(); closeModal(); });
}

/* ─────────── arranque ─────────── */
async function resumeOrHome() {
  const code = new URLSearchParams(location.search).get('mesa');
  const saved = JSON.parse(sessionStorage.getItem('snc-session') || 'null');
  if (saved?.tk && (!code || saved.id === code)) {
    wireMesa(new Mesa(saved.base, saved.id, saved.seat, saved.tk));
    return;
  }
  if (code) {
    $('#mesaInput').value = code;
    show('join');
    await loadRooms();
    if (roomCache.has(code)) joinEntry('', code, roomCache.get(code).info);
    return;
  }
  show('home');
  loadRooms().catch(() => { /* sem anunciador neste servidor estático */ });
}
document.addEventListener('visibilitychange', () => {
  if (!document.hidden) S.mesa?.resume();
});

// Service worker só em contexto seguro (https ou localhost); em http puro o browser recusa.
if ('serviceWorker' in navigator && (isSecureContext || location.hostname === 'localhost')) {
  addEventListener('load', () => {
    navigator.serviceWorker.register('sw.js').catch(() => { /* sem cache, o jogo continua online */ });
  });
}

resumeOrHome();
