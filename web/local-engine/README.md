# Free local web generation — 0.7.0

The web Studio connects to your own **StudyForge SD/ControlNet/LTX engine**, or
keeps the earlier Ollama text-to-image option. No paid inference API, app account or
credits are added. Models, compatible hardware and electricity remain necessary.

## StudyForge engine and web app on one computer

1. Follow [engine setup](../../docs/ENGINE.md) and optional
   [photo guidance/OCR setup](../../docs/PHOTO_STUDY_WORKFLOW.md). Supply compatible
   local weights and install FFmpeg for video.
2. Set `STUDYFORGE_TOKEN` to the same long random token in the engine and bridge
   terminals. Run `python engine/server.py` in the model environment.
3. In another terminal, with that token set, run:

   ```bash
   python3 web/local-engine/bridge.py
   ```

4. Open `http://127.0.0.1:8766/`. Choose **StudyForge SD / ControlNet / LTX** and
   **Check local engine**. Status distinguishes model folders and tool availability.
5. Write your prompt/negative prompt. **Prompt only** preserves your instructions; other visual directions are appended and shown in **Prompt sent to computer**. Choose output, seed, steps, guidance
   and photo transformation strength. Photo conversion/guidance/refinement/video
   send the current canvas to your computer. Text-only image jobs send no canvas.
6. Use **Download original result** for full-size PNG/MP4. An image's 960×540
   editor preview is smaller than the original; generation participates in Undo.
7. **Manage computer jobs** reopens completed results, requests cancellation at
   the next safe step, or deletes inactive result/prompt history. Reloading a page
   does not cancel a computer job; use this manager to recover it.

Image guidance applies only to image jobs. Choose None for refinement/video.
LTX needs the configured local motion model/CUDA; its denoising resolution is
768×448 before 1080p delivery. Reveal video is a still reveal, not AI subject
motion or an inferred brush sequence. Tiled 4K/8K refinement can take a long time;
there is no quality, temporal realism or phone-speed guarantee.

OCR uses installed Tesseract languages. Read a canvas photo, correct its text in
**Review and correct extracted text**, then explicitly copy reviewed notes to the
prompt. It does not fact-check science, automatically place labels or silently
truncate long notes.

## Ollama option

Install [Ollama](https://ollama.com/) and a compatible image model yourself. Start
Ollama on loopback port 11434, select the Ollama backend, connect, choose an
installed image model and generate. This adapter is text-to-image only; photo,
negative conditioning, guidance, refinement and video use the StudyForge engine.
An installed text-only model may be listed but cannot return an image.

## Phone access, privacy and boundaries

See [private HTTPS Android connection](ANDROID_SECURE_CONNECTION.md). GitHub Pages
serves the editor offline after first load, but **cannot run your computer models**.
The phone opens the private bridge URL for generation. The bridge remains bound
to loopback; do not expose it publicly or enable Tailscale Funnel.

The bridge serves only explicit public web assets, rejects unrelated Host/Origin
values, never follows engine redirects, forwards only fixed engine routes, and
keeps the bearer token on your computer. Only the exact configured private
Tailscale hostname is allowed. Tailnet access controls are the remote security
boundary; there are no per-user accounts. Limit access to your trusted devices.

Generated results/OCR/job history are `no-store` and bypass the service worker.
Only the static interface is cached. Engine jobs remain on the computer until
deleted; browser projects remain until deleted or site storage is cleared. Export
important originals. Data is sent to the computer only for the explicit actions
above. Native parental controls do not apply to this separate web edition.

Browser CI uses engine fixtures to check integration. It does not establish real
SD/ControlNet/LTX quality or physical Samsung-to-computer connectivity.
