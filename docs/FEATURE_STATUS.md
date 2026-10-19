# Feature status — 0.7.0

Evidence: [VALIDATION.md](../VALIDATION.md). Implemented code, automated tests and real-model/device evaluation are distinct.

| Requirement | Implemented / remaining boundary |
| --- | --- |
| Prompt-first generation | Android/iOS prompts, negative prompts, seeds and model inference controls |
| Visual directions | Both platforms save style/detail/realism/scientific-intent instructions with effective-prompt preview and length checks; no factual-accuracy guarantee |
| Native image generation | Shared pinned CPU SD 1.x GGUF adapter, manual import, img2img, 256/512px and RAM guards; real weights/phone speed untested |
| Computer image generation | Local SD 1.x adapters and both-platform clients; explicit local weights |
| Image guidance | Computer Canny/depth/pose ControlNet and IP-Adapter; Canny computes edges; prepared depth/pose plus optional local CPU photo estimators; native-phone guidance and pretrained-weight evaluation remain missing |
| 8K stills | Computer tiled 4K/8K path and Android original-file export; no native-phone 8K denoising or verified model quality/seams |
| Layers | Both platforms: up to 16 ink/raster layers, reversible masks, transforms, visibility/opacity/blends, ordering, document undo and persistent assets |
| Graphics tools | Android shapes/text, booleans, SVG, guides/symmetry; iOS PencilKit pen/pencil/marker; full node/typography/print tools and vector parity incomplete |
| Projects | Android schema 4 ZIP backups and legacy reads; iOS versioned native assets and legacy defaults; folders, search and metadata updates; cross-platform editable interchange missing |
| Video | Phone still-reveal implementations and optional computer LTX adapter, five chained segments/720 frames/24fps/30sec; physical encoders and actual models untested |
| Motion detail | LTX denoises at 768×448 before scaling to 1080p; no native 1080p-detail or ultra-realism guarantee |
| Photo notes | Local Tesseract OCR, editable review and explicit bounded prompt handoff on Android/iOS/web; no factual validation or automatic label placement |
| Web | Local SD/ControlNet/LTX jobs, original-file export, cancellation/history/deletion, OCR, secure same-origin bridge and static-only offline caching; browser engine tests use fixtures |
| Science/medical | Ten original referenced conceptual diagrams and body-to-cell/mitosis paths; broad reviewed atlas, validated CT/MRI overlays and factual verification and model-consistent semantic zoom missing |
| Family access | Parent PIN, persisted 15-minute cooldown after five failures, restricted bundled-study workspace and adult self-declaration on both platforms; no unrestricted Adult Mode or independent child-safety/bypass certification |
| Foundations | Room/DataStore, onboarding, Material 3, reminders, default-off optional Firebase, accessibility semantics, tests and signing config; configured Firebase/physical accessibility evaluation pending |
| Packaging | AAB density/ABI splits, JNI, extractNativeLibs and binary-manifest verification |
| Cost/privacy | No paid inference/backend/distribution console; local storage, manual model import and explicit optional computer transfers |
| CI | Platform-input push filters and a full emulator workflow for test-only updates; full PR/manual runs; Android lint/JVM/assembly/split/emulator and iOS simulator checks |

See [FAMILY_ACCESS.md](FAMILY_ACCESS.md), [GENERATION_CONTROLS.md](GENERATION_CONTROLS.md), [EDITOR.md](EDITOR.md) and [MASTER_SPEC_AUDIT.md](MASTER_SPEC_AUDIT.md) for exact boundaries.

Remaining work includes real SD/LTX/guidance evaluation on target hardware, physical encoders and low-memory/process-death/storage testing, independent science/family-control review, advanced graphics/iOS parity, cross-platform project exchange and owner-signed device releases. Passing builds do not establish the complete master specification or a “10/10” rating.

## 0.8.0 portable layers

Android/iOS import and export local layered raster copies with prompts/folders,
layer ordering/settings/transforms and checksummed PNG assets. Export disclosures
explain rasterized strokes/text/masks and bounded resolution; original native
projects remain editable. Lossless native stroke/vector/mask-path interchange
is still incomplete. See [Portable projects](PORTABLE_PROJECTS.md).
