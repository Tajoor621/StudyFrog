const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const path=require('node:path');
const handlers={},deleted=[],cached=[];
const context={URL,Set,Promise,self:{location:{href:'https://example.com/StudyFrog/sw.js'},addEventListener:(name,fn)=>handlers[name]=fn,clients:{claim:async()=>{}},skipWaiting:async()=>{}},caches:{keys:async()=>['study-frog-web-v1','unrelated-app-cache'],delete:async key=>deleted.push(key),open:async()=>({put:async(request)=>cached.push(request.url)})},fetch:async()=>({ok:true,clone:()=>({})})};
vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../../sw.js'),'utf8'),context);
(async()=>{
 let activation;handlers.activate({waitUntil:promise=>activation=promise});await activation;
 assert.deepEqual(deleted,['study-frog-web-v1']);
 for(const route of ['/StudyFrog/engine/jobs','/StudyFrog/engine/results/abc','/engine/health','/engine/ocr','/StudyFrog/private.png']){
  handlers.fetch({request:{url:'https://example.com'+route,method:'GET'},respondWith:()=>assert.fail('Private data must bypass service worker'),waitUntil:()=>assert.fail('Private data must not be cached')});
 }
 let response,cacheWrite;
 handlers.fetch({request:{url:'https://example.com/StudyFrog/web/app.js',method:'GET'},respondWith:p=>response=p,waitUntil:p=>cacheWrite=p});await response;await cacheWrite;
 assert.deepEqual(cached,['https://example.com/StudyFrog/web/app.js']);
 console.log('Service worker privacy: static caching, private bypass, scoped cleanup passed');
})().catch(error=>{console.error(error);process.exitCode=1;});
