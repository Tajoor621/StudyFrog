# Media Learning: local setup and operation

Media Learning is an optional feature of the Study Frog **computer bridge**. It is available in a desktop browser or on a phone browser through the existing private bridge setup. The public GitHub Pages site, native editors, and creative generation and remix paths do not ingest these study files.

The current repository has no prior PDF ingest, ChromaDB collection, RAG retriever, or quiz generator. Both learning modules therefore share one local SQLite FTS5 library and reuse the bridge's Ollama transport. The retriever indexes extracted text, never image bytes. SQLite full-text matching is lexical rather than embedding-based. PDF files are outside this module and are rejected; no PDF code was added or replaced.

## Start without a model

Install Python 3.10 or newer with SQLite FTS5, Tesseract with your language data for image OCR, and FFmpeg/ffprobe for video. From the repository root:

```sh
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r engine/requirements.txt
STUDYFORGE_MEDIA_LEARNING=1 python web/local-engine/bridge.py
```

On Windows PowerShell after creating the virtual environment:

```powershell
.\.venv\Scripts\Activate.ps1
python -m pip install -r engine/requirements.txt
$env:STUDYFORGE_MEDIA_LEARNING = "1"
python web/local-engine/bridge.py
```

Open **http://127.0.0.1:8766/#learning**. Import a PNG, JPEG, WebP, HEIC/HEIF or an MP4, MOV or WebM. Select a ready source to search, make review questions, export Markdown, or remove it. Without Ollama, the app returns source excerpts and review prompts. No SD engine, bearer token, account, or API subscription is needed for this local study workflow. The creative engine still uses its existing separate token.

## Enable optional capabilities

| Capability | Setup |
| --- | --- |
| HEIC/HEIF | Install `pillow-heif==0.22.0`, or convert the file locally to PNG/JPEG first. |
| Structured OCR | Install the [EngOCR CLI](https://github.com/hggeorgiev/engocr) (its own Python 3.12+ environment is fine) and make `engocr` available on the bridge's PATH. Set `STUDYFORGE_VISION_MODEL` to an installed local Ollama vision tag, such as `qwen2.5vl:3b`. EngOCR receives only one image and explicitly uses loopback Ollama. |
| CPU OCR fallback | Install the [Falcon-OCR CPU runner](https://github.com/amazingvince/falcon-ocr-cpu) and its local checkpoint, put `falcon-ocr` on PATH, and set `STUDYFORGE_FALCON_MODEL_DIR` to the checkpoint directory. If unavailable or unsuccessful, the bridge uses Tesseract. Set `STUDYFORGE_OCR_LANGUAGE` for Tesseract, e.g. `eng+urd` with both language packs installed. |
| Video shot detection | Put [videoctx](https://github.com/dunfred/videoctx) in the bridge's Python environment to use its shot detector. Otherwise install PySceneDetect and OpenCV (pinned in `engine/requirements-learning.txt`). Without either, FFmpeg key packets and periodic samples are used. The bridge writes the videoctx 0.1 directory format itself so it can enforce a frame limit and keep model downloads disabled. |
| Speech without embedded captions | Install `faster-whisper==1.2.1` and obtain a compatible CTranslate2 Whisper checkpoint separately. Set `STUDYFORGE_WHISPER_MODEL_DIR` to its local directory. The bridge uses CPU INT8, checks embedded text subtitles first, and never fetches weights during ingestion. No transcript is fabricated when neither captions nor local ASR is available. |
| Generated answers and quizzes | Set `STUDYFORGE_STUDY_MODEL` to a locally installed Ollama text-model tag. The bridge sends retrieved excerpts and source numbers, with special rules for image formulas, Mermaid diagrams, and timestamped video examples. |
| Saved video descriptions | Enable the local vision model, select a video, and describe the nearest saved frame from a timestamp. When [Vidlizer](https://github.com/arizawan/vidlizer) is on PATH, the bridge runs its Ollama mode on that single JPEG in an isolated working directory. Direct local Ollama is the fallback. Saved captions become searchable and appear in chronological notes. |

Install all four pinned optional Python packages together with `python -m pip install -r engine/requirements-learning.txt`, or install only the packages you need. Set environment variables **before** launching the bridge. Install any models explicitly before going offline, keep Ollama on loopback, and disable Ollama cloud features with `OLLAMA_NO_CLOUD=1` on the Ollama server or the [server configuration](https://docs.ollama.com/faq#how-do-i-disable-ollamas-cloud-features). The bridge has no hosted inference fallback. [VLM Run Gateway](https://vlm.run/gateway) requires a key and publishes usage prices, so it is outside the zero-cost contract.

## Query and citation behavior

- Text search reads the shared index, without video decoding or frame inference. Image citations show a local thumbnail and offer the complete Markdown, including LaTeX and Mermaid blocks when recognized. Blocks are shown as text; extracted diagrams and formulas should be checked against the original.
- With **Allow local vision for visual questions** checked, a visual question about a video selects one existing keyframe. Use an explicit timestamp such as `What is written on the whiteboard at 12:34?` when possible. If no timestamp is given, the closest frame to a retrieved transcript/caption citation is used. The answer can make one local vision inference call. The frame is labelled with its actual sampled time; it may differ from the requested time.
- With no configured vision, the saved frame can still be cited for manual review. Ordinary queries never send frames to an inference model. Text answering and quiz generation can use the optional text model.
- **Export Markdown** downloads the full image extraction or chronological video notes. Models can mistranscribe or hallucinate; review extracted text, formulas, captions, and answers before using them for assessment.

## Limits and privacy

Images are capped at 12 MB and 24 megapixels; videos at 128 MB, one hour, and up to 48 sampled frames. The library retains up to 24 sources. One import or update runs at a time. An interrupted import is marked failed and its partial media are removed on bridge restart. Video originals are deleted after successful indexing. Image originals are retained privately for provenance alongside a normalized JPEG preview. **Remove source** deletes the retained media and searchable data. Browser offline caching excludes all learning endpoints.

Default storage: `engine/data/learning/` (or `$STUDYFORGE_DATA/learning/`). The portable `video.vctx/` directories contain a manifest, JSON index, and JPEG keyframes. Keep the bridge on loopback or behind the existing authorized private Tailscale Serve setup; anyone with access to that private bridge can access its learning library. Keep the library out of synced or shared folders if your media is private.

## Verification

```sh
cd engine
python -m unittest -v test_media_learning
```

Tests exercise real Tesseract and FFmpeg with synthetic fixtures, plus model-independent coverage for metadata, quiz routing, single-frame inference, saved captions, restart cleanup, and deletion. CI's browser integration exercises the upload-to-citation flow. Optional neural backends require separately installed models and are not assumed verified by these tests.
