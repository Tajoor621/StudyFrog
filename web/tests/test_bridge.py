import http.client
import importlib.util
import json
import os
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from unittest.mock import patch

spec=importlib.util.spec_from_file_location("studyforge_bridge",Path(__file__).resolve().parents[1]/"local-engine/bridge.py")
bridge=importlib.util.module_from_spec(spec);spec.loader.exec_module(bridge)

class BridgeTests(unittest.TestCase):
    def setUp(self):
        self.server=bridge.Server(("127.0.0.1",0),bridge.Handler)
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start()
        self.addCleanup(self.close)
    def close(self): self.server.shutdown();self.server.server_close();self.thread.join()
    def request(self,path,method="GET",body=None,host="127.0.0.1:8766",origin=None):
        connection=http.client.HTTPConnection("127.0.0.1",self.server.server_port,timeout=5)
        headers={"Host":host,"Content-Type":"application/json"}
        if origin is not None: headers["Origin"]=origin
        connection.request(method,path,body=json.dumps(body) if body is not None else None,headers=headers)
        response=connection.getresponse();data=response.read();status=response.status;headers=dict(response.getheaders());connection.close();return status,headers,data
    def test_static_allowlist_protects_repository_and_private_engine_files(self):
        self.assertEqual(self.request("/")[0],200)
        for path in ("/.git/config","/engine/server.py","/engine/data/test.png","/README.md","/web/local-engine/bridge.py","/web/%2e%2e/engine/server.py"):
            with self.subTest(path=path):
                self.assertEqual(self.request(path)[0],404);self.assertEqual(self.request(path,"HEAD")[0],404)
        status,headers,_=self.request("/web/local-engine/ui.js")
        self.assertEqual(status,200);self.assertIn("frame-ancestors 'none'",headers["Content-Security-Policy"])
    def test_origin_host_and_tailscale_are_exact_not_suffix_only(self):
        for host in ("evil.example","127.0.0.1.evil:8766","evil.ts.net","localhost:1234"):
            self.assertEqual(self.request("/",host=host)[0],403)
        self.assertEqual(self.request("/",origin="https://evil.example")[0],403)
        self.assertEqual(self.request("/",origin="http://localhost:8766")[0],403)
        self.assertEqual(self.request("/",origin="http://127.0.0.1:8766")[0],200)
        with patch.object(bridge,"PUBLIC_HOST","laptop.tail123.ts.net"):
            self.assertTrue(bridge.allowed("127.0.0.1","laptop.tail123.ts.net","https://laptop.tail123.ts.net"))
            self.assertFalse(bridge.allowed("192.168.1.2","laptop.tail123.ts.net","https://laptop.tail123.ts.net"))
            self.assertFalse(bridge.allowed("127.0.0.1","intruder.tail123.ts.net","https://intruder.tail123.ts.net"))
    def test_proxy_has_no_arbitrary_routes_or_credential_fallback(self):
        for path in ("/engine/../results/x","/engine/results/not-uuid","/engine/admin","/engine/jobs/"+"a"*32+"/secret"):
            self.assertEqual(self.request(path)[0],404)
        with patch.dict(os.environ,{"STUDYFORGE_TOKEN":""}): self.assertEqual(self.request("/engine/health")[0],503)
    def test_proxy_auth_json_and_results_are_no_store_and_redirects_rejected(self):
        calls=[]
        class Engine(BaseHTTPRequestHandler):
            def log_message(self,*args): pass
            def do_POST(self):
                calls.append((self.path,self.headers.get("Authorization"),json.loads(self.rfile.read(int(self.headers["Content-Length"])))))
                data=b'{"text":"CELL WALL","review_required":true}';self.send_response(200);self.send_header("Content-Length",str(len(data)));self.end_headers();self.wfile.write(data)
            def do_GET(self): self.send_response(302);self.send_header("Location","http://example.com/");self.end_headers()
        engine=ThreadingHTTPServer(("127.0.0.1",0),Engine);thread=threading.Thread(target=engine.serve_forever,daemon=True);thread.start()
        try:
            with patch.object(bridge,"ENGINE",f"http://127.0.0.1:{engine.server_port}"),patch.dict(os.environ,{"STUDYFORGE_TOKEN":"test-token"}):
                status,headers,body=self.request("/engine/ocr","POST",{"image":"test"})
                self.assertEqual(status,200);self.assertEqual(headers["Cache-Control"],"no-store");self.assertEqual(json.loads(body)["text"],"CELL WALL")
                self.assertEqual(calls,[("/ocr","Bearer test-token",{"image":"test"})])
                self.assertEqual(self.request("/engine/health")[0],502)
        finally: engine.shutdown();engine.server_close();thread.join()
    def test_ollama_rejects_bool_and_fractional_settings_before_request(self):
        for width in (True,512.2,"512"):
            status,_,_=self.request("/generate","POST",{"prompt":"plant","model":"local","width":width})
            self.assertEqual(status,400)

if __name__=="__main__": unittest.main()
