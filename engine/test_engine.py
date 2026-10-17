import base64, io, json, os, tempfile, threading, unittest, urllib.request, urllib.error
from pathlib import Path
TMP=tempfile.TemporaryDirectory()
os.environ["STUDYFORGE_DATA"]=TMP.name
os.environ["STUDYFORGE_TOKEN"]="test-only-token"
import server
from PIL import Image

class EngineTests(unittest.TestCase):
    def setUp(self):
        image=Image.new("RGB",(128,128),"teal");out=io.BytesIO();image.save(out,format="PNG")
        self.image=base64.b64encode(out.getvalue()).decode()
    def test_reject_invalid_jobs(self):
        for body in ({"prompt":""},{"prompt":"ok","kind":"fake"},{"prompt":"ok","width":769},{"prompt":"ok","steps":True},{"prompt":"ok","image":"garbage"},{"prompt":"ok","kind":"video"}):
            with self.assertRaises(ValueError): server.validate(body)
    def test_medical_prompt_allowed(self):
        self.assertEqual(server.validate({"prompt":"Educational reproductive anatomy"})["kind"],"image")
    def test_paths_cannot_escape_data(self):
        with self.assertRaises(ValueError): server.read("../../etc/passwd")
    def test_diagram_lifecycle_and_persistence(self):
        job=server.validate({"prompt":"Cell study","kind":"diagram"});job.update(id="a"*32,status="queued")
        server.process(job)
        self.assertEqual(server.read("a"*32)["status"],"done")
        with Image.open(server.ROOT/("a"*32+".png")) as im: self.assertEqual(im.size,(768,432))
    def test_missing_model_fails_without_fake_result(self):
        old=os.environ.pop("STUDYFORGE_MODEL",None)
        try:
            job=server.validate({"prompt":"Plant"});job.update(id="b"*32,status="queued");server.process(job)
            self.assertEqual(server.read("b"*32)["status"],"failed")
            self.assertFalse((server.ROOT/("b"*32+".png")).exists())
        finally:
            if old: os.environ["STUDYFORGE_MODEL"]=old
    def test_http_auth_and_queue(self):
        http=server.Server(("127.0.0.1",0),server.Handler);thread=threading.Thread(target=http.serve_forever,daemon=True);thread.start()
        url=f"http://127.0.0.1:{http.server_port}"
        try:
            with self.assertRaises(urllib.error.HTTPError) as err: urllib.request.urlopen(url+"/health")
            self.assertEqual(err.exception.code,401)
            req=urllib.request.Request(url+"/health",headers={"Authorization":"Bearer test-only-token"})
            self.assertTrue(json.load(urllib.request.urlopen(req))["local_only"])
            req=urllib.request.Request(url+"/jobs",data=json.dumps({"prompt":"Study","kind":"diagram"}).encode(),headers={"Authorization":"Bearer test-only-token","Content-Type":"application/json"})
            response=json.load(urllib.request.urlopen(req));self.assertEqual(response["status"],"queued")
            job=server.JOBS.get_nowait();server.process(job);server.JOBS.task_done()
            req=urllib.request.Request(url+"/results/"+response["id"],headers={"Authorization":"Bearer test-only-token"})
            self.assertTrue(urllib.request.urlopen(req).read().startswith(b"\x89PNG"))
        finally: http.shutdown();http.server_close();thread.join()
    def test_tiled_refine_preserves_dimensions_and_overlap(self):
        job=server.validate({"prompt":"Refine plant study","kind":"refine","image":self.image,"width":1536,"height":864})
        seen=[]
        def fake(patch):
            seen.append((patch["width"],patch["height"]))
            return Image.new("RGB",(patch["width"],patch["height"]),"red")
        result=server.tiled_refine(job,generate=fake)
        self.assertEqual(result.size,(1536,864));self.assertGreater(len(seen),1)
        self.assertTrue(all(w<=768 and h<=768 for w,h in seen))
        self.assertEqual(result.getpixel((0,0)),(255,0,0))
        self.assertEqual(result.getpixel((1535,863)),(255,0,0))
    def test_video_has_real_frames(self):
        import shutil
        if not shutil.which("ffmpeg"): self.skipTest("FFmpeg unavailable")
        output=Path(TMP.name)/"test.mp4"
        server.reveal_video(server.decode_image(self.image),output,seconds=3,width=320,height=180,fps=24)
        self.assertGreater(output.stat().st_size,1000)
        import subprocess
        data=json.loads(subprocess.check_output(["ffprobe","-v","error","-show_streams","-of","json",str(output)]))
        stream=data["streams"][0];self.assertEqual(stream["width"],320);self.assertEqual(stream["height"],180);self.assertEqual(stream["nb_frames"],"72")

if __name__=="__main__": unittest.main()
