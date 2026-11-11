"""Loopback-only static app and explicit Ollama / StudyForge engine proxy.

No model downloads. Optional STUDYFORGE_PUBLIC_HOST is the exact private
Tailscale Serve hostname. STUDYFORGE_TOKEN is kept on the computer, never in JS.
"""
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from urllib.request import Request, build_opener, HTTPRedirectHandler, ProxyHandler
from urllib.error import HTTPError
from urllib.parse import urlsplit, unquote
from pathlib import Path
import base64
import json
import shutil
import sys
import subprocess
import tempfile
import os
import re

ROOT=Path(__file__).resolve().parents[2]
OLLAMA="http://127.0.0.1:11434"
ENGINE="http://127.0.0.1:8765"
STATIC={"/index.html","/manifest.webmanifest","/sw.js","/web/app.js","/web/science.mjs","/web/styles.css","/web/local-engine/ui.js","/web/local-engine/learning.js","/content/study-catalog.json","/branding/studyfrog-icon-192.png","/branding/studyfrog-icon-512.png"}
PUBLIC_HOST=os.environ.get("STUDYFORGE_PUBLIC_HOST","").lower()
if PUBLIC_HOST and not re.fullmatch(r"[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.[a-z0-9-]+\.ts\.net",PUBLIC_HOST):
    raise ValueError("STUDYFORGE_PUBLIC_HOST must be your exact private Tailscale Serve hostname")

class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs): return None
OPENER=build_opener(ProxyHandler({}), NoRedirect())
LEARNING = None


def ollama_generate(payload, timeout, maximum):
    body = json.dumps(payload | {"stream": False}).encode()
    request = Request(OLLAMA + "/api/generate", data=body, headers={"Content-Type": "application/json"})
    with OPENER.open(request, timeout=timeout) as response:
        raw = response.read(maximum + 1)
    if len(raw) > maximum:
        raise ValueError("Local model response exceeds the limit")
    result = json.loads(raw)
    if not isinstance(result, dict):
        raise ValueError("Invalid local model response")
    return result


def installed_study_model(key):
    model = os.environ.get(key, "")
    if not model or "cloud" in model.casefold() or "://" in model:
        return None
    with OPENER.open(OLLAMA + "/api/tags", timeout=5) as response:
        installed = json.loads(response.read(1_000_000)).get("models", [])
    if not any(item.get("name") in (model, model + ":latest") and not item.get("remote_host")
               and not item.get("remote_model") for item in installed):
        return None
    return model


def study_generate(prompt, frame):
    key = "STUDYFORGE_VISION_MODEL" if frame else "STUDYFORGE_STUDY_MODEL"
    model = installed_study_model(key)
    if not model:
        return None
    payload = {"model": model, "prompt": prompt, "options": {"num_predict": 700}}
    if frame:
        if frame.stat().st_size > 500_000:
            raise ValueError("Saved frame exceeds the vision budget")
        payload["images"] = [base64.b64encode(frame.read_bytes()).decode("ascii")]
    result = ollama_generate(payload, timeout=150, maximum=64_000).get("response")
    return result.strip() if isinstance(result, str) else None


def study_caption(prompt, frame):
    # Query-time vision bypasses Vidlizer so it always sends exactly one frame
    # and issues at most one inference request. Vidlizer is for saved captions only.
    executable = shutil.which("vidlizer")
    if executable:
        model = installed_study_model("STUDYFORGE_VISION_MODEL")
        if not model:
            return None
        from image_ingest import bounded_command, local_environment
        with tempfile.TemporaryDirectory(prefix="studyfrog-caption-") as scratch:
            output = Path(scratch) / "caption.json"
            env = dict(local_environment(), HOME=scratch, USERPROFILE=scratch,
                       VIDLIZER_CONFIG_DIR=scratch, OLLAMA_HOST=OLLAMA)
            try:
                bounded_command([executable, str(frame), "--provider", "ollama",
                                 "--model", model, "--no-transcript", "--format", "json",
                                 "--output", str(output)], scratch, 150, env=env)
                if output.stat().st_size > 240_000:
                    raise ValueError("Frame caption output is too large")
                data = json.loads(output.read_text(encoding="utf-8"))
                if data.get("provider_used") == "ollama" and data.get("model_used") in (model, model + ":latest"):
                    flow = data.get("flow", [])
                    if flow:
                        summary = " — ".join(str(flow[0].get(field) or "").strip()
                                             for field in ("scene", "text_visible")).strip(" —")
                        if summary:
                            return summary
            except (OSError, RuntimeError, ValueError, subprocess.TimeoutExpired):
                pass
    return study_generate(prompt, frame)


