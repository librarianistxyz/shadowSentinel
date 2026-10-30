// Shadow Service Worker (sw.js)
// High-Speed Local Caching for Offline Resilience & Sub-Second Quiz Recall

const CACHE_NAME = 'shadow-edge-v2';
const STATIC_ASSETS = [
  '/',
  '/index.html',
  '/style.css',
  '/app.js',
  '/eye_geometry.js',
  '/manifest.json',
  '/eyes.png'
];

// Install Event: Pre-cache core client application assets
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      console.log('[SW] Pre-caching static assets for instant offline load');
      return cache.addAll(STATIC_ASSETS);
    }).then(() => self.skipWaiting())
  );
});

// Activate Event: Clean up stale caches
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(
        cacheNames.map((name) => {
          if (name !== CACHE_NAME && !name.startsWith('shadow-quiz-')) {
            console.log('[SW] Deleting old cache:', name);
            return caches.delete(name);
          }
        })
      );
    }).then(() => self.clients.claim())
  );
});

// Fetch Event: Cache-First for static assets; Network-First with Local Cache for API
self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);

  // 1. Static Assets (app.js, styles, html) -> Cache First with Network Revalidation
  if (event.request.method === 'GET' && !url.pathname.startsWith('/api/')) {
    event.respondWith(
      caches.match(event.request).then((cached) => {
        if (cached) {
          // Revalidate in background
          fetch(event.request).then((networkRes) => {
            if (networkRes.ok) {
              caches.open(CACHE_NAME).then((cache) => cache.put(event.request, networkRes));
            }
          }).catch(() => {});
          return cached;
        }
        return fetch(event.request);
      })
    );
    return;
  }

  // 2. TTS Audio & Dynamic Responses -> Network with Fallback
  event.respondWith(fetch(event.request));
});
