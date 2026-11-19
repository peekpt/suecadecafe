// Service worker — cache da aplicação. Funciona em https ou localhost;
// em http puro (IP do café) o browser não regista SW, mas o resto funciona.
const VERSION = 'sueca-cafe-v14';
const ASSETS = [
  'index.html',
  'css/style.css',
  'css/sueca.css',
  'js/app.js',
  'js/engine.js',
  'js/bots.js',
  'js/net.js',
  'js/cards.js',
  'js/audio.js',
  'js/fx.js',
  'js/avatars.js',
  'vendor/avatars.js',
  'vendor/baloo2.woff2',
  'vendor/luckiest-guy.woff2',
  'icons/icon-192.png',
  'icons/icon-512.png',
  'icons/maskable-192.png',
  'icons/maskable-512.png',
  'icons/apple-touch-icon.png',
  'manifest.webmanifest',
];

self.addEventListener('install', (e) => {
  e.waitUntil(
    caches.open(VERSION).then((c) => c.addAll(ASSETS)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== self.location.origin) return;
  // o estado das mesas têm de vir sempre do servidor
  if (url.pathname.startsWith('/api/')) return;

  const isPage = e.request.mode === 'navigate' || url.pathname.endsWith('.html') || url.pathname === '/';
  e.respondWith(
    caches.open(VERSION).then(async (cache) => {
      if (isPage) {
        // rede primeiro: o HTML nunca fica preso a uma versão antiga
        try {
          const res = await fetch(e.request);
          if (res.ok) cache.put(url.pathname, res.clone());
          return res;
        } catch { return cache.match(url.pathname) || cache.match('/index.html'); }
      }
      // rede primeiro também no resto: numa mesa de café o servidor está ali ao lado,
      // e uma carta mal desenhada vale mais que 0.1 s de arranque. A cache fica para
      // quando a ligação falha (telemóvel em modo avião, servidor desligado).
      const key = url.pathname;
      try {
        const res = await fetch(e.request);
        if (res.ok) cache.put(key, res.clone());
        return res;
      } catch {
        return cache.match(key);
      }
    })
  );
});
