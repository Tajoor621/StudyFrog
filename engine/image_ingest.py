"""Local image decoding and structured OCR, with the existing plain-text fallback."""
from pathlib import Path
import os
import re
import shutil
import subprocess
import tempfile
from PIL import Image, ImageOps

MIME = {"image/png", "image/jpeg", "image/webp", "image/heic", "image/heif"}


def local_environment():
    # Preserve platform/runtime settings without forwarding service credentials.
    names = ("PATH", "SYSTEMROOT", "WINDIR", "TEMP", "TMP", "TMPDIR",
             "LANG", "LC_ALL", "LD_LIBRARY_PATH", "VIRTUAL_ENV")
    return {key: os.environ[key] for key in names if key in os.environ}


def bounded_command(command, cwd, timeout, limit=240_000, env=None):
    with tempfile.TemporaryFile() as output:
        process = subprocess.run(command, cwd=cwd, env=env, stdin=subprocess.DEVNULL,
                                 stdout=output, stderr=subprocess.DEVNULL, timeout=timeout)
        if process.returncode or output.tell() > limit:
            raise RuntimeError("Local extraction tool failed or exceeded its output budget")
        output.seek(0)
        return output.read().decode("utf-8")


def decode(path, mime):
    if mime in {"image/heic", "image/heif"}:
        try:
            from pillow_heif import register_heif_opener
        except ImportError as exc:
            raise ValueError("Install optional pillow-heif or convert HEIC to PNG or JPEG") from exc
        register_heif_opener()
    try:
        with Image.open(path) as source:
            accepted = {"image/png": "PNG", "image/jpeg": "JPEG", "image/webp": "WEBP",
                        "image/heic": "HEIF", "image/heif": "HEIF"}
            if source.format != accepted[mime] or source.width * source.height > 24_000_000:
                raise ValueError("Image format does not match its upload type or exceeds 24 megapixels")
            image = ImageOps.exif_transpose(source).convert("RGB")
            image.thumbnail((2400, 2400))
            return image
    except (OSError, Image.DecompressionBombError) as exc:
        raise ValueError("Unable to decode this image") from exc


def structured(markdown, engine):
    formula = bool(re.search(r"\$\$\s*[^$]+?\s*\$\$", markdown))
    diagram = bool(re.search(r"^\s*```mermaid\s*\n.+?^\s*```", markdown, re.M | re.S | re.I))
    meta = dict(source_type="image", ocr_engine=engine, has_formula=formula,
                has_diagram=diagram, diagram_format="mermaid" if diagram else "none")
    header = "\n".join(f"{key}: {str(value).lower()}" for key, value in meta.items())
    return f"---\n{header}\n---\n{markdown}", meta


def ingest(path, mime, directory, ollama_url):
    from study_ocr import extract

    image = decode(path, mime)
    preview = image.copy()
    preview.thumbnail((640, 640))
    preview.save(directory / "image.jpg", quality=86)
    model = os.environ.get("STUDYFORGE_VISION_MODEL", "")
    falcon = os.environ.get("STUDYFORGE_FALCON_MODEL_DIR", "")
    warnings = []
    with tempfile.TemporaryDirectory(prefix="studyfrog-ocr-") as scratch:
        scratch = Path(scratch)
        page = scratch / "page.png"
        image.save(page)
        env = dict(local_environment(), HOME=str(scratch), USERPROFILE=str(scratch),
                   ENGOCR_CONFIG_DIR=str(scratch), OLLAMA_BASE_URL=ollama_url + "/v1",
                   HF_HUB_OFFLINE="1")
        choices = []
        if model and "cloud" not in model.casefold() and "://" not in model and shutil.which("engocr"):
            choices.append(("engocr", ["engocr", "convert", str(page), "--stdout",
                                       "--provider", "ollama", "--model", model]))
        if falcon and Path(falcon).is_dir() and shutil.which("falcon-ocr"):
            choices.append(("falcon-ocr", ["falcon-ocr", "--model", str(Path(falcon).resolve()),
                                           "run", str(page), "--text"]))
        for name, command in choices:
            try:
                markdown = bounded_command(command, scratch, 150, env=env)
                if markdown.strip():
                    return (*structured(markdown, name), warnings)
            except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired):
                pass
            warnings.append(f"{name} did not extract text; tried the next local OCR option.")
    try:
        markdown = extract(image, os.environ.get("STUDYFORGE_OCR_LANGUAGE", "eng"))["text"]
    except RuntimeError as exc:
        raise RuntimeError("No local OCR succeeded. Configure EngOCR or Falcon-OCR, or install Tesseract.") from exc
    if not markdown.strip():
        raise ValueError("No readable text found. Try a sharper image or configure a vision model.")
    warnings.append("Plain-text OCR was used. Formulas, handwriting, and diagrams may be incomplete.")
    return (*structured(markdown, "tesseract"), warnings)