def start_learning():
    global LEARNING
    if os.environ.get("STUDYFORGE_MEDIA_LEARNING") == "1":
        sys.path.insert(0, str(ROOT / "engine"))
        from media_learning import Library
        data = Path(os.environ.get("STUDYFORGE_DATA", ROOT / "engine/data"))
        LEARNING = Library(data / "learning", OLLAMA, study_generate, study_caption)


def allowed(peer,host,origin):
    authorities={"127.0.0.1:8766","localhost:8766"}
    origins={"http://127.0.0.1:8766","http://localhost:8766"}
    if PUBLIC_HOST: authorities.update((PUBLIC_HOST,PUBLIC_HOST+":443"));origins.add("https://"+PUBLIC_HOST)
    return peer in ("127.0.0.1","::1") and host in authorities and (not origin or origin in origins and urlsplit(origin).netloc==host)

def route(method,path):
    if path=="/engine/video-inputs" and method=="POST": return "/video-inputs"
    if re.fullmatch(r"/engine/video-inputs/[0-9a-f]{32}",path) and method=="DELETE": return path.removeprefix("/engine")
    if path=="/engine/health" and method=="GET": return "/health"
    if path=="/engine/ocr" and method=="POST": return "/ocr"
    if path=="/engine/jobs" and method in ("POST","GET"): return "/jobs"
    if re.fullmatch(r"/engine/(?:jobs|results)/[0-9a-f]{32}",path) and method in ("GET","DELETE"):
        if method=="DELETE" and not path.startswith("/engine/jobs/"): return None
        return path.removeprefix("/engine")
    if re.fullmatch(r"/engine/jobs/[0-9a-f]{32}/cancel",path) and method=="POST": return path.removeprefix("/engine")
    return None

