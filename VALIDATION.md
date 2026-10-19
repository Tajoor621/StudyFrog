# Validation — 0.8.0 portable project work

Portable layered raster import/export implementation and native tests have been
added. Local checks: ten script/content tests, repository structural audit and
`git diff --check` pass. New Android/iOS native build and runtime results will be
recorded after CI completes. The previous 96-test result below describes 0.7.0;
it must not be treated as verification of the new native codecs or screens.

## Previous verified release

### Version 0.7.0 — 2026-10-19 (Asia/Dubai)

Production application/engine/web source: `637c5fe24bafbc3654ca8b66cd0dda86724c7136`.
Android UI-test/CI follow-up: `0cb8b9bf5928ec7439dd1fb236ed12be212cc50f`.
iOS verified source: `6a6bebe6b25a8ee504f22484b871824a2bdc51dd`.
The seven files changed after the iOS build are engine/web files; all iOS/shared
native/build inputs are identical. The entire 211-file source tree was compared
with the local tested Git tree and matched `41199bd9957397bf2b4e8a0c0723e5f563933f0a`.
After this source snapshot, only the Science Explorer instrumentation test and
Android test-only CI routing changed (tree `7cc77576480f08e0cfd99c0d961c4009b39eba55`,
212 files). Production application, engine, web, iOS and shared runtime inputs
are unchanged. Final documentation/inventory publication adds the photo guide
and does not modify application or test code.
A transfer-corrupted intermediate commit was repaired and every repaired file
was read back and verified before this source comparison. No claim is based on
that intermediate commit's checks.

## Workflow results

