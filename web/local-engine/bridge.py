"""Loopback-only static app and explicit Ollama / StudyForge engine proxy.

No model downloads. Optional STUDYFORGE_PUBLIC_HOST is the exact private
Tailscale Serve hostname. STUDYFORGE_TOKEN is kept on the computer, never in JS.
"""
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from urllib.request import Request, build_opener, HTTPRedirectHandler
from urllib.error import HTTPError
from urllib.parse import urlsplit, unquote
from pathlib import Path
import json
import os
import re

ROOT=Path(__file__).resolve().parents[2]
OLLAMA="http://127.0.0.1:11434"
ENGINE="http://127.0.0.1:8765"
STATIC={"/index.html","/manifest.webmanifest","/sw.js","/web/app.js","/web/styles.css","/web/local-engine/ui.js","/branding/studyfrog-icon-1024.png"}
PUBLIC_HOST=os.environ.get("STUDYFORGE_PUBLIC_HOST","").lower()
if PUBLIC_HOST and not re.fullmatch(r"[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.[a-z0-9-]+\.ts\.net",PUBLIC_HOST):
    raise ValueError("STUDYFORGE_PUBLIC_HOST must be your exact private Tailscale Serve hostname")

class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs): return None
OPENER=build_opener(NoRedirect())

def allowed(peer,host,origin):
    authorities={"127.0.0.1:8766","localhost:8766"}
    origins={"http://127.0.0.1:8766","http://localhost:8766"}
    if PUBLIC_HOST: authorities.update((PUBLIC_HOST,PUBLIC_HOST+":443"));origins.add("https://"+PUBLIC_HOST)
    return peer in ("127.0.0.1","::1") and host in authorities and (not origin or origin in origins and urlsplit(origin).netloc==host)

def route(method,path):
    if path=="/engine/health" and method=="GET": return "/health"
    if path=="/engine/ocr" and method=="POST": return "/ocr"
    if path=="/engine/jobs" and method in ("POST","GET"): return "/jobs"
    if re.fullmatch(r"/engine/(?:jobs|results)/[0-9a-f]{32}",path) and method in ("GET","DELETE"):
        if method=="DELETE" and not path.startswith("/engine/jobs/"): return None
        return path.removeprefix("/engine")
    if re.fullmatch(r"/engine/jobs/[0-9a-f]{32}/cancel",path) and method=="POST": return path.removeprefix("/engine")
    return None

