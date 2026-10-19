# Master specification coverage — 2026-10-19 (Asia/Dubai)

The requested product is **not fully implemented**. Study Frog 0.8.0 has working
Android/iOS builds and a substantial offline foundation. A passing build is not
proof of model quality, medical accuracy, device performance or complete scope.
The tested source and exact results are recorded in [VALIDATION.md](../VALIDATION.md).

## Product requirements

| Requested capability | Current evidence and gap |
| --- | --- |
| Prompt-first generation | Android prompt/negative/seed controls and iOS prompt-first studio implemented |
| Text-to-image | Native CPU SD 1.x GGUF adapter on both platforms; optional computer SD 1.x adapter; real weights not evaluated |
| Photo-to-image | Native and computer img2img adapters; photos are bounded/downsampled for mobile memory; semantic accuracy unverified |
| Generate from ideas | Text prompts reach actual model adapters; correctness/adherence require model testing |
| Style/detail/realism sliders | Both platforms save style/detail/realism and scientific-intent prompt directions with preview/bounds; iOS exposes steps/photo strength; these do not establish actual factual accuracy |
| Negative prompts | Passed to image/video adapters; actual adherence unverified |
| Whole → system → organ → tissue → cell | Five linked conceptual views and context-preserving prompt handoff; no broad, model-consistent semantic atlas |
| Science diagrams and labels | Ten original referenced concept views, including five mitosis phases; no independent scientific peer review |
| Convert arbitrary notes/photos into accurately labeled study materials | Local Tesseract transcription, review/correction and explicit bounded prompt handoff on all clients; automatic label layout and factual validation incomplete |
| ControlNet, IP-Adapter, OpenPose, depth maps | Computer-local Canny/depth/pose ControlNet and IP-Adapter paths and both-platform clients; prepared maps remain supported; photo modes add local CPU relative depth and RT-DETR/ViTPose body pose adapters; native-phone guidance and real-weight evaluation remain incomplete |
| 8K still output | Optional computer tiled refinement can allocate 7680×4320; real-model quality/seams unverified; native phone inference limited to 256/512px |
| Fast mid-range-phone generation | Not established; CPU runtime, RAM guards and benchmark export exist; phone thermal/battery/latency tests pending |
| Layers/masks/blending | Android/iOS base plus up to 16 ink/raster layers, reversible hide/restore masks, transforms, opacity/visibility, normal/multiply/screen and full-action undo; resource limits documented in EDITOR.md; portable layered-raster exchange added; advanced vector/guide/export parity and lossless stroke interchange incomplete |
| Brush engine | Android pressure/optional tilt width and adjustable smoothing; professional textured brush presets and physical stylus validation incomplete |
| Vector/raster hybrid | Android raster layers, editable ink/geometry/text, union/intersection/subtract/XOR of closed shapes and streamed SVG; complete vector-node editing incomplete |
| Perspective, symmetry, rulers and grids | Android bounded grids, percentage ruler, one-point perspective/ray tool and mirrored drawing implemented; multi-point and calibrated professional rulers incomplete |
| Typography/open fonts/print tools | Android sans-serif/serif/monospace labels and PNG/SVG export; custom font management, advanced typography and print/color workflows incomplete |
| Image-to-video, 30 seconds | Phone still-reveal implementations and optional computer LTX motion adapter; real-model inference and phone encoders unverified |
| Subject movement/transformation/camera prompts | Prompt passed to local LTX; real temporal quality and instruction adherence unverified |
| Cinematic creation/drawing process | Still reveal is implemented; it does not infer or reconstruct an artist's actual brush sequence |
| Video 1080p, 24–30fps | 1080p/24fps container mechanics tested; LTX denoises 768×448 before scaling; no native 1080p detail or ultra-realism guarantee |
| Medical layers: skin/muscle/bone/organs/vessels/nerves | Full multi-layer atlas not implemented |
| X-ray/CT/MRI overlays | No validated imaging/overlay system or diagnostic functionality |
| Six named modes | Mode choices exist; many actions share pipelines; complete dedicated mode experiences incomplete |
| Projects/folders/prompt history | Local editable projects, folders/filter/search/move on Android/iOS, Android schema 4 raster ZIP backups, draft recovery and computer job history; portable layered-raster Android/iOS exchange added; lossless native stroke exchange incomplete |
| Adult Mode/age gate/parental PIN | Android/iOS parent PIN, persisted retry cooldown, study-only compartment and adult self-declaration implemented; no unrestricted explicit-sexual mode or independent parental-control evaluation |
| Completely safe child mode | Not certified; restricted bundled content hides general inputs/private data/browser links and still requires teacher review |
| Medical content available in safe mode | Bundled anatomy stays available in PIN-protected study-only mode; general generation stays in the adult creative workspace |
| Offline/private by default | Private local storage, manual model import, no required backend; optional computer transfer and optional Android telemetry are explicit |
| Free workflow | No paid API, subscription, ads or required paid distribution service added; models/hardware must still be supplied |
| Android/iOS parity | Shared native runtime and study catalog plus substantial iOS editor/project functionality; raster/mask/transform/document-undo parity added; advanced vector/guide/export parity and physical iOS release incomplete |