| Workflow | Result |
| --- | --- |
| [Android 39243506413](https://github.com/Tajoor621/StudyFrog/actions/runs/39243506413) | Release/lint/JVM/engine/browser/split checks passed; initial emulator attempt found one window-focus test failure |
| [Android UI 39247095254](https://github.com/Tajoor621/StudyFrog/actions/runs/39247095254) | Complete 25-case API 35 suite: 24 passed, one opt-in model test skipped, zero failures/errors |
| [iOS 39242551700](https://github.com/Tajoor621/StudyFrog/actions/runs/39242551700) | Shared native framework/app compile, all 12 XCTest tests pass |
| [Pages 39247094971](https://github.com/Tajoor621/StudyFrog/actions/runs/39247094971) | Build/deploy succeeded |

**96 automated tests passed; one opt-in real-model test was skipped.**
Browser integration and service-worker privacy check scripts also passed.

Verified test groups: 12 JVM; 24 Android instrumentation with one opt-in
real-model case skipped; 35 engine; eight manifest/content; 12 iOS; five bridge
HTTP tests. Service-worker privacy and browser integration are two additional
check scripts, not extra individual unit-test counts. Owner-signed release and
configured Firebase builds are not verified by the default workflow.

The first emulator attempt reported 23 passes, one focus failure and one model
skip. Downloaded XML/logcat located the failure on the first Science Explorer
interaction, before recreation. The follow-up initializes UI automation, wakes
and dismisses the test-device keyguard, and targets the current scenario decor
on every interaction, including after recreation. All assertions remain intact;
no retry or exclusion masks the failure. Test-only pushes now run the full
emulator suite separately; production-source, PR and manual release checks keep
the existing lint/JVM/release/engine/device pipeline.

## New evidence

- Actual Tesseract 5 recognizes rendered CELL WALL / DNA AND PROTEIN fixture
  text. Tests exercise TSV line bounds/confidence, invalid language rejection,
  missing-tool errors, concurrency limits, subprocess timeout/temporary-file
  cleanup, authenticated HTTP success, unauthorized/busy/missing-tool responses,
  and absence of saved OCR jobs. This is not a handwriting/medical accuracy study.
- Local CPU estimator adapters are exercised with explicit model stubs: local-only,
  no-remote-code and safetensors arguments; relative-depth normalization and
  invalid values; COCO body-to-OpenPose skeleton/confidence; prepared-map and
  photo-mode routing. No pretrained depth, RT-DETR, ViTPose, SD or LTX weights
  were evaluated in this environment.
- Real OpenCV Canny edges and existing engine job lifecycle/cancellation,
  model handoff, tiled rendering and FFmpeg container-mechanics tests pass.
- Bridge tests start real loopback HTTP servers, checking static-file isolation,
  exact Host/Origin/Tailscale rules, forwarding of the computer-held token,
  fixed routes, no-store results and redirect rejection.
- Chromium browser integration uses explicit engine fixtures. It checks connection,
  style/effective-prompt preview and request payload, image download/preview/Undo,
  review-before-OCR-prompt replacement and length limits, video/guidance validation,
  negative-conditioning rejection for Ollama and computer job deletion. It does
  not establish physical Samsung-to-computer networking or real model generation.
- The service-worker script verifies static-only caching, bypass of media/job/OCR
  requests and cleanup scoped to this application's caches.
- New Android/iOS OCR controls compile in the platform builds. Native OCR dialogs,
  network permission interactions and real handwriting still need physical testing.
- Downloaded JVM XML reports independently confirm 12 tests with zero failures,
  errors or skips. Downloaded final emulator XML confirms 25 cases, zero failures/
  errors and one opt-in model skip, including the corrected Science Explorer test.
  Generated binary manifests report config.xxhdpi, base__density,
  hasCode=false and extractNativeLibs=true; the density split contains no DEX.
- Repository guards check package/component/JNI resolution, version/toolchain
  agreement, native/density packaging, no source hardcoded split/Play metadata,
  icons, local documentation links and absence of tracked signing/config secrets.
  Structural assertion counts are not additional functional tests.

## Existing coverage retained

Room/DataStore persistence, onboarding/recreation/font scaling, reminders,
JNI input bounds, science content/hierarchy, PIN digest/cooldown/corruption,
project/layer/raster/mask/transform/undo persistence, SVG and metadata recovery
remain covered by the platform suites. iOS PIN storage tests use memory fixtures,
not a physical Keychain validation. Real TalkBack/VoiceOver, 16 KB runtime devices,
physical encoders, low-memory/process-death/storage behavior and owner signing
remain outside this result.

Android pins JDK 17, SDK 35, AGP 8.7.3, Gradle 8.9, NDK 27.2.12479018 and CMake
3.22.1. iOS uses the shared pinned CPU runtime and XcodeGen 2.42.0. Native builds
retain visible upstream advisory warnings; no lint errors are suppressed.

The existing FFmpeg fixture checks a 30-second, 720-frame, 24fps, 1920×1080
container. LTX denoising remains at 768×448 before scaling; container dimensions
are not proof of native detail, temporal coherence or ultra-realism. Computer
8K tiled output allocation is not a real-model quality/seam benchmark. Native
phone image inference remains bounded to 256/512px.

## Repeat checks

```bash
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r engine/requirements-test.txt
python scripts/check_repository.py
python scripts/audit_repository.py
python -m unittest discover -s scripts -v
(cd engine && python -m unittest -v)
python -m unittest discover -s web/tests -v
node --check web/app.js
node --check web/local-engine/ui.js
node web/tests/service-worker.test.cjs
./gradlew --no-daemon lintDebug testDebugUnitTest assembleDebugAndroidTest assembleRelease bundleRelease -PfirebaseEnabled=false
./gradlew --no-daemon connectedDebugAndroidTest -PfirebaseEnabled=false
```

Install free FFmpeg/Tesseract/English data and DejaVu fonts for all integration
cases. Browser CI installs pinned Playwright 1.58.2 and uses runner Chrome. See
[photo/OCR setup](docs/PHOTO_STUDY_WORKFLOW.md), [web setup](web/local-engine/README.md),
[Android packaging](docs/ANDROID_BUILD.md), and [coverage gaps](docs/MASTER_SPEC_AUDIT.md).
The local workflow remains available independent of provider-controlled CI quotas.
