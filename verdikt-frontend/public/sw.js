const CACHE_NAME = 'verdikt-app-shell-v3';
const APP_SHELL = [
  '/',
  '/manifest.webmanifest',
  '/favicon.svg',
  '/verdikt-mark.svg',
  '/verdikt-icon-192.png',
  '/verdikt-icon-512.png',
  '/verdikt-icon-maskable-512.png',
  '/apple-touch-icon.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(Promise.all([
    caches.open(CACHE_NAME).then((cache) => cache.addAll(APP_SHELL)),
    self.skipWaiting(),
  ]));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(Promise.all([
    caches.keys().then((keys) => Promise.all(
      keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key)),
    )),
    self.clients.claim(),
  ]));
});

self.addEventListener('message', (event) => {
  if (event.data?.type !== 'CACHE_URLS' || !Array.isArray(event.data.urls)) return;

  const urls = event.data.urls.filter((url) => {
    try {
      const assetUrl = new URL(url, self.location.origin);
      return assetUrl.origin === self.location.origin
        && (assetUrl.pathname === '/' || assetUrl.pathname === '/manifest.webmanifest'
          || ['/favicon.svg', '/verdikt-mark.svg', '/verdikt-icon-192.png', '/verdikt-icon-512.png',
            '/verdikt-icon-maskable-512.png', '/apple-touch-icon.png'].includes(assetUrl.pathname)
          || assetUrl.pathname.startsWith('/assets/'));
    } catch {
      return false;
    }
  });

  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => Promise.allSettled(
      urls.map((url) => cache.add(url)),
    )),
  );
});

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET') return;

  const requestUrl = new URL(event.request.url);
  if (requestUrl.origin !== self.location.origin) return;

  if (event.request.mode === 'navigate') {
    event.respondWith(
      fetch(event.request)
        .then((response) => {
          const copy = response.clone();
          void caches.open(CACHE_NAME).then((cache) => cache.put('/', copy));
          return response;
        })
        .catch(() => caches.match('/')),
    );
    return;
  }

  if (!['font', 'image', 'manifest', 'script', 'style'].includes(event.request.destination)) return;

  event.respondWith(
    caches.match(event.request).then((cached) => cached || fetch(event.request).then((response) => {
      if (response.ok) {
        const copy = response.clone();
        void caches.open(CACHE_NAME).then((cache) => cache.put(event.request, copy));
      }
      return response;
    })),
  );
});
