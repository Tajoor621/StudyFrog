import io
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import unittest
from unittest.mock import patch

from PIL import Image, ImageDraw, ImageFont
import image_ingest
from media_learning import Library
import video_index


class MediaLearningTests(unittest.TestCase):
    def setUp(self):
        self.scratch = tempfile.TemporaryDirectory()
        self.addCleanup(self.scratch.cleanup)
        self.root = Path(self.scratch.name)
        self.calls = []

        def generate(prompt, frame):
            self.calls.append((prompt, frame))
            return "A diagram shows photosynthesis." if frame else None

        self.library = Library(self.root / "library", "http://127.0.0.1:11434", generate)

    def wait(self, doc):
        deadline = time.monotonic() + 40
        while time.monotonic() < deadline:
            row = self.library.get(doc)
            if row["status"] != "indexing":
                self.assertEqual(row["status"], "ready", row["warnings"])
                return
            time.sleep(.05)
        self.fail("Indexing did not finish")

    def test_structured_markdown_preserves_body_and_quiz_routes_by_media_type(self):
        body = "Kinetic energy\n\n$$ E = mc^2 $$\n\n```mermaid\ngraph TD; A --> B\n```\n"
        markdown, meta = image_ingest.structured(body, "engocr")
        self.assertTrue(markdown.endswith(body))
        self.assertTrue(meta["has_formula"])
        self.assertTrue(meta["has_diagram"])
        with patch.object(image_ingest, "ingest", return_value=(markdown, meta, [])):
            doc = self.library.receive(io.BytesIO(b"x"), 1, "image/png", "formulas.png")["id"]
            self.wait(doc)
        result = self.library.answer("kinetic", doc)
        self.assertEqual(result["citations"][0]["meta"]["preview"], "image.jpg")
        self.assertIsNone(self.calls[-1][1])
        quiz = self.library.answer("", doc, quiz=True)
        self.assertIn("Explain a formula", quiz["answer"])
        self.assertIn("Mermaid", self.calls[-1][0])
        self.assertEqual(self.library.markdown(doc), markdown)

    @unittest.skipUnless(shutil.which("tesseract"), "requires local Tesseract")
    def test_real_tesseract_import_persists_and_delete_removes_private_media(self):
        font = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
        if not font.is_file():
            self.skipTest("requires DejaVu test font")
        picture = Image.new("RGB", (900, 240), "white")
        ImageDraw.Draw(picture).text((30, 70), "Photosynthesis converts light", fill="black",
                                    font=ImageFont.truetype(str(font), 44))
        buffer = io.BytesIO()
        picture.save(buffer, "PNG")
        data = buffer.getvalue()
        with patch.dict(os.environ, {"STUDYFORGE_VISION_MODEL": "", "STUDYFORGE_FALCON_MODEL_DIR": ""}):
            doc = self.library.receive(io.BytesIO(data), len(data), "image/png", "../lesson.png")["id"]
            self.wait(doc)
        reopened = Library(self.library.root, self.library.ollama_url, self.library.generate)
        self.assertIn("Photosynthesis", reopened.markdown(doc))
        self.assertEqual(reopened.get(doc)["name"], "lesson.png")
        self.assertTrue(reopened.asset(doc, "image.jpg").is_file())
        with self.assertRaises(FileNotFoundError):
            reopened.asset(doc, "../library.sqlite3")
        reopened.delete(doc)
        self.assertFalse(reopened.directory(doc).exists())
        self.assertEqual(reopened.search("photosynthesis"), [])

    @unittest.skipUnless(shutil.which("ffmpeg") and shutil.which("ffprobe"), "requires local FFmpeg")
    def test_real_video_index_reuses_subtitles_and_one_frame_per_visual_request(self):
        srt = self.root / "source.srt"
        srt.write_text("1\n00:00:00,500 --> 00:00:01,700\nPhotosynthesis stores light energy.\n", encoding="utf-8")
        video = self.root / "source.mp4"
        subprocess.run(["ffmpeg", "-nostdin", "-loglevel", "error", "-f", "lavfi", "-i",
                        "color=c=green:s=320x240:d=2", "-i", str(srt), "-c:v", "libx264",
                        "-c:s", "mov_text", str(video)], check=True, timeout=20)
        data = video.read_bytes()
        doc = self.library.receive(io.BytesIO(data), len(data), "video/mp4", "lecture.mp4")["id"]
        self.wait(doc)
        self.assertFalse((self.library.directory(doc) / "original").exists())
        self.assertEqual(self.calls, [])
        self.assertIn("Photosynthesis", self.library.markdown(doc))
        self.assertEqual(self.library.index(doc)["transcript"]["model"], "embedded-subtitles")
        with patch.object(video_index, "build", side_effect=AssertionError("Must not re-decode")):
            text = self.library.answer("photosynthesis", doc, allow_vision=True)
            self.assertEqual(text["mode"], "extracts")
            self.assertIsNone(self.calls[-1][1])
            self.assertEqual(text["citations"][0]["meta"]["timestamp_start"], .5)
            self.calls.clear()
            visual = self.library.answer("What is written on the whiteboard at 00:01?", doc, allow_vision=True)
            self.assertEqual(visual["mode"], "local-vision")
            self.assertEqual(len(self.calls), 1)
            self.assertEqual(self.calls[0][1].suffix, ".jpg")
            self.assertIn("diagram", self.library.enrich(doc, 1)["caption"])
            self.assertIn("Visual description", self.library.markdown(doc))
            self.assertTrue(any("Visual description" in c["body"] for c in self.library.search("diagram", doc)))
            self.calls.clear()
            self.library.enrich(doc, 1)
            self.assertEqual(self.calls, [])

    def test_restart_removes_unfinished_data_and_rejects_pdf(self):
        with self.assertRaises(ValueError):
            self.library.receive(io.BytesIO(b"%PDF"), 4, "application/pdf", "notes.pdf")
        pending = "b" * 32
        folder = self.library.directory(pending)
        folder.mkdir()
        (folder / "original").write_text("partial")
        with self.library.connect() as conn:
            conn.execute("INSERT INTO documents(id,name,kind,status) VALUES (?,?,?,'indexing')",
                         (pending, "partial", "video"))
        library = Library(self.library.root, self.library.ollama_url, self.library.generate)
        self.assertEqual(library.get(pending)["status"], "failed")
        self.assertFalse(folder.exists())


if __name__ == "__main__":
    unittest.main()
