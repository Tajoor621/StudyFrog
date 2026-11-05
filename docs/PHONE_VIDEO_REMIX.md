# Offline phone video remix — 0.11.0

Android and iOS now include an on-device path: preserve the source performance
and first AAC audio track while replacing its background with the current
canvas. It uses no network, computer, paid API or account during rendering.
No novel face/voice reenactment is performed. The existing computer workflow
remains available for larger clips, non-AAC audio and heavier generation.

## Setup and use

1. Obtain the free FP32 `human_segmentation_pphumanseg_2023mar.onnx` from the
   [official source and checksum instructions](VIDEO_REMIX.md). This is the
   6,163,938-byte Apache-2.0 model with SHA-256
   `552d8a984054e59b5d773d24b9b12022b22046ceb2bbc4c9aaeaceb36a9ddf24`.
   A GGUF model or Git LFS pointer file is not interchangeable with it.
2. Use **Import phone person-removal ONNX** on Android or **Offline phone video
   remix → Import person-removal ONNX** on iOS. Only the published checksum is
   accepted; a rejected import keeps the previous installed model.
3. Import/generate a new background on the canvas. Open the offline phone remix
   controls, choose Person or Green screen, set start/duration, confirm editing
   permission, select a video and render. Green screen needs no model.
4. Keep the app open; export/share the MP4 when ready. Cancel stops the operation
   and deletes temporary source/unfinished output. The original source remains
   intact. Segmentation weights stay private and excluded from backups.

The phone cap is **15 seconds, up to 854×480 (portrait box rotated), 15 fps,
64 MB input**, SDR sources up to 1080p/one hour. Small sources are not upscaled;
frame count rounds to output frame boundaries. Extreme aspect ratios are
rejected. Selectable start is 0–3600 seconds. Original first AAC audio is copied
on its source timeline; silent sources remain silent. Other source audio/HDR/
larger inputs require conversion or the computer engine. Inspect edges/sync.
Android backgrounds above eight million pixels are rejected before compositing.
At least 512 MB free storage is required; low memory, excessive heat, cancellation
and a 30-minute render deadline stop processing. iOS requires at least 2 GB
physical RAM. These are engineering guards, not measured device speed/safety
certification; a short test on the actual device is necessary.

## Implementation and privacy

ONNX Runtime **1.20.0**, MIT-licensed, runs the known PPHumanSeg graph on CPU
with bounded thread counts. Android uses the pinned Maven Android artifact;
iOS uses the exact-version official Swift package and its checksum-verified
binary target. No weights are downloaded implicitly by the production app.
RGB normalization/model output match the computer adapter. One decoded frame
and its mask are processed at a time, then sent to the OS H.264 encoder.
Native file pickers grant access only to the selected file; no new broad media,
camera/microphone or network permissions were introduced. Family guards cover
selection, processing and export. Phone generation does not invoke the computer
engine or send an upload request.

Per-frame source seeking can be slow on long-GOP footage and older hardware.
Exact native codec/rotation, hair/crowd/transparent-object masking, thermal,
low-memory/process-death and AAC sync behaviour must be qualified on physical
Android/iOS devices. No realtime, perfect-identity/mask, clinical or 10/10 claim
is made. Physical/independent evidence remains pending in the existing register.

## Test preparation

CI explicitly downloads the checksum-pinned ONNX to test-only assets and makes
synthetic green/red video, silent video and a timed audio/visual marker. These
inputs contain no real person or user media; they are not human-edge quality
or physical hardware certification. Native suites load the actual model,
exercise person/green encode, preserve/suppress source audio appropriately,
check dimensions/foreground/background, reject bounds and remove canceled
outputs. Use the following before local native test builds (FFmpeg required):

```bash
bash scripts/prepare-phone-fixtures.sh app/src/androidTest/assets
bash scripts/prepare-phone-fixtures.sh ios/NativeFixtures
```

Fixtures are ignored by Git and are not production app model assets. See the
[current verification record](../VALIDATION.md) for actual completed evidence.
