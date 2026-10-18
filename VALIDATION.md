# Validation — 2026-10-18 (UTC), version 0.5.0

Tested source: `442ab6256e8a3dda04229f5e226ad91b9bbd62f4`.
The final documentation/tree/checksum follow-up does not change application or
test code. A passing build does not establish complete master-spec delivery.

## Passing CI

| Workflow | Verified result |
| --- | --- |
| [Android 39116104866](https://github.com/Tajoor621/StudyFrog/actions/runs/39116104866) | Lint, eight JVM tests, instrumentation APK compilation, release APK/AAB assembly, generated split verification, repository guard, Python checks and API 35 emulator tests pass |
| [iOS 39116104838](https://github.com/Tajoor621/StudyFrog/actions/runs/39116104838) | Shared native CPU framework and SwiftUI app compile; five XCTest tests pass |

**61 automated tests passed; one opt-in real-model test was skipped.**
Counts: eight JVM, twenty-two Android instrumentation, eighteen engine, four
manifest-parser, four study-content and five iOS tests. The skipped test needs a
physical phone and imported compatible weights. The protected signed-release
job is intentionally skipped without owner signing secrets and explicit dispatch.
Optional configured Firebase builds remain unverified. Android/JVM counts were
checked against the downloaded JUnit XML: 23 instrumentation cases with one
skipped and no failures/errors, plus eight JVM cases with no skips/failures.

Android uses JDK 17, SDK 35, NDK 27.2.12479018, CMake 3.22.1, Gradle 8.9 and
AGP 8.7.3, building arm64-v8a and x86_64 native CPU inference. iOS uses macOS/Xcode,
XcodeGen 2.42.0 and the same pinned native runtime. Upstream C++ warnings and
AGP's extractNativeLibs packaging advisory remain visible, without disabling lint.

## What passed

- Actual bundletool binary manifests contain `split="config.xxhdpi"`,
  `splitTypes="base__density"`, density `android:hasCode=false`, and base
  `android:extractNativeLibs=true`. Native libraries are packaged; the density
  split has no DEX. Source manifests do not hardcode Play-derived or split data.
- JVM tests cover geometry, reactive progress/preferences and cancellation that
  joins native-like non-cooperative work before cleanup.
- Android tests cover onboarding/recreation, double font scaling, Room
  persistence, disabled reminders, JNI input bounds, studio recreation, linked
  study navigation/content/labels and legacy documents.
- Editor regressions cover reversible raster masks, transforms, guide exclusion
  from rendered exports, SVG streaming, native and legacy multiply alpha,
  full-action undo/redo, boolean shapes, mirrored gestures, and malformed raster
  references/transforms. Legacy multiply is called directly in the API 35 test;
  this is not an API 24–28 device UI run.
- ZIP round trips retain raster pixels, transforms, mask state and folder names;
  traversal is rejected. Metadata recovery tests restore a legacy AtomicFile
  backup before listing and updating the folder.
- iOS tests cover project reopening/rename/delete, folder persistence and invalid
  names, old records with no folder field, invalid model input, hierarchy prompts
  and all ten conceptual diagram renders.
- Engine tests cover authentication, limits, job persistence/cancellation,
  missing-model failure, tiling mechanics, motion segment planning and image/video
  model-memory handoff. Model/CUDA lifecycle checks use stubs. Actual FFmpeg and
  ffprobe check fixture output at 720 frames, 24fps, 1920×1080 and 30 seconds;
  no real LTX denoising is exercised.
- Repository integrity, XML, backup privacy exclusions and whitespace checks pass.
  The repository guard checks merge markers, manifest/JNI identities, versions,
  toolchain/ABI pins, icon sizes, secret-file exclusions and documentation links.
  Eight parser/content tests also pass locally.

CI artifacts include Android test/lint reports, AAB and packaging-test APK set,
and the iOS simulator app/results. Packaging-test keys are disposable; they are
not the owner's durable update identity. A simulator app is not a signed iPhone
release. No production keys, configuration secrets, bearer tokens or model
weights are committed.

## Hardware and product boundaries

No real SD/LTX weights, compatible CUDA GPU or connected ARM64/iPhone device
were available. Output quality, prompt adherence, scientific accuracy, real
motion/segment continuity, real 8K refinement, latency, battery/thermals, physical
encoders, real stylus behavior and physical 16 KB-page-size compatibility remain
unverified. The manual real-device workflow was not dispatched.

Native inference offers 256/512px. LTX denoises at 768×448 before scaling to
1080p; container/frame tests do not prove ultra-realism or native 1080p detail.
See [LOCAL_MODELS.md](docs/LOCAL_MODELS.md) for model and benchmark steps.

Automated checks do not certify TalkBack/VoiceOver, contrast, every OS version,
process death, interrupted imports, low-memory/storage behavior or child safety.
Android schema 4 reads legacy projects and backs up raster assets; iOS uses its
own PencilKit format. Advanced Android editor tools are not fully ported to iOS.
Owner-signed release updates and physical iOS distribution need owner setup.

See [EDITOR.md](docs/EDITOR.md) for editor bounds, [FEATURE_STATUS.md](docs/FEATURE_STATUS.md)
and [MASTER_SPEC_AUDIT.md](docs/MASTER_SPEC_AUDIT.md) for all remaining scope, and
[SCIENCE_EXPLORER.md](docs/SCIENCE_EXPLORER.md) for conceptual content provenance.
No complete-master-specification or “10/10” claim is made.

Previous verified 0.4.1 source: `8c2f2e401193992d24b427d4bf2a64b5e090e93e`,
[Android 39065556114](https://github.com/Tajoor621/StudyFrog/actions/runs/39065556114)
and [iOS 39065556067](https://github.com/Tajoor621/StudyFrog/actions/runs/39065556067).
The 0.5.0 changes add seven Android tests and one iOS test to that 53-test baseline.
