const CACHE = 'n3-v1';
self.addEventListener('install', e => { self.skipWaiting(); });
self.addEventListener('activate', e => { e.waitUntil(clients.claim()); });
self.addEventListener('fetch', e => {
  if (e.request.url.startsWith('file://')) return;
  e.respondWith(caches.match(e.request).then(r => r || fetch(e.request)));
});