class LimitedBody:
    def __init__(self, stream, remaining): self.stream=stream;self.remaining=remaining
    def read(self, size=65536):
        if not self.remaining: return b""
        data=self.stream.read(min(size,self.remaining))
        if not data: raise ValueError("Incomplete video upload")
        self.remaining-=len(data);return data

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
        started=False
        try:
            headers={"Authorization":"Bearer "+token,"Content-Type":"application/json"}
            if path=="/video-inputs" and method=="POST":
                length=int(self.headers.get("Content-Length","0"))
                if not 0<length<=128_000_000: raise ValueError("Video upload limit is 128 MB")
                content=self.headers.get_content_type()
                if content not in ("video/mp4","video/quicktime","video/webm","application/octet-stream"): raise ValueError("Use MP4, MOV or WebM video")
                self.connection.settimeout(120)
                body=LimitedBody(self.rfile,length)
                headers.update({"Content-Type":content,"Content-Length":str(length)})
            else: body=self.body(13_000_000)[0] if method=="POST" else None
            request=Request(ENGINE+path,data=body,method=method,headers=headers)
            with OPENER.open(request,timeout=120 if path=="/video-inputs" else 35) as response:
                size=int(response.headers.get("Content-Length","0"));maximum=256_000_000 if path.startswith("/results/") else 1_000_000
                if not 0<size<=maximum: raise ValueError("Invalid engine response size")
                started=True
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
        except ValueError as exc:
            if not started: self.reply(400,{"error":str(exc)[:200]})
        except OSError:
            if not started: self.reply(502,{"error":"Local SD engine unavailable; check port 8765 and token"})
    def learning(self, method, path):
        if LEARNING is None:
            return self.reply(503, {"error": "Media Learning is disabled. Enable it on your local bridge."})
        from media_learning import Busy
        if method != "GET" and self.headers.get("X-StudyFrog-Learning") != "1":
            return self.reply(403, {"error": "Use the Study Frog learning interface"})
        try:
            if method == "GET" and path == "/learning/sources":
                return self.reply(200, {"sources": LEARNING.list()})
            if method == "POST" and path == "/learning/sources":
                self.connection.settimeout(120)
                return self.reply(202, LEARNING.receive(
                    self.rfile, int(self.headers.get("Content-Length", "0")),
                    self.headers.get_content_type(), unquote(self.headers.get("X-StudyFrog-Name", ""))))
            if method == "POST" and path in ("/learning/ask", "/learning/quiz", "/learning/enrich"):
                _, body = self.body(8_192)
                if path.endswith("/enrich"):
                    try:
                        return self.reply(200, LEARNING.enrich(body.get("doc"), body.get("timestamp")))
                    except OSError:
                        return self.reply(503, {"error": "Local vision is unavailable. Check Ollama and the installed model."})
                return self.reply(200, LEARNING.answer(body.get("question", ""), body.get("doc"),
                                  quiz=path.endswith("/quiz"), allow_vision=body.get("vision") is True))
            match = re.fullmatch(r"/learning/sources/([0-9a-f]{32})(?:/(.+))?", path)
            if match:
                doc, child = match.groups()
                if method == "DELETE" and not child:
                    LEARNING.delete(doc)
                    return self.reply(200, {"status": "deleted"})
                if method == "GET" and child == "notes":
                    return self.reply(200, {"markdown": LEARNING.markdown(doc)})
                if method == "GET" and child and child.startswith("asset/"):
                    with LEARNING.asset(doc, child.removeprefix("asset/")).open("rb") as file:
                        self.send_response(200)
                        self.send_header("Content-Type", "image/jpeg")
                        self.send_header("Cross-Origin-Resource-Policy", "same-origin")
                        self.send_header("Cache-Control", "no-store")
                        self.send_header("Content-Length", str(os.fstat(file.fileno()).st_size))
                        self.end_headers()
                        shutil.copyfileobj(file, self.wfile)
                    return
            self.reply(404, {"error": "Study source or route not found"})
        except FileNotFoundError:
            self.reply(404, {"error": "Study source or asset not found"})
        except Busy as exc:
            self.reply(409, {"error": str(exc)})
        except (TypeError, ValueError) as exc:
            self.reply(400, {"error": str(exc)[:180]})

    def do_HEAD(self):
        if not self.allowed(): return self.reply(403,{"error":"Private local app only"})
        path=unquote(urlsplit(self.path).path)
        if path=="/": path="/index.html"
        if path not in STATIC: return self.reply(404,{"error":"Not found"})
        self.path=path;super().do_HEAD()
    def do_GET(self):
        if not self.allowed(): return self.reply(403,{"error":"Private local app only"})
        path=urlsplit(self.path).path
        if path.startswith("/learning/"): return self.learning("GET",path)
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
        path=urlsplit(self.path).path
        if path.startswith("/learning/"): return self.learning("DELETE",path)
        target=route("DELETE",path)
        if target: return self.proxy("DELETE",target)
        self.reply(404,{"error":"Not found"})
    def do_POST(self):
        if not self.allowed(): return self.reply(403,{"error":"Private local app only"})
        path=urlsplit(self.path).path
        if path.startswith("/learning/"): return self.learning("POST",path)
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
            result=ollama_generate({"model":model,"prompt":prompt,**settings},600,24_000_000)
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
    start_learning()
    print("Study Frog local app: http://127.0.0.1:8766/")
    Server(("127.0.0.1",8766),Handler).serve_forever()
