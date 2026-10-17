# Feature status — 0.4.0

Evidence: [VALIDATION.md](../VALIDATION.md). Implementation and real-model quality
validation are separate stages.

| Requirement | Implemented / remaining boundary |
| --- | --- |
| Prompt-first Android | Studio and six mode choices; modes share pipelines |
| Phone-local images | Pinned native CPU SD 1.x runtime, GGUF import, img2img, negative prompt/seed, 256/512px; actual weights not tested |
| Background generation | Android foreground WorkManager, progress, scoped cancellation, persistent requests and result recovery; iOS keeps app open |
| Computer image engine | Local SD 1.x Diffusers text-to-image/img2img, requires separately prepared weights |
| 8K | Android exposes computer tiled 4K/8K refinement and original-file export; model quality/seams untested; no native 8K denoising |
| Editing | Android base plus up to 16 ink layers, normal/multiply/screen, opacity/visibility, erasing, pressure ink, shapes/text and SVG |
| Professional graphics | Arbitrary raster layers/full masks, booleans, advanced typography/print tools and tilt stabilization incomplete |
| Projects | Android schema 3, legacy reads, ZIP backups, search/rename/delete/drafts; iOS private editable store/drafts; folders/cross-platform interchange incomplete |
| Still-reveal video | Android MediaCodec, iOS AVAssetWriter and computer FFmpeg implementations; physical encoders untested |
| Subject motion | Optional computer LTX integration, five chained segments, 720 frames/24fps/30sec; CUDA and complete model required, real inference untested |
| Motion resolution | Model denoising 768×448 then 1920×1080 delivery; no native 1080p detail or ultra-realism claim |
| Science/medical | Illustrative templates and hierarchy selector; semantic atlas, reviewed labels, CT/MRI and scientific accuracy validation missing |
| Safety | No unrestricted explicit-sexual Adult Mode or evaluated parental controls; no child-safe certification |
| iOS | XcodeGen project/shared native runtime, SwiftUI/PencilKit layers, projects/drafts, photo/PNG export, appearance/reminders, reveal and computer motion client; full Android parity incomplete |
| Foundations | Room/DataStore, onboarding, Material 3, reminders, optional opt-in Firebase, accessibility semantics, tests, CI/signing config |
| AAB/native | Density/ABI splits, extractNativeLibs=true and actual binary-manifest verification |
| Benchmarks | Private timing/model-hash/sampled-native-heap export; manual physical-device workflow supplied, not executed |
| Cost/privacy | No paid API/backend or distribution console; local storage/inference and explicit optional computer transfer |

## Release work still required

Run real SD/LTX models on target hardware; evaluate speed, memory, battery,
thermals, cancellation and temporal quality. Test physical encoders, process
termination, interrupted import, low storage and actual TalkBack/VoiceOver.
Add reviewed science/medical content, remaining professional tools and evaluated
parental controls before claiming the complete specification. Preserve the
owner's signing identity and establish a feasible physical iOS release route.

Passing builds cannot establish a “10/10” product or hardware-dependent quality.
