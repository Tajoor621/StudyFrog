# Validation — 2026-10-17, version 0.4.0

Tested source: `a988e9960a8dbf6cbe02bf7c39d03dc67a0e3051`.
The following documentation-only commit does not change application code.

## Passing CI

| Workflow | Verified result |
| --- | --- |
| [Android 39054355888](https://github.com/Tajoor621/StudyFrog/actions/runs/39054355888) | Lint, seven JVM tests, release APK/AAB assembly, generated split verification, Python checks and API 35 emulator tests pass |
| [iOS 39054355851](https://github.com/Tajoor621/StudyFrog/actions/runs/39054355851) | Shared native CPU framework and SwiftUI app compile; two XCTest tests pass |

**39 automated tests passed; one opt-in real-model test was skipped.**
Counts: seven JVM, eleven Android instrumentation, fifteen engine, four
manifest-parser and two iOS tests. The skipped test requires a physical phone
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
  recreation, layer persistence/erasing/legacy reads and JNI bounds checks.
- iOS tests exercise editable project reopening/rename/delete and invalid
  inference-input rejection. They do not run a real model.
- Python tests cover authentication, limits, job persistence/cancellation,
  missing-model failure, tiling mechanics and motion segment planning. Motion
  tests use fixture frames; actual FFmpeg/ffprobe verifies 720 frames, 24fps,
  1920×1080 and 30 seconds. No real LTX denoising is exercised.
- Repository integrity, XML, backup exclusions and whitespace checks pass.
  Fifteen engine tests also passed locally during implementation.

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

No production signing keys, Firebase configuration, real bearer tokens, user
projects or weights are committed. Preserve the existing app's signing key and
Room schema history before releases and database migrations.
