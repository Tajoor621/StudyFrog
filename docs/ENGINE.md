# Local generation and video engine

The phone application includes source for templates, photo annotation, project
saving, PNG export and a phone-local MediaCodec/EGL reveal video encoder.
Android compilation and hardware encoder verification are still pending. AI generation in this build
runs on YOUR computer, not on the phone. A USB cable plus adb reverse works
without an internet connection after dependencies/models are prepared.

```bash
python3 -m venv .venv
. .venv/bin/activate
python3 -m pip install -r engine/requirements.txt
# Install the free FFmpeg executable using your OS's package manager.
python3 engine/server.py
```

Enter the printed session token in the Android app. The token is kept in
memory in the app. The service binds only to 127.0.0.1, requires a bearer
token on every route, rejects browser Origin headers, validates images and
limits the queue. Do not expose it on a public network. Job prompts/results
are stored in engine/data on the computer until you remove them; no cloud
synchronization occurs. Photos supplied to a model necessarily leave the
phone for that computer via your explicit job submission.

## Optional SD 1.x inference

Download a legally usable SD 1.x Diffusers-format model to your own computer.
This source does not redistribute model weights. Inspect its license and
hardware requirements. Supply safetensors weights, tokenizer, text encoder,
VAE, scheduler, feature extractor and safety_checker. This adapter does not
support SDXL/FLUX/ControlNet/OpenPose. It refuses models without a safety checker.
No `safety_checker=None` bypass is provided. Safety checking is imperfect;
this is not a guarantee of child-safe content or medical accuracy.

```bash
python3 -m pip install -r engine/requirements-models.txt
export STUDYFORGE_MODEL=/absolute/path/to/local-sd1-diffusers-folder
export HF_HUB_OFFLINE=1
export TRANSFORMERS_OFFLINE=1
python3 engine/server.py
```

The engine uses local_files_only=True and use_safetensors=True. CUDA is
selected if available, otherwise CPU float32. CPU inference can be extremely
slow. Attention slicing and VAE tiling reduce certain memory costs; they do
not create a phone inference runtime or guarantee fast high-resolution output.
Negative prompts, seeds, inference steps and source-photo img2img are passed
to the actual pipeline. A single worker owns the model to avoid concurrent
VRAM exhaustion. Failed jobs report errors; no fake AI fallback is returned.

## Native client / command-line API

Set STUDYFORGE_TOKEN before launching the service if you need a stable token.
Do not commit real tokens. Use the printed session token otherwise.

```bash
export STUDYFORGE_TOKEN='replace-with-your-session-token'
curl -H "Authorization: Bearer $STUDYFORGE_TOKEN" http://127.0.0.1:8765/health
curl -H "Authorization: Bearer $STUDYFORGE_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"kind":"image","prompt":"botanical study of a fern","negative":"blurry","width":768,"height":432,"steps":20,"seed":42}' \
  http://127.0.0.1:8765/jobs
# Take the returned id, then:
curl -H "Authorization: Bearer $STUDYFORGE_TOKEN" http://127.0.0.1:8765/jobs/RETURNED_ID
curl -H "Authorization: Bearer $STUDYFORGE_TOKEN" http://127.0.0.1:8765/results/RETURNED_ID -o result.png
```

`kind=diagram` provides an explicitly labeled geometric template. `kind=video`
requires `image` containing base64 PNG/JPEG data. The same `image` field with
`kind=image` enables img2img. Source uploads are capped; standard image
inference dimensions are 256–1024, multiples of eight.

## Video output

The implemented video exporter uses FFmpeg to produce **30 seconds, 1920×1080,
24 fps, H.264**. It progressively reveals the supplied still from left to right.
It does not infer a subject's motion, simulate an artist's brush sequence,
accept camera instructions or add audio. Resolution is 1080p but source detail
remains limited by the input. It is not a claim of ultra-realistic AI video.
The full-duration output is verified separately with ffprobe in validation.

## Verify without downloading AI models

```bash
cd engine
python3 -m unittest -v
```

Tests cover invalid requests, malformed images, medical prompt acceptance,
authentication, path traversal, job persistence, genuine diagram output,
missing-model failure and real video frames. Diffusion quality cannot be
validated without model files and target hardware.

## API-only tiled refinement up to 8K

Submit `kind=refine`, `width=7680`, `height=4320`, and a base64 `image`
with your refinement prompt. The engine resizes the source, then performs
img2img diffusion on overlapping 768-pixel patches and feathers their edges.
This adds model-derived patch detail; it is not native full-frame 8K denoising.
Global consistency, seams and hardware feasibility must be evaluated.
Every patch uses the same model safety checker. It can take a very long time;
there is no speed guarantee. This API route is not exposed by the phone UI.
The output image is allocated on your computer, not the phone.

## Android phone-local reveal encoder

The separate “Create 30-second video on this phone” action uses Android
MediaCodec, EGL and OpenGL ES 2.0 to draw 720 frames onto an encoder surface
and mux H.264 MP4. It does not use FFmpeg or a server. Keep the app open;
this is not a background service. It is implemented source but has not yet
been compiled or tested on hardware. Devices may reject the 1080p encoder
configuration; errors are reported rather than claiming success.
