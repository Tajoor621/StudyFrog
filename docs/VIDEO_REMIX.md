# Video background remix — 0.10.0

Upload an existing video, keep the source people/faces, their performance and
original sound, and replace the background with an imported or locally generated
image. This is local compositing: it does not transfer someone's face onto a
new actor, clone a voice, invent lip movement or turn sound alone into motion.
The original first audio track is re-encoded as AAC, not copied bit-for-bit.
There is no paid service, account, API or per-video charge.

## How to use it

1. Import or generate the new background on the studio canvas. To create one
   from written instructions, use the existing local image engine first.
2. Open **Video remix · keep people and original sound** on web/iOS, or
   **Replace uploaded video background · keep faces and sound** on Android.
3. Choose an SDR MP4, MOV or WebM, confirm permission to edit the people, video
   and soundtrack, select a profile, start time and duration, then send it.
4. Person mode removes the scene around people using the configured local ONNX.
   Green-screen mode needs no model; green clothing disappears too. Hair,
   transparent objects, hands, fast movement, occlusion and crowds can have
   imperfect edges. Review the result before use.
5. Web/iOS poll and offer the result; Android uses **Check current engine job /
   download result**. Cancel uploads or jobs with their controls. Download/share
   the MP4. Manage computer jobs to delete retained results and prompt history.

All three clients use the same computer engine. Android uses the existing USB
`adb reverse tcp:8765 tcp:8765` connection; iOS uses its configured trusted
`.local` host; mobile browsers use the existing private HTTPS bridge setup.
This release does **not** run person segmentation/AI remix entirely on a phone.
A mobile budget also works when a phone controls a larger computer. Static
GitHub Pages cannot accept/process video itself; open the local/private bridge.
Existing still-reveal and LTX motion modes retain their separately documented
30-second behaviour. These new budgets apply to uploaded-video remix.

## Conservative output budgets

| Budget | Maximum clip | Maximum landscape box | Frame rate | Input cap |
| --- | --- | --- | --- | --- |
| Mobile | 15 seconds | 854 × 480 | 15 fps | 64 MB |
| Laptop | 30 seconds | 1280 × 720 | 24 fps | 128 MB |
| Desktop | 60 seconds | 1920 × 1080 | 30 fps | 128 MB |

Portrait boxes are rotated; framing/aspect ratio is preserved. Dimensions are
even for H.264. Small inputs are not upscaled. Output is normalized to the
profile's frame rate, which can duplicate/drop source frames; it does not
synthesize extra motion. Clip ends are rounded to encoded frame boundaries.
Input decode is limited to 4K/8.8 million pixels, known duration up to one hour,
with selectable start 0–3600 seconds. Extremely narrow aspect ratios and non-square pixels are rejected. HDR input is rejected; export SDR first.
The export cap is 256 MB, processing deadline 30 minutes, with at least 512 MB
free disk required before processing. One worker streams frames rather than
loading a complete clip into memory. Two waiting jobs are allowed.

These are engineering caps, **not benchmark results or guarantees of realtime
rendering, quality, thermal safety or compatibility on every device**. Choose
based on the processing machine, use a short test first and lower the budget if
needed. The operator sets `STUDYFORGE_VIDEO_PROFILE` to `mobile`, `laptop`
(default), or `desktop`. Clients cannot exceed that ceiling; it is checked both
when submitting and when execution starts. A desktop selection does not itself
prove adequate hardware. No automatic form-factor or GPU-speed claim is made.

## Free local setup

Use a Python 3.11 or 3.12 virtual environment and install the optional
pinned CPU dependencies:

```bash
python -m pip install -r engine/requirements-remix.txt
```

Install free FFmpeg/ffprobe on PATH. No new mobile SDK or permissions are
needed: document pickers grant only the chosen file. Green-screen remix then
works without any trained model or GPU.

