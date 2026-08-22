/* NexusChat Service Worker — PWA offline + caching */
'use strict';

const CACHE   = 'nexuschat-v1';
const ASSETS  = [
  './',
  './index.html',
  './app.js',
  'https://cdnjs.cloudflare.com/ajax/libs/libsodium-wrappers/0.7.13/sodium.js',
  'https://cdnjs.cloudflare.com/ajax/libs/tweetnacl/1.0.3/nacl-fast.min.js',
  'https://cdnjs.cloudflare.com/ajax/libs/tweetnacl-util/0.15.1/nacl-util.min.js',
  'https://cdnjs.cloudflare.com/ajax/libs/Chart.js/4.4.1/chart.umd.min.js',
  'https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js',
  'https://cdnjs.cloudflare.com/ajax/libs/lz-string/1.5.0/lz-string.min.js',
];

self.addEventListener('install', e => {
  e.waitUntil(
    caches.open(CACHE).then(c => c.addAll(ASSETS.filter(a => !a.startsWith('http'))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', e => {
  // Network first for API calls, cache first for assets
  const url = new URL(e.request.url);
  if (url.hostname === 'api.tailscale.com' || url.hostname === 'api.ipify.org') {
    e.respondWith(fetch(e.request).catch(() => new Response('{"error":"offline"}',
      { headers:{'Content-Type':'application/json'} })));
    return;
  }
  e.respondWith(
    caches.match(e.request).then(cached => {
      if (cached) return cached;
      return fetch(e.request).then(r => {
        if (r.ok && e.request.method === 'GET') {
          const clone = r.clone();
          caches.open(CACHE).then(c => c.put(e.request, clone));
        }
        return r;
      }).catch(() => caches.match('./index.html'));
    })
  );
});

// Push notifications
self.addEventListener('push', e => {
  const data = e.data?.json() || { title:'NexusChat', body:'New message' };
  e.waitUntil(
    self.registration.showNotification(data.title, {
      body:    data.body,
      icon:    'icon-192.png',
      badge:   'icon-72.png',
      tag:     'nexuschat-msg',
      vibrate: [100,50,100],
      data:    { url: './' }
    })
  );
});

self.addEventListener('notificationclick', e => {
  e.notification.close();
  e.waitUntil(clients.openWindow(e.notification.data?.url || './'));
});
