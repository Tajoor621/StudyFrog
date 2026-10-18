# Validation — 2026-10-18 (Asia/Dubai), version 0.4.1

Tested source: `8c2f2e401193992d24b427d4bf2a64b5e090e93e`.
The audit documentation and integrity-manifest follow-up does not change the
application or test code verified below. CI timestamps are UTC; the validation
date above uses Asia/Dubai.

## Passing CI

| Workflow | Verified result |
| --- | --- |
| [Android 39065556114](https://github.com/Tajoor621/StudyFrog/actions/runs/39065556114) | Lint, eight JVM tests, instrumentation APK compilation, release APK/AAB assembly, generated split verification, repository guard, Python checks and API 35 emulator tests pass |
| [iOS 39065556067](https://github.com/Tajoor621/StudyFrog/actions/runs/39065556067) | Shared native CPU framework and SwiftUI app compile; four XCTest tests pass |

**53 automated tests passed; one opt-in real-model test was skipped.**
Counts: eight JVM, fifteen Android instrumentation, eighteen engine, four
manifest-parser, four study-content and four iOS tests. The skipped test requires a physical phone
and an imported compatible model. The protected signed-release job was also
intentionally skipped because it requires owner signing secrets and explicit
workflow dispatch. Optional Firebase-enabled builds remain unverified.

Android uses JDK 17, SDK 35, NDK 27.2.12479018, CMake 3.22.1, Gradle 8.9 and
AGP 8.7.3. It builds native CPU inference for arm64-v8a and x86_64. iOS builds
with macOS/Xcode, XcodeGen 2.42.0 and the same pinned inference sources.
Upstream C++ deprecation/override warnings remain visible in logs. AGP warns
that native extraction is normally controlled through packaging DSL; this app
also retains the explicit source attribute required by its packaging specification.

## What passed

- Generated binary manifests contain `split="config.xxhdpi"`,
  `splitTypes="base__density"`, and `android:hasCode=false` in the density split;
  base application contains `android:extractNativeLibs=true`.
- Native libraries are packaged; the density split has no DEX. Source manifests
  do not hardcode generated split attributes or Play-derived APK metadata.
- JVM tests exercise geometry and reactive data/preferences. Android tests
  cover onboarding recreation, double font scaling, Room updates/reopening,
  disabled reminders, editable ZIP round trips/traversal rejection, studio
  recreation, layer persistence/erasing/legacy reads, JNI bounds checks, every bundled study
  view, editable study labels, explorer navigation/recreation and SVG
  opacity/pressure/escaped-label serialization.
- iOS tests exercise editable project reopening/rename/delete, invalid
  inference-input rejection, hierarchy-context prompts and all ten study
  diagram renders. They do not run a real model.
- Python tests cover authentication, limits, job persistence/cancellation,
  missing-model failure, tiling mechanics, motion segment planning and
  image/video model-memory handoff. Lifecycle tests use model/CUDA stubs;
  they do not establish real VRAM behavior. Motion
  tests use fixture frames; actual FFmpeg/ffprobe verifies 720 frames, 24fps,
  1920×1080 and 30 seconds. No real LTX denoising is exercised.
- Repository integrity, XML, backup exclusions and whitespace checks pass.
  Eighteen engine tests and eight parser/content tests also passed locally.
  The repository guard checks merge markers, manifest class resolution, JNI
  symbols, version/toolchain consistency, icon sizes, secret-file exclusions
  and local documentation links.

CI artifacts include test reports, AAB/test APK set and iOS simulator app/results.
Packaging-test APKs use a disposable certificate, not a durable release identity.
The simulator app is not an installable signed iPhone release.

## Hardware/model checks still required

No real SD/LTX weights, compatible CUDA GPU or connected ARM64/iPhone device
were available for this implementation. Native output quality, scientific
accuracy, real LTX motion/segment continuity, real 8K refinement, latency,
thermals/battery, hardware encoders and physical 16 KB-page-size compatibility
remain unverified. A compiled runtime is not a model benchmark.

Native images are 256/512px. LTX denoises at 768×448 before scaling to 1080p;
container/frame tests cannot establish ultra-realism or native 1080p model detail.
The supplied manual physical-device workflow was not dispatched. See
[LOCAL_MODELS.md](docs/LOCAL_MODELS.md) for model import, benchmark export and
real-device commands.

Simulator tests do not certify TalkBack/VoiceOver, contrast, every OS version,
process death, interrupted import, storage exhaustion or low-memory recovery.
Android schema 3 supports legacy projects; iOS uses its own editable format,
not cross-platform ZIP interchange. Full original-specification delivery is
not claimed; see [FEATURE_STATUS.md](docs/FEATURE_STATUS.md).

## Audit-fix regression evidence

The JVM cancellation test verifies that native-like non-cooperative work is
joined before file cleanup can run. The SVG instrumentation test verifies color
opacity, pressure-scaled dots/segments and escaped label text. Three engine
regressions verify cached image-pipeline release before motion loading, cleanup
after success/failure, and worker survival when CUDA cache cleanup fails. These
are the five additions to the prior 48-test baseline. The worker benchmark now
records imported model SHA-256, or an explicit unknown marker.

Full requirements and remaining implementation gaps are recorded in
[MASTER_SPEC_AUDIT.md](docs/MASTER_SPEC_AUDIT.md); the study catalog is documented
in [SCIENCE_EXPLORER.md](docs/SCIENCE_EXPLORER.md). No 10/10 or complete-master-
specification claim is made.

No production signing keys, Firebase configuration, real bearer tokens, user
projects or weights are committed. Preserve the existing app's signing key and
Room schema history before releases and database migrations.