For person removal, obtain the **FP32** model
`human_segmentation_pphumanseg_2023mar.onnx` from the
[official OpenCV Zoo directory](https://github.com/opencv/opencv_zoo/tree/2027dd2f5a8a5746b5d4964900a0465afc6d3a53/models/human_segmentation_pphumanseg).
The model directory's [Apache 2.0 license](https://github.com/opencv/opencv_zoo/blob/2027dd2f5a8a5746b5d4964900a0465afc6d3a53/models/human_segmentation_pphumanseg/LICENSE)
applies. Save the actual Git LFS model bytes, not the small pointer text. This
revision's LFS pointer records 6,163,938 bytes and SHA-256:

```text
552d8a984054e59b5d773d24b9b12022b22046ceb2bbc4c9aaeaceb36a9ddf24
```

Verify your download against that digest. Model imports are explicit; the app
never downloads models automatically. Keep the source/license alongside it.
The preprocessing/output contract follows the official PPHumanSeg adapter:
RGB normalized to [-1,1], 192×192 input, two-class argmax, resized foreground
mask with small feathered edges. This is person segmentation, not identity
recognition or a face/voice embedding database.

PowerShell (use your actual local model path):

```powershell
$env:STUDYFORGE_SEGMENT_MODEL = (Resolve-Path models/human_segmentation_pphumanseg_2023mar.onnx).Path
$env:STUDYFORGE_SEGMENT_SHA256 = '552d8a984054e59b5d773d24b9b12022b22046ceb2bbc4c9aaeaceb36a9ddf24'
$env:STUDYFORGE_VIDEO_PROFILE = 'laptop'
./scripts/start-windows.ps1 -Mode Engine
```

Linux/macOS (after setting the existing private engine token):

```bash
export STUDYFORGE_SEGMENT_MODEL="$PWD/models/human_segmentation_pphumanseg_2023mar.onnx"
export STUDYFORGE_SEGMENT_SHA256=552d8a984054e59b5d773d24b9b12022b22046ceb2bbc4c9aaeaceb36a9ddf24
export STUDYFORGE_VIDEO_PROFILE=laptop
python3 engine/server.py
```

Start the existing web bridge in a second terminal with the same private token,
then open `http://127.0.0.1:8766`. Native clients use their existing connection
settings. No public engine binding, router port forwarding or cloud upload is
required. Use the existing trusted-network/USB/private HTTPS instructions.

## Privacy, API and recovery

Raw uploads use authenticated `POST /video-inputs` (bridge
`/engine/video-inputs`), require a length and stream at most 128 MB. The server
probes actual media rather than trusting names/MIME. It accepts no client file
paths or remote media URLs. Host/origin checks, exact proxy routes, no redirects,
private tokens, no request logging and no-store responses remain in effect.
There is at most one upload at a time and four unclaimed uploads. Unused uploads
expire after 30 minutes and are removed on the next upload or engine restart;
there is no background deletion timer. Clients delete unclaimed successful
uploads when submission fails, when possible. An upload that completes after a
client disconnect can remain until this expiry cleanup.

A remix job requires `kind=remix`, `video_input`, PNG `image` (background),
`profile`, `remix_mode=person|green`, `start`, `seconds`, and
`rights_confirmed=true`, plus its descriptive `prompt`. Source uploads can be
claimed once only. They become a job-owned input file; it is removed on success,
failure/cancellation or restart. Partial output is removed on failure. Process
watchdogs kill blocked decode/encode pipes on cancellation or timeout. Results
include an MP4 edit comment and a JSON `remix_report`, including dimensions,
frames, audio handling and actual model digest when used. No measured-speed or
scientific-accuracy status is inferred. Original device files remain intact.

Native creative/family guards still apply to file selection and transfer. The
local computer engine and a separate browser do not implement the phone's
parent PIN; they must not be described as a certified child-safe environment.
No identity verification is implied by the user's editing-permission check.
Independent privacy/family review and physical-device qualification remain
pending in the existing evidence register.

## Verification scope

Tests use synthetic green/red footage with an actual 440 Hz soundtrack: they
check changed background pixels, preserved foreground pixels, H.264/AAC output,
trim/duration, frame count, audio frequency/sync, silent input, cancellation,
one-use upload ownership, deletion, auth, limits and incomplete upload cleanup.
The model contract test verifies normalization and shape using a fake network;
it is not real-model evidence. CI separately downloads the checksum-pinned
Apache-licensed ONNX into temporary storage for an explicitly enabled runtime
smoke test. Synthetic smoke input does not validate real human-edge quality.

Browser integration checks permission/limits before upload and the submitted
remix payload using explicit network fixtures. iOS tests bound the file-provider
copy and verify that rejected inputs leave no temporary copy. Android/iOS build
and runtime CI results are recorded in the validation report. Physical phone/
laptop/desktop performance, crowded/hair/fast-motion quality and user-provided
soundtracks still need representative device/media testing.
