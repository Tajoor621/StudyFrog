import io
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from PIL import Image, ImageDraw, ImageFont
import study_ocr

HEADER="level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext\n"
class StudyOCRTests(unittest.TestCase):
    def test_tsv_layout_confidence_and_untrusted_coordinates(self):
        tsv=HEADER+"5\t1\t1\t1\t1\t1\t10\t20\t30\t20\t90\tCell\n5\t1\t1\t1\t1\t2\t45\t20\t40\t20\t70\twall\n5\t1\t1\t1\t2\t1\t10\t60\t45\t20\t50\tDNA\n"
        result=study_ocr.parse_tsv(tsv,200,100)
        self.assertEqual(result["text"],"Cell wall\nDNA");self.assertEqual(result["lines"][0]["box"],[10,20,85,40]);self.assertEqual(result["lines"][0]["confidence"],80)
        for row in ("5\t1\t1\t1\t1\t1\t-1\t0\t10\t10\t90\tBad\n","5\t1\t1\t1\t1\t1\t0\t0\t10\t10\tnan\tBad\n"):
            self.assertEqual(study_ocr.parse_tsv(HEADER+row,100,100)["text"],"")
        self.assertTrue(result["review_required"])
    def test_missing_tool_language_and_busy_are_actionable(self):
        image=Image.new("RGB",(10,10))
        for code in ("../eng","eng --oem 0",[],"eng+urd+ara+fra"):
            with self.subTest(code=code),self.assertRaises(ValueError): study_ocr.extract(image,code)
        with patch.object(study_ocr.shutil,"which",return_value=None),self.assertRaisesRegex(RuntimeError,"Install free Tesseract"): study_ocr.extract(image)
        with patch.object(study_ocr.shutil,"which",return_value="tesseract"):
            study_ocr.GATE.acquire()
            try:
                with self.assertRaises(study_ocr.BusyError): study_ocr.extract(image)
            finally: study_ocr.GATE.release()
    def test_failed_subprocess_and_timeout_remove_private_temporary_files(self):
        paths=[]
        def run(command,**kwargs):
            paths.append(Path(command[1]).parent);raise subprocess.TimeoutExpired(command,25)
        with patch.object(study_ocr.shutil,"which",return_value="tesseract"),patch.object(study_ocr.subprocess,"run",side_effect=run):
            with self.assertRaisesRegex(RuntimeError,"timed out"): study_ocr.extract(Image.new("RGB",(100,100)))
        self.assertTrue(paths);self.assertTrue(all(not path.exists() for path in paths))
        self.assertTrue(study_ocr.GATE.acquire(blocking=False));study_ocr.GATE.release()
    @unittest.skipUnless(shutil.which("tesseract"),"Install Tesseract to run real OCR integration")
    def test_real_tesseract_recognizes_rendered_notes(self):
        font=ImageFont.truetype("DejaVuSans.ttf",64)
        image=Image.new("RGB",(1400,350),"white");draw=ImageDraw.Draw(image)
        draw.text((50,40),"CELL WALL",fill="black",font=font);draw.text((50,150),"DNA AND PROTEIN",fill="black",font=font)
        result=study_ocr.extract(image)
        self.assertIn("CELL WALL",result["text"]);self.assertIn("DNA AND PROTEIN",result["text"])
        self.assertTrue(result["lines"])

class OCRHTTPTests(unittest.TestCase):
    def test_authenticated_endpoint_returns_review_text_without_saving_jobs(self):
        import base64
        import json
        import threading
        import urllib.request
        import urllib.error
        import server
        output=io.BytesIO();Image.new("RGB",(20,20),"white").save(output,format="PNG")
        body=json.dumps({"image":base64.b64encode(output.getvalue()).decode(),"language":"eng"}).encode()
        http=server.Server(("127.0.0.1",0),server.Handler);thread=threading.Thread(target=http.serve_forever,daemon=True);thread.start()
        before=set(server.ROOT.iterdir())
        try:
            url=f"http://127.0.0.1:{http.server_port}/ocr"
            def request(auth=True):
                headers={"Content-Type":"application/json"}
                if auth: headers["Authorization"]="Bearer "+server.TOKEN
                return urllib.request.urlopen(urllib.request.Request(url,data=body,headers=headers),timeout=5)
            with self.assertRaises(urllib.error.HTTPError) as error: request(False)
            self.assertEqual(error.exception.code,401)
            with patch.object(study_ocr,"extract",return_value={"text":"DNA","review_required":True}):
                with request() as response:
                    self.assertEqual(json.load(response)["text"],"DNA");self.assertEqual(response.headers["Cache-Control"],"no-store")
            with patch.object(study_ocr,"extract",side_effect=study_ocr.BusyError("busy")):
                with self.assertRaises(urllib.error.HTTPError) as error: request()
                self.assertEqual(error.exception.code,429)
            with patch.object(study_ocr,"extract",side_effect=RuntimeError("Install free Tesseract")):
                with self.assertRaises(urllib.error.HTTPError) as error: request()
                self.assertEqual(error.exception.code,503)
            self.assertEqual(set(server.ROOT.iterdir()),before)
        finally: http.shutdown();http.server_close();thread.join()
