// Ligação às mesas da Sueca no Café (Wi-Fi local).
// O servidor é o árbitro: distribui as cartas e responde pelo estado da mesa.
// Cada telemóvel só recebe a própria mão.
async function api(base, path, opts = {}) {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), opts.timeout ?? 6000);
  try {
    const res = await fetch(base + path, {
      method: opts.method || 'GET',
      headers: opts.body ? { 'content-type': 'application/json' } : undefined,
      body: opts.body ? JSON.stringify(opts.body) : undefined,
      signal: ctrl.signal,
    });
    const data = await res.json().catch(() => null);
    if (!res.ok) throw new Error(data?.error || 'Erro ' + res.status);
    return data;
  } finally { clearTimeout(timer); }
}

export const fetchInfo = (base = '') => api(base, '/api/info');
export const fetchRooms = (base = '') => api(base, '/api/rooms').then((d) => d.rooms);
export const createRoom = (base, name, player) => api(base, '/api/rooms', { method: 'POST', body: { name, player } });
export const joinRoom = (base, id, player) => api(base, `/api/rooms/${id}/join`, { method: 'POST', body: player });

export const roomUrl = (base, id) => `${base}/?mesa=${id}`;
export const parseMesaInput = (text) => {
  const t = String(text || '').trim().toUpperCase();
  return /^[A-Z0-9]{4}$/.test(t) ? t : null;
};

export function subnetPrefix(hostname) {
  const m = String(hostname).match(/^(\d+\.\d+\.\d+)\.\d+$/);
  if (!m || m[1].startsWith('127.')) return null;
  return m[1];
}

// A mesa vive num ciclo de perguntas-respostas: sem websockets, funciona em
// qualquer browser do café e aguenta o tab em segundo plano nos telemóveis.
export class Mesa {
  constructor(base, id, seat, tk) {
    this.base = base; this.id = id; this.seat = seat; this.tk = tk;
    this.since = 0; this.stopped = false; this.busy = false; this.fails = 0; this.timer = 0;
    this.onView = null; this.onEvents = null; this.onState = null; this.onClose = null;
  }
  start() {
    this.onState?.('connecting');
    this._tick();
  }
  // Volta ao polling rápido quando o telemóvel volta para o ecrã.
  resume() {
    if (this.stopped || this.busy) return;
    clearTimeout(this.timer);
    this._tick();
  }
  // Mudar de equipa na sala troca o lugar; a credencial é a mesma.
  moveTo(seat) { this.seat = seat; }
  async _tick() {
    if (this.stopped) return;
    this.busy = true;
    let delay = document.hidden ? 1500 : 420;
    try {
      const d = await api(this.base,
        `/api/rooms/${this.id}/state?seat=${this.seat}&tk=${this.tk}&since=${this.since}`, { timeout: 7000 });
      this.fails = 0;
      this.onState?.('connected');
      if (d.view?.status === 'closed') { this.stopped = true; this.onClose?.(); return; }
      if (d.events?.length) {
        this.since = Math.max(this.since, ...d.events.map((e) => e.seq));
        this.onEvents?.(d.events);
      }
      this.onView?.(d.view);
    } catch (err) {
      if (/409|403|não existe|Saíste|Credencial/.test(String(err.message))) {
        this.stopped = true;
        this.onClose?.(err.message);
        return;
      }
      if (++this.fails >= 5) this.onState?.('offline');
      else this.onState?.('weak');
      delay = Math.min(4000, delay * 2);
    }
    this.busy = false;
    this.timer = setTimeout(() => this._tick(), delay);
  }
  async act(body) {
    try {
      const d = await api(this.base, `/api/rooms/${this.id}/action`, {
        method: 'POST', body: { seat: this.seat, tk: this.tk, ...body },
      });
      if (d.view) this.onView?.(d.view);
      if (d.error) return d;
      return d;
    } catch (err) {
      return { error: err.message || 'Sem ligação ao servidor.' };
    }
  }
  close() {
    this.stopped = true;
    clearTimeout(this.timer);
    try {
      navigator.sendBeacon?.(this.base + `/api/rooms/${this.id}/action`,
        new Blob([JSON.stringify({ seat: this.seat, tk: this.tk, type: 'leave' })], { type: 'application/json' }));
    } catch { /* Paz, o servidor dá pelo jeito pelo heartbeat. */ }
  }
}

// Varre a sub-rede à procura de máquinas com o anunciador da Sueca.
export async function scanNetwork(port, onProgress) {
  const sub = subnetPrefix(location.hostname);
  const found = [];
  if (!sub) return found;
  const ips = Array.from({ length: 254 }, (_, i) => `${sub}.${i + 1}`);
  let done = 0;
  for (let i = 0; i < ips.length; i += 24) {
    await Promise.all(ips.slice(i, i + 24).map(async (ip) => {
      const base = `http://${ip}:${port}`;
      try {
        const ctrl = new AbortController();
        const t = setTimeout(() => ctrl.abort(), 700);
        const res = await fetch(base + '/api/info', { signal: ctrl.signal });
        clearTimeout(t);
        if (res.ok) {
          const d = await res.json().catch(() => null);
          if (d?.app === 'sueca-no-cafe') found.push(base);
        }
      } catch { /* não há nada neste IP */ }
      done++; onProgress?.(done, ips.length);
    }));
  }
  return found;
}
