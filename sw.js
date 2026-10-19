const CACHE='study-frog-web-v2';
const FILES=['./','./index.html','./web/styles.css','./web/app.js','./web/local-engine/ui.js','./manifest.webmanifest','./branding/studyfrog-icon-1024.png'];
const STATIC=new Set(FILES.map(path=>new URL(path,self.location.href).href));
self.addEventListener('install',event=>event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(FILES)).then(()=>self.skipWaiting())));
self.addEventListener('activate',event=>event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(key=>key.startsWith('study-frog-web-')&&key!==CACHE).map(key=>caches.delete(key)))).then(()=>self.clients.claim())));
// Never cache prompts, OCR, health, job history, generated images or videos.
self.addEventListener('fetch',event=>{
 if(event.request.method!=='GET'||!STATIC.has(event.request.url))return;
 event.respondWith(fetch(event.request).then(response=>{
  if(response.ok){const copy=response.clone();event.waitUntil(caches.open(CACHE).then(cache=>cache.put(event.request,copy)));}
  return response;
 }).catch(()=>caches.match(event.request)));
});