class Handler(SimpleHTTPRequestHandler):
    def __init__(self,*args,**kwargs): super().__init__(*args,directory=str(ROOT),**kwargs)
    def log_message(self,*args): pass
    def allowed(self): return allowed(self.client_address[0],self.headers.get("Host",""),self.headers.get("Origin",""))
    def end_headers(self):
        self.send_header("X-Content-Type-Options","nosniff")
        self.send_header("Referrer-Policy","no-referrer")
        self.send_header("Content-Security-Policy","default-src 'self'; img-src 'self' data: blob:; media-src 'self' blob:; connect-src 'self'; style-src 'self'; script-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")
        super().end_headers()
    def reply(self,code,data):
        payload=json.dumps(data).encode();self.send_response(code)
        self.send_header("Content-Type","application/json");self.send_header("Cache-Control","no-store")
        self.send_header("Content-Length",str(len(payload)));self.end_headers();self.wfile.write(payload)
    def body(self,maximum):
        if self.headers.get_content_type()!="application/json": raise ValueError("Use application/json")
        n=int(self.headers.get("Content-Length","0"))
        if not 2<=n<=maximum: raise ValueError("Invalid body size")
        raw=self.rfile.read(n)
        if len(raw)!=n: raise ValueError("Incomplete request")
        value=json.loads(raw)
        if not isinstance(value,dict): raise ValueError("Expected JSON object")
        return raw,value
    def proxy(self,method,path):
        token=os.environ.get("STUDYFORGE_TOKEN","")
        if not token: return self.reply(503,{"error":"Set STUDYFORGE_TOKEN to the same token as your local SD engine, then restart the bridge"})
        try:
            body=self.body(13_000_000)[0] if method=="POST" else None
            request=Request(ENGINE+path,data=body,method=method,headers={"Authorization":"Bearer "+token,"Content-Type":"application/json"})
            with OPENER.open(request,timeout=35) as response:
                size=int(response.headers.get("Content-Length","0"));maximum=256_000_000 if path.startswith("/results/") else 1_000_000
                if not 0<size<=maximum: raise ValueError("Invalid engine response size")
                self.send_response(response.status);self.send_header("Content-Type",response.headers.get("Content-Type","application/octet-stream"))
                self.send_header("Content-Length",str(size));self.send_header("Cache-Control","no-store");self.end_headers()
                while size:
                    block=response.read(min(size,65536))
                    if not block: break
                    self.wfile.write(block);size-=len(block)
        except HTTPError as exc:
            # Never follow redirects, disclose engine paths, or forward credentials elsewhere.
            if 400<=exc.code<600:
                try: message=json.loads(exc.read(1000)).get("error","Local engine request failed")
                except (ValueError,AttributeError): message="Local engine request failed"
                self.reply(exc.code,{"error":str(message)[:300]})
            else: self.reply(502,{"error":"Engine redirect rejected"})
        except ValueError as exc: self.reply(400,{"error":str(exc)[:200]})
        except OSError: self.reply(502,{"error":"Local SD engine unavailable; check port 8765 and token"})
    def do_HEAD(self):
        if not self.allowed(): return self.reply(403,{"error":"Private local app only"})
        path=unquote(urlsplit(self.path).path)
        if path=="/": path="/index.html"
        if path not in STATIC: return self.reply(404,{"error":"Not found"})
        self.path=path;super().do_HEAD()
    def do_GET(self):
        if not self.allowed(): return self.reply(403,{"error":"Private local app only"})
        path=urlsplit(self.path).path
        if target:=route("GET",path): return self.proxy("GET",target)
        if path!="/health":
            path=unquote(path)
            if path=="/": path="/index.html"
            if path not in STATIC: return self.reply(404,{"error":"Not found"})
            self.path=path;return super().do_GET()
        try:
            with OPENER.open(OLLAMA+"/api/tags",timeout=5) as response: models=json.loads(response.read(1_000_000)).get("models",[])
            self.reply(200,{"models":[m["name"] for m in models if m.get("name")]})
        except (OSError,ValueError): self.reply(503,{"error":"Ollama unavailable"})
    def do_DELETE(self):
        if not self.allowed(): return self.reply(403,{"error":"Private local app only"})
        target=route("DELETE",urlsplit(self.path).path)
        if target: return self.proxy("DELETE",target)
        self.reply(404,{"error":"Not found"})
    def do_POST(self):
        if not self.allowed(): return self.reply(403,{"error":"Private local app only"})
        path=urlsplit(self.path).path
        if target:=route("POST",path): return self.proxy("POST",target)
        if path!="/generate": return self.reply(404,{"error":"Not found"})
        try:
            _,data=self.body(32768);prompt,model=data.get("prompt"),data.get("model")
            if not isinstance(prompt,str) or not 0<len(prompt.strip())<=6000: raise ValueError("Invalid prompt")
            if not isinstance(model,str) or not 0<len(model)<=150: raise ValueError("Invalid model")
            settings={}
            for key,default,low,high in (("width",512,256,2048),("height",512,256,2048),("steps",20,1,100)):
                value=data.get(key,default)
                if type(value) is not int or not low<=value<=high: raise ValueError("Invalid generation settings")
                settings[key]=value
            body=json.dumps({"model":model,"prompt":prompt,**settings,"stream":False}).encode()
            with OPENER.open(Request(OLLAMA+"/api/generate",data=body,headers={"Content-Type":"application/json"}),timeout=600) as response:
                raw=response.read(24_000_001)
                if len(raw)>24_000_000: raise ValueError("Generated image exceeds response limit")
                result=json.loads(raw)
            images=result.get("images") or ([result["image"]] if result.get("image") else [])
            if not images: return self.reply(502,{"error":"Model returned no image. Use a compatible image model."})
            self.reply(200,{"image":images[0]})
        except (ValueError,TypeError,KeyError) as exc: self.reply(400,{"error":str(exc)[:200]})
        except OSError: self.reply(502,{"error":"Local generation failed. Check Ollama logs."})

class Server(ThreadingHTTPServer):
    daemon_threads=True
    def get_request(self):
        connection,address=super().get_request();connection.settimeout(45);return connection,address

if __name__=="__main__":
    print("Study Frog local app: http://127.0.0.1:8766/")
    Server(("127.0.0.1",8766),Handler).serve_forever()