## Requested Android foundations

| Feature | Delivery and validation boundary |
| --- | --- |
| Room + DataStore | Integrated local progress/preferences; transactional progress and persistence tests |
| Compose onboarding + Navigation | Integrated; recreation test |
| Material 3 dark/dynamic color | Feature screens/native studio integrated; science illustrations use a light paper canvas; full visual/accessibility review pending |
| Settings | DataStore-backed appearance/reminders/opt-in telemetry |
| WorkManager reminders | Local opt-in reminders; notification permission checks; disabled-reminder test |
| Firebase crash/analytics | Optional build flag and configuration, default-off consent controls; owner-configured enabled build unverified |
| Accessibility | Labels, descriptions, font scaling regression coverage; real TalkBack/VoiceOver and full contrast/interaction audit pending |
| JUnit5/Turbine/Compose UI tests | Integrated; native Espresso UI test dependency explicitly pinned |
| GitHub Actions | Lint/JVM/test-APK compilation/release assembly, engine tests, split verification and API 35 instrumentation; iOS simulator workflow |
| Signed release | Protected environment/secret configuration; durable signed release requires original owner key; normal CI uses disposable packaging keys |

## Android packaging contract

| Requirement | Evidence |
| --- | --- |
| `com.scenio.mth`, Kotlin, Gradle Kotlin DSL, AGP 8+, JDK 17 | Present and compiled in CI |
| minSdk 24 / targetSdk 35 / NDK | Present; CI/app toolchain pins checked |
| Density-only xxhdpi resource | `res/drawable-xxhdpi/ic_xxhdpi.png`, used by MainActivity ImageView |
| `config.xxhdpi` and `splitTypes="base__density"` | Verified from bundletool-generated binary manifests |
| Density split `android:hasCode=false` | Verified; split has no DEX |
| Base `extractNativeLibs=true` and native JNI result | Manifest/legacy native packaging and JNI load checks pass |
| CMake/C++ native library | Compiles for arm64-v8a and x86_64; shared image runtime integrated |
| No hardcoded Play-derived metadata or source split declarations | Source manifest guard enforced |
| AAB/APK generation, inspection and install commands | [ANDROID_BUILD.md](ANDROID_BUILD.md), [XXHDPI.md](XXHDPI.md), scripts and archived integration guide |
| App icon | Original supplied artwork retained across Android/iOS sizes |
| Local/free distribution | AAB/APK scripts and artifact workflow; no Play Console publication |

## Integration audit results and limits

The repository audit checks source merge markers, duplicate top-level Kotlin
identities, own manifest component resolution, launcher identity, JNI symbols,
Kotlin/Compose/KSP/Room pins, Android/iOS versions, ABI/native packaging settings,
CI NDK alignment, icon dimensions and local documentation links. It also rejects
tracked private-key payloads and signing/configuration secret files.

Run `python3 scripts/audit_repository.py`. It is also part of the engine CI job.
Its count includes individual structural assertions, not additional model or UI
tests. It does not prove that every branch in every file is conflict-free.

The 0.7.0 [photo/OCR workflow](PHOTO_STUDY_WORKFLOW.md) and [web engine guide](../web/local-engine/README.md) add local preprocessing and usable web image/video jobs.

The 0.6.0 family/generation additions are described in [FAMILY_ACCESS.md](FAMILY_ACCESS.md) and [GENERATION_CONTROLS.md](GENERATION_CONTROLS.md).

The 0.5.0 editor/folder additions are described in [EDITOR.md](EDITOR.md).

This audit corrected cancellation cleanup ordering, image/video model memory
handoff, benchmark model-hash reporting, and SVG opacity/pressure consistency.
The earlier interrupted Science Explorer documentation is now included in the
publication set. See [AUDIT.md](AUDIT.md) for findings and [SCIENCE_EXPLORER.md](SCIENCE_EXPLORER.md)
for content scope and provenance.

## Release priorities

1. Test real SD/LTX weights on supported phones/CUDA hardware, record model
   licenses and measured quality, timing, memory, battery and thermal evidence.
2. Exercise physical encoders, cancellation, process termination, low storage,
   16 KB-page-size devices and real accessibility interactions.
3. Complete the professional editor, reviewed science/medical content and
   independently evaluated family/content controls before advertising the full master specification.
4. Establish owner-signed Android updates, physical iOS testing/distribution and
   the optional configured telemetry build without exposing signing secrets.

A “10/10” rating or claim of all requested features would be unsupported today.

Version 0.8.0 exchange scope and limits: [Portable projects](PORTABLE_PROJECTS.md).
