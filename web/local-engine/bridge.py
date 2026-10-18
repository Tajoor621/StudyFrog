"""Study Frog localhost-only Ollama image bridge; standard library only."""
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from urllib.request import Request, urlopen
from urllib.parse import urlsplit
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[2]
OLLAMA = "http://127.0.0.1:11434"

class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

    def allowed(self):
        return self.headers.get("Host", "").split(":")[0] in ("localhost", "127.0.0.1")

    def reply(self, code, data):
        payload = json.dumps(data).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self):
        if not self.allowed():
            return self.reply(403, {"error": "Localhost only"})
        if urlsplit(self.path).path != "/health":
            return super().do_GET()
        try:
            with urlopen(OLLAMA + "/api/tags", timeout=5) as r:
                models = json.load(r).get("models", [])
            self.reply(200, {"models": [m["name"] for m in models if m.get("name")]})
        except (OSError, ValueError):
            self.reply(503, {"error": "Ollama unavailable"})

    def do_POST(self):
        if not self.allowed():
            return self.reply(403, {"error": "Localhost only"})
        if urlsplit(self.path).path != "/generate":
            return self.reply(404, {"error": "Not found"})
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length < 2 or length > 32768:
                return self.reply(413, {"error": "Request too large"})
            data = json.loads(self.rfile.read(length))
            prompt, model = data.get("prompt"), data.get("model")
            if not isinstance(prompt, str) or not prompt.strip() or len(prompt) > 6000:
                return self.reply(400, {"error": "Invalid prompt"})
            if not isinstance(model, str) or not 0 < len(model) <= 150:
                return self.reply(400, {"error": "Invalid model"})
            width, height, steps = (int(data.get(k, v)) for k, v in (("width", 512), ("height", 512), ("steps", 20)))
            if not (256 <= width <= 2048 and 256 <= height <= 2048 and 1 <= steps <= 100):
                return self.reply(400, {"error": "Invalid generation settings"})
            body = json.dumps({"model": model, "prompt": prompt, "width": width, "height": height, "steps": steps, "stream": False}).encode()
            req = Request(OLLAMA + "/api/generate", data=body, headers={"Content-Type": "application/json"})
            with urlopen(req, timeout=600) as r:
                result = json.load(r)
            images = result.get("images") or ([result["image"]] if result.get("image") else [])
            if not images:
                return self.reply(502, {"error": "Model returned no image. Use a compatible image model."})
            self.reply(200, {"image": images[0]})
        except (ValueError, TypeError, KeyError):
            self.reply(400, {"error": "Invalid request"})
        except OSError:
            self.reply(502, {"error": "Local generation failed. Check Ollama logs."})

if __name__ == "__main__":
    print("Study Frog local app: http://127.0.0.1:8766/")
    ThreadingHTTPServer(("127.0.0.1", 8766), Handler).serve_forever()
