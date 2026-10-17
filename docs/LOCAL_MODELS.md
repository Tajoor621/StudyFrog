# Local models and physical testing

## Native phone images

`shared/runtime.cmake` pins stable-diffusion.cpp to
`f0aac524d3f29140e60ba78f3cfb16d6acea87db`, including its pinned ggml submodule.
Android JNI and the iOS C bridge use this CPU runtime. MIT notices ship in both
apps. No weights, automatic downloads or paid APIs are included.

Prepare a legally usable **complete SD 1.x GGUF**. Read its model card/license
and verify the publisher's checksum. Denoiser-only GGUF files and unrelated
model families are insufficient. A supported conversion with the pinned upstream
CLI uses:

```bash
sd-cli --mode convert -m original-sd1.safetensors -o image.gguf --type q4_0
```

The CLI is an upstream tool, not an app command. Choose a complete model: the
phone importer accepts one file, not a multi-component package. Import through
the studio model action (Android) or Settings (iOS). Imports check GGUF magic,
32 MB–4 GB bounds and SHA-256; these checks do not establish compatibility or
content safety. Android stores it at `no_backup/models/image.gguf`; iOS models
are private and excluded from backup. Loading failures are reported.

256px requires nominal 6 GB RAM; 512px requires 8 GB. Android also checks
available memory. These guards cannot prevent every OS memory termination.
The engine uses at most four CPU threads and VAE tiling; no mobile GPU/NPU
acceleration or latency guarantee is claimed. Android uses a cancellable
foreground job; stay in the app on iOS. Prompts/photos stay on the device unless
you explicitly submit a computer job. The runtime is not a safety classifier.

## Android commands

Use JDK 17, SDK 35, Build Tools 35.0.0, NDK 27.2.12479018, CMake 3.22.1.
Initial source/dependency downloads require internet; prepared inference is offline.

```bash
./gradlew --no-daemon lintDebug testDebugUnitTest assembleDebug -PfirebaseEnabled=false
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew --no-daemon connectedDebugAndroidTest -PfirebaseEnabled=false
bash scripts/device-smoke.sh
```

Updating an existing installation requires its existing certificate; scripts do
not uninstall apps or clear user data. On a debuggable ARM64 phone, import a
compatible model through the app, then explicitly run real inference:

```bash
./gradlew --no-daemon connectedDebugAndroidTest -PfirebaseEnabled=false -Pandroid.testInstrumentationRunnerArguments.runRealInference=true
adb exec-out run-as com.scenio.mth cat files/device-test/report.json > device-report.json
adb exec-out run-as com.scenio.mth cat files/device-test/image.png > device-output.png
```

The test checks a nontrivial output; a human must evaluate image quality.
Normal CI skips it. `.github/workflows/physical-device.yml` runs it manually on
an owner's self-hosted `studyforge-device` runner in the `physical-device`
environment. No device was supplied and this workflow was not dispatched here.

The studio benchmark export records elapsed milliseconds, sampled peak native
heap, model hash, runtime revision, SDK and ABI, without prompt/photo content.
Sampled native heap is not total process/PSS peak. Measure first/warm runs,
thermal throttling, low memory and cancellation on actual target phones.

## Optional computer LTX motion

Install FFmpeg and pinned dependencies; prepare a complete compatible LTX
image-to-video Diffusers folder locally, with safetensors weights and all model
components. No job downloads weights. CUDA is required; failure never silently
substitutes still reveal. See [ENGINE.md](ENGINE.md).

```bash
python3 -m venv .venv
. .venv/bin/activate
python3 -m pip install -r engine/requirements-models.txt
export STUDYFORGE_VIDEO_MODEL=/absolute/path/to/local-ltx-image-to-video-folder
export HF_HUB_OFFLINE=1
export TRANSFORMERS_OFFLINE=1
python3 engine/server.py
```

Each of five segments requests 145 frames and drops its first conditioning
frame, contributing 144. The previous final frame conditions the next segment.
Motion/negative prompts and incremented seeds feed real model calls. The result
is 720 frames at 24fps: 30 seconds. Denoising is 768×448, then letterboxed/scaled
to 1920×1080. This does not establish native 1080p detail, seam-free continuity,
ultra-realism or biological accuracy. Real GPU/model execution is untested here.

Android can use USB:

```bash
adb reverse tcp:8765 tcp:8765
```

For an explicitly chosen private LAN connection, set `STUDYFORGE_BIND` to the
computer's actual private IPv4 before starting the server. iOS accepts its
`.local` hostname or simulator localhost and requests consent before transfer.
Local HTTP is unencrypted: use a trusted network, never public exposure.
Default binding is loopback; bearer tokens are required and browser Origin
requests rejected. Tokens stay in app memory. Jobs/prompts persist in
`engine/data` on the computer until explicitly deleted.

No real weights, compatible CUDA GPU or physical ARM64/iPhone were available in
this implementation session. Unit/simulator/frame tests establish mechanics,
not generative quality, mobile performance, battery life or realism.
