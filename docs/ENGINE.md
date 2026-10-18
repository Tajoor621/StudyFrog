# Local generation and video engine

The phone application includes source for templates, photo annotation, project
saving, PNG export and a phone-local MediaCodec/EGL reveal video encoder.
Android compilation is verified in CI; physical hardware encoding remains unverified. The optional Python engine runs on YOUR computer. Phone-native image inference
is separate; see [LOCAL_MODELS.md](LOCAL_MODELS.md). A USB cable plus adb reverse works
without an internet connection after dependencies/models are prepared.

```bash
python3 -m venv .venv
. .venv/bin/activate
python3 -m pip install -r engine/requirements.txt
# Install the free FFmpeg executable using your OS's package manager.
python3 engine/server.py
```

Enter the printed session token in the Android app. The token is kept in
memory in the app. The service defaults to 127.0.0.1 (explicit private-IPv4 binding is optional), requires a bearer
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
not guarantee fast high-resolution output. The separate phone-native runtime
has its own smaller resolutions and memory limits.
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

## Computer tiled refinement up to 8K

Submit `kind=refine`, `width=7680`, `height=4320`, and a base64 `image`
with your refinement prompt. The engine resizes the source, then performs
img2img diffusion on overlapping 768-pixel patches and feathers their edges.
This adds model-derived patch detail; it is not native full-frame 8K denoising.
Global consistency, seams and hardware feasibility must be evaluated.
Every patch uses the same model safety checker. It can take a very long time;
there is no speed guarantee. Android exposes computer 4K/8K refinement and retains the original result file
for full-resolution export; the canvas uses a bounded preview.
The output image is allocated on your computer, not the phone.

## Android phone-local reveal encoder

The separate “Create 30-second video on this phone” action uses Android
MediaCodec, EGL and OpenGL ES 2.0 to draw 720 frames onto an encoder surface
and mux H.264 MP4. It does not use FFmpeg or a server. Keep the app open;
this is not a background service. It compiles in CI but has not been encoder-tested on physical hardware. Devices may reject the 1080p encoder
configuration; errors are reported rather than claiming success.

## Job management

The Android studio includes engine history and confirmed cancellation/deletion.
All routes require the same bearer token:

- `GET /jobs`: latest 50 job summaries.
- `POST /jobs/<id>/cancel`: request cancellation at the next safe step.
- `DELETE /jobs/<id>`: delete a finished job and its stored result.

Active jobs must be cancelled before deletion. Diffusion checks cancellation
between denoising steps; tiled refinement checks between patches. Video encoding
terminates its FFmpeg subprocess on cancellation. No phone project is deleted
by a computer-engine job deletion. Strength and guidance values are bounded;
the Android photo workflow exposes transformation strength and seed control.


## Model-generated subject motion

`kind=motion` requires base64 `image`, with motion/camera instructions in
`prompt`; negative prompts, seed and steps are supported. Configure a complete
local LTX image-to-video model using `STUDYFORGE_VIDEO_MODEL` and install
`requirements-models.txt`. CUDA, local files and safetensors are required.

Five chained segments produce 720 frames/24fps/30sec, H.264 1920×1080 delivery.
Inference is 768×448 then scaled. Real-model quality, seams and GPU requirements
remain unverified. A missing model fails explicitly; no silent still-reveal
fallback occurs. Cancellation checks denoising steps and encoding frames and
removes partial results. Android/iOS expose optional computer motion actions.
The separate `kind=video` reveal route retains its original behavior.
See [setup and testing](LOCAL_MODELS.md).


### Image/video memory handoff

A motion job discards the cached SD image pipeline before loading LTX and
collects unused model allocations after completion/failure. The next image job
reloads SD. This reduces retained image/video memory overlap; it is not a GPU
VRAM guarantee. Model-lifecycle tests use stubs; actual CUDA/weights still need
measurement. Failed CUDA cache cleanup does not terminate the job worker.


Optional local Canny/depth/pose ControlNet and IP-Adapter image modes are documented in [GENERATION_CONTROLS.md](GENERATION_CONTROLS.md). They require separately prepared SD 1.x weights; depth/pose inputs are prepared maps, not automatic detector outputs.
