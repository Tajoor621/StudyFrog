# Generation controls and local image guidance — 0.6.0

## Prompt controls

Visual directions are off by default and preserve the original prompt. Enabling them appends visible style/detail/realism instructions. Styles cover natural, scientific, diagram, photographic, graphic concept and watercolor. Scientific intent requests omitted uncertain details and non-invented labels; it is not factual verification or a measured accuracy percentage.

The effective prompt is previewed. Inputs are never silently truncated: combined prompts above 2,000 characters must be shortened or directions disabled. Negative prompting remains separate. Android/iOS store profiles with projects/drafts; Android also remembers the last selection. iOS saves seed, 10–40 inference steps, photo strength and resolution with legacy defaults. Phone inference remains 256/512px with existing RAM guards.

## Optional computer guidance

Both apps submit image/photo jobs to the local engine. Android uses loopback/USB adb-reverse; iOS uses a configured local hostname and explicit transfer consent. Tokens are memory-only. No hosted inference API is added.

| Mode | Input | Local model |
| --- | --- | --- |
| None | Prompt, optional img2img photo | Complete SD 1.x Diffusers folder with safety checker |
| Canny | Photo; CPU OpenCV computes edges | Compatible SD 1.x Canny ControlNet |
| Depth | Prepared grayscale depth map | Compatible depth ControlNet |
| Pose | Prepared OpenPose RGB map | Compatible pose ControlNet |
| Reference | Reference photo | SD 1.x IP-Adapter safetensors and matching CLIP image encoder |

Depth/pose modes consume prepared maps; they do not run automatic detectors on photos. One mode is supported per image job. Native-phone guidance and guidance on video/refinement jobs are not implemented. Inputs are resized/center-cropped to job aspect ratio. Strength ranges 0–1 and does not guarantee identity or pose fidelity.

Install engine/requirements-models.txt in your own environment. It pins Diffusers 0.32.2, Transformers 4.49.0, Torch 2.6.0, NumPy 1.26.4 and OpenCV headless 4.11.0.86 with existing dependencies. Retain compatible model cards/licenses. Set only the local modes you use:

    export STUDYFORGE_MODEL=/absolute/path/to/sd1-diffusers
    export STUDYFORGE_CONTROLNET_CANNY=/absolute/path/to/canny-controlnet
    export STUDYFORGE_CONTROLNET_DEPTH=/absolute/path/to/depth-controlnet
    export STUDYFORGE_CONTROLNET_POSE=/absolute/path/to/pose-controlnet
    export STUDYFORGE_IP_ADAPTER=/absolute/path/to/sd1-ip-adapter
    python engine/server.py

ControlNet folders need Diffusers config and safetensors. The reference folder needs ip-adapter_sd15.safetensors plus a complete image_encoder/ directory with compatible config/safetensors. Alternate architectures, FaceID and SDXL adapters are not inferred.

Model/encoder loads explicitly use local files only. Encoder auto-loading is disabled after the explicit local load. Missing weights fail clearly; no downloads or substitute mode occur. Changing mode/model path releases the cached pipeline before replacement. Existing safety-checker/guard behavior is retained. Hardware, storage and compatible weights must be supplied.

GET /health reports configured guidance folders. Image jobs accept control=none/canny/depth/pose/reference and control_scale=0–1, with the existing image/prompt/token limits. Model paths come from the operator environment, never client requests.

## Source and verification

Android: generation/GenerationProfile.kt, generation/ProfileCodec.kt, MainActivity.kt and editor/EditorSession.kt. iOS: GenerationProfile.swift, StudyForgeApp.swift, ProjectStore.swift, NativeDiffusion.swift and ComputerEngine.swift. Engine: image_guidance.py, server.py and requirements-models.txt.

Tests exercise real CPU Canny edges, prepared-map normalization, validation, missing-config failure, local-only loader arguments, cache replacement/reuse and strength propagation. Model execution uses stubs; no real ControlNet/IP-Adapter quality/VRAM benchmark is claimed. Validate actual weights, map formats, repeated mode switching, cancellation, RAM/VRAM and instruction adherence on target hardware.

For lightweight tests: install engine/requirements-test.txt, then run python -m unittest -v from engine/. Android uses existing dependencies; iOS uses system frameworks. No paid service or mandatory backend is added.

Primary API references: [ControlNet](https://huggingface.co/docs/diffusers/v0.32.1/en/api/pipelines/controlnet) and [IP-Adapter loader](https://huggingface.co/docs/diffusers/v0.32.1/en/api/loaders/ip_adapter), documenting the 0.32 API family used by 0.32.2.

For iOS over Wi-Fi, bind the engine to your computer's private LAN address with STUDYFORGE_BIND and use its .local hostname on a trusted network. Android can use adb reverse tcp:8765 tcp:8765. See [ENGINE.md](ENGINE.md) for connection/authentication details; no internet port forwarding is required.
