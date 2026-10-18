# Validation — 2026-10-18 (Asia/Dubai), version 0.6.0

Application/test source: `52abc98916e9edd48499fe447e047348af35feff`.
Android/engine verified source: `43ac0b3e1053f481a6075bf55a23ebd9b61c6755`.
The 120 Android/engine input files are identical between those trees. Android
verification job definitions are unchanged; only push trigger paths were scoped.
The later changes are iOS-only. Final documentation/integrity publication changes
no application/test code.

## Passing workflows

| Workflow | Verified result |
| --- | --- |
| [Android 39121050440](https://github.com/Tajoor621/StudyFrog/actions/runs/39121050440) | Lint, JVM tests, test-APK compilation, release APK/AAB assembly, generated split inspection, repository guard, Python checks and API 35 emulator suite pass |
| [iOS 39121520515](https://github.com/Tajoor621/StudyFrog/actions/runs/39121520515) | Shared CPU framework/app compile and all 12 XCTest tests pass |

**80 automated tests passed; one opt-in real-model test was skipped.**
Counts: 12 JVM, 24 Android instrumentation, 24 engine, four manifest-parser,
four study-content and 12 iOS tests. Android starts 25 instrumentation cases and
reports one real-model skip/zero failures. The protected signed-release job is
intentionally skipped without owner secrets and explicit dispatch. Configured
Firebase-enabled builds remain unverified.

Android pins JDK 17, SDK 35, NDK 27.2.12479018, CMake 3.22.1, Gradle 8.9 and
AGP 8.7.3, with arm64-v8a/x86_64 inference. iOS uses Xcode/macOS, XcodeGen 2.42.0
and the same pinned native runtime. Upstream C++ and native-packaging advisory
warnings remain visible; lint errors are not suppressed.

## Evidence covered

- Actual bundletool manifests contain density split config.xxhdpi,
  splitTypes=base__density and hasCode=false; the base contains
  extractNativeLibs=true. Native libraries are packaged and the density split has
  no DEX. Source manifests do not hardcode generated split/Play metadata.
- JVM tests cover geometry, progress/preferences, cancellation cleanup,
  six-digit PIN/digest behavior and visual-direction bounds/prompt preservation.
- Android tests cover onboarding/recreation/font scaling, Room, reminders, JNI
  bounds, linked science views, layered rendering/legacy reads, raster ZIP
  persistence, full-action undo, transforms, masks, symmetry, boolean shapes,
  SVG/multiply and atomic metadata recovery.
- New Android policy tests use isolated private files to check PIN changes,
  failed-attempt cooldown, persistence and corrupt-record fail-closed behavior.
  They do not establish physical bypass resistance.
- iOS tests cover project/settings/folder persistence, legacy records, native
  invalid-input rejection, study renders/context, PIN vault/cooldown and prompt
  controls. Vault tests use isolated memory storage; actual Keychain/device
  entitlement behavior still needs physical evaluation.
- Four iOS layer tests exercise actual Core Image mask hide/restore/disable,
  immutable photo pixels, transforms/multiply/invalid bounds, raster/mask/settings
  persistence, stale folder/title updates, legacy defaults and document undo.
- Engine tests cover auth, validation, queue/history/cancellation, missing-model
  failure, tiling, image/video memory handoff and fixture video mechanics.
  Six guidance tests cover actual CPU Canny, prepared maps, missing weights,
  local-only loader arguments, cache replacement/reuse and strength propagation.
  Loader/model execution uses stubs, not real weights.
- Actual FFmpeg/ffprobe fixture output verifies 720 frames, 24fps, 1920×1080 and
  30 seconds. No real LTX denoising or temporal-quality evaluation is exercised.
- Engine CI passed 357 structural repository assertions at its source snapshot.
  Final file inventory, changed-source digests, unchanged blob identities and
  documentation links are checked against the published repository tree.
  Eight parser/content tests and all six guidance tests also passed locally
  before publication, including the real OpenCV edge-map check.

Artifacts contain Android lint/test reports, AAB/packaging-test APK set and the
iOS simulator app/results. Packaging-test certificates are disposable. They do
not replace the owner's update key; simulator output is not a signed iPhone
release. Push CI now filters platform inputs to avoid redundant native builds;
pull-request and manual checks remain full.

## Boundaries and remaining work

No real SD/LTX/guidance weights, CUDA GPU or connected ARM64/iPhone device were
available. Real output quality/adherence, scientific correctness, 8K seams,
motion continuity, RAM/VRAM, latency, battery/thermals, physical encoders, stylus
feel and physical 16 KB-page-size behavior remain unverified. The opt-in physical
workflow was not dispatched.

Native inference remains 256/512px. LTX denoises at 768×448 before scaling to
1080p; frame/container tests do not establish ultra-realism or native 1080p
detail. Depth/pose guidance consumes prepared maps, not automatic detections.
PIN controls do not certify child safety or OS-level restrictions; content and
bypass review, accessibility, process-death/low-memory/storage behavior and
actual signing/Keychain evaluation remain outstanding.

iOS now has core raster/mask/transform/document-undo parity. iOS ports of Android's vector,
SVG/text/boolean and guide tools, cross-platform editable interchange, advanced
typography/print workflows, broad reviewed medical atlas/validated imaging,
OCR/factual verification and consistent semantic zoom are still incomplete.

See [FAMILY_ACCESS.md](docs/FAMILY_ACCESS.md),
[GENERATION_CONTROLS.md](docs/GENERATION_CONTROLS.md),
[EDITOR.md](docs/EDITOR.md), [FEATURE_STATUS.md](docs/FEATURE_STATUS.md),
[MASTER_SPEC_AUDIT.md](docs/MASTER_SPEC_AUDIT.md) and
[LOCAL_MODELS.md](docs/LOCAL_MODELS.md). No complete-master-specification or
“10/10” claim is made.

No production signing key, real token, Firebase configuration, user project or
model weight is committed. Preserve original signing identity and Room history.
The previous 0.5.0 baseline recorded 61 passing tests; this delivery adds 19.
