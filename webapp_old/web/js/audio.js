// Som sintetizado — apenas SFX ligados a eventos do jogo (sem música de fundo).
let ctx = null, master = null;
let muted = localStorage.getItem('snc-muted') === '1';

function ensure() {
  if (!ctx) {
    ctx = new (window.AudioContext || window.webkitAudioContext)();
    master = ctx.createGain();
    master.gain.value = muted ? 0 : 1;
    master.connect(ctx.destination);
  }
  if (ctx.state === 'suspended') ctx.resume();
}
document.addEventListener('pointerdown', ensure, { once: false });

function env(t, dur, peak = 0.2) {
  const g = ctx.createGain();
  g.gain.setValueAtTime(0.0001, t);
  g.gain.exponentialRampToValueAtTime(peak, t + 0.008);
  g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  g.connect(master);
  return g;
}

function tone(freq, dur, type = 'sine', peak = 0.2, delay = 0, slideTo = null) {
  if (!ctx || muted) return;
  const t = ctx.currentTime + delay;
  const o = ctx.createOscillator();
  o.type = type; o.frequency.setValueAtTime(freq, t);
  if (slideTo) o.frequency.exponentialRampToValueAtTime(slideTo, t + dur);
  o.connect(env(t, dur, peak));
  o.start(t); o.stop(t + dur + 0.05);
}

function noise(dur, filterFreq, type = 'lowpass', peak = 0.25, delay = 0) {
  if (!ctx || muted) return;
  const t = ctx.currentTime + delay;
  const len = Math.ceil(ctx.sampleRate * dur);
  const buf = ctx.createBuffer(1, len, ctx.sampleRate);
  const d = buf.getChannelData(0);
  for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * (1 - i / len);
  const src = ctx.createBufferSource(); src.buffer = buf;
  const f = ctx.createBiquadFilter(); f.type = type; f.frequency.value = filterFreq;
  src.connect(f).connect(env(t, dur, peak));
  src.start(t);
}

export const sfx = {
  click() { ensure(); tone(660, 0.05, 'square', 0.06); },
  // carta a bater na mesa: o farfalhar do baralho
  card() { ensure(); noise(0.07, 2600, 'highpass', 0.16); tone(210, 0.07, 'triangle', 0.14, 0, 140); },
  // o trunfo cai com uma pancada seca na madeira
  hit() {
    ensure();
    noise(0.13, 800, 'lowpass', 0.5);
    tone(130, 0.18, 'sine', 0.5, 0, 58);
    tone(300, 0.05, 'square', 0.14, 0, 140);
  },
  deal() { ensure(); for (let i = 0; i < 5; i++) noise(0.05, 2200, 'highpass', 0.1, i * 0.05); },
  trump() { ensure(); [659, 880, 1175].forEach((f, i) => tone(f, 0.22, 'triangle', 0.14, i * 0.07)); },
  trick(mine) {
    ensure();
    if (mine) [523, 784].forEach((f, i) => tone(f, 0.16, 'triangle', 0.16, i * 0.08));
    else tone(300, 0.12, 'sine', 0.1, 0, 220);
  },
  win() {
    ensure();
    [523, 659, 784, 1047, 784, 1047, 1319].forEach((f, i) => tone(f, 0.26, 'triangle', 0.2, i * 0.12));
    noise(0.5, 5000, 'highpass', 0.05, 0.8);
  },
  lose() { ensure(); [392, 330, 262].forEach((f, i) => tone(f, 0.3, 'sine', 0.16, i * 0.16)); },
  draw() { ensure(); tone(440, 0.22, 'triangle', 0.16); tone(440, 0.22, 'triangle', 0.16, 0.22); },
  notify() { ensure(); tone(880, 0.09, 'sine', 0.14); tone(1175, 0.12, 'sine', 0.12, 0.08); },
  join() { ensure(); tone(520, 0.1, 'triangle', 0.16); tone(780, 0.14, 'triangle', 0.14, 0.09); },
  emote() { ensure(); tone(500, 0.14, 'sine', 0.16, 0, 1100); },
  error() { ensure(); tone(180, 0.14, 'square', 0.1); tone(140, 0.16, 'square', 0.1, 0.1); },
};

export function isMuted() { return muted; }
export function setMuted(m) {
  muted = m;
  localStorage.setItem('snc-muted', m ? '1' : '0');
  ensure();
  if (master) master.gain.setTargetAtTime(m ? 0 : 1, ctx.currentTime, 0.03);
}
