# Study Frog

![StudyFrog artwork](branding/studyfrog-banner.webp)

**Study Frog** is a prompt-first Android creative and study workspace. The GitHub repository slug is `StudyFrog` (GitHub repository URLs cannot contain spaces). Android and iOS display the app name **Study Frog**. Legacy internal build identifiers, iOS targets/directories and artifact filenames retain `StudyForge` to avoid breaking project references, existing builds or saved data. The Android application ID remains `com.scenio.mth`. This is a user-facing branding alignment, not a complete internal path migration.

## Included functionality

Version 0.8.0 adds [portable layered raster project exchange](docs/PORTABLE_PROJECTS.md) between Android and iOS. Native ink projects remain unchanged; stroke/text nodes and mask paths are rasterized only in the exported copy.

Version 0.7.0 adds [local photo depth/body-pose preparation and reviewed OCR](docs/PHOTO_STUDY_WORKFLOW.md), plus [web SD/ControlNet/LTX jobs](web/local-engine/README.md), original-file export and computer-job management. Native Android/iOS applications are preserved. Real-weight quality and physical-device validation remain outstanding.

- Android/iOS parent PIN and study-only workspaces, persisted retry cooldown and adult self-declaration.
- Saved style/detail/realism/scientific-intent prompt directions and preview.
- Computer-local ControlNet Canny/depth/pose and IP-Adapter image guidance with explicit local weights.
- iOS raster layers, reversible masks, move/scale/rotate, document undo and background project snapshots.

- Offline Science Explorer on Android/iOS: ten linked views, body-to-cell and
  mitosis paths, source references, accessible descriptions and prompt handoff.
- Android text-label editing/positioning and persistent study-label layers.
- First-use onboarding and navigation to the creative studio.
- Room-backed progress, transactional updates, and DataStore preferences.
- Material 3 system/light/dark appearance and Android 12+ dynamic color for
  the feature screens and native studio.
- Optional local study reminders with contextual notification permission.
- Separate, default-off analytics and crash-reporting controls. Firebase is
  optional and is not initialized by this app until an explicit opt-in.
- Local schematic templates, photo import, ink annotation, undo/redo, pressure-aware ink, editable project
  saving, search/rename/delete, ZIP backups and aspect-preserving PNG export.
- EXIF-aware photo import, rotation state retention and background draft recovery.
- Authenticated local engine job history, cancellation and result deletion.
- Native CPU SD 1.x GGUF inference on Android/iOS, manual model import,
  256/512px resolution controls, Android foreground jobs and benchmark export.
- Multiple editable ink/raster layers, opacity/blending/visibility, layer erasing,
  shape/text annotations, streamed SVG and backward-compatible schema 4 projects.
- Android imported raster layers, reversible masks, move/scale/rotate, boolean
  shape operations, grids/ruler/perspective guides, symmetry and adjustable smoothing.
- Android/iOS project folders, folder filtering and moving existing projects.
- Phone-local 30-second 1080p still-reveal video encoder implementations.
- Optional computer-local SD 1.x text-to-image/img2img engine and tiled
  refinement, requiring separately downloaded model files.
- Optional computer-local LTX image-to-video integration, with five-segment
  chaining into a 30-second 1080p delivery file; separate FFmpeg still reveal.
- Buildable SwiftUI/PencilKit iOS app, shared native runtime, projects/drafts,
  photo/PNG export, local reminders, appearance and video implementations.
- CMake JNI library, density-only xxhdpi fixture, AAB ABI/density splits and
  scripts that inspect the actual generated binary manifests.
- JUnit5/Turbine, Room, WorkManager and Compose test sources, plus GitHub Actions
  lint/test/build, split verification and opt-in signed-release configuration.

## Build locally

Open the repository in free Android Studio. Install SDK 35, Build Tools 35.0.0,
NDK 27.2.12479018 and CMake 3.22.1. Select JDK 17.

```bash
chmod +x gradlew
./gradlew lintDebug testDebugUnitTest assembleDebug -PfirebaseEnabled=false
```

Install the debug APK on a development device through Android Studio, or:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew connectedDebugAndroidTest -PfirebaseEnabled=false
```

An existing installation with another certificate cannot be updated by a new
key or debug build. Preserve the required signing key and export existing data
before any separately authorized uninstall. No script here uninstalls user apps.

## CI and signing

[The workflow](.github/workflows/android.yml) automatically runs Android lint,
JVM tests, release assembly, AAB packaging verification and local engine tests.
It produces a packaging-test APK set using a temporary test key. That key is
not stable between runs; use your own protected key for durable release updates.

For a stable signed build, create the `android-release` GitHub environment and
set `SF_KEYSTORE_B64`, `SF_STORE_PASSWORD`, `SF_KEY_PASSWORD`, `SF_KEY_ALIAS`.
Run the workflow manually on main with `signedRelease=true`. Local signing and
split-install commands are documented in [the feature guide](docs/android-features/IMPLEMENTATION.md).
The protected signing step does not run on pull requests and does not publish
to a store. No paid developer console is used.

Firebase is off by default. To enable it, register `com.scenio.mth` in a free
Spark project and supply `app/google-services.json` locally or the optional
`GOOGLE_SERVICES_B64` secret. Build with `-PfirebaseEnabled=true`. Do not enable
billing-dependent services. Core features work without this configuration.

## Local engine and privacy

See [engine setup](docs/ENGINE.md). Initial dependency/model downloads require
internet. After preparation, compatible imported GGUF files run on the phone.
Optional larger models run on your own computer. See
[model setup and testing](docs/LOCAL_MODELS.md). Android can connect to the
computer through USB adb-reverse without cloud inference.

Projects and progress stay in private app storage. App backups are disabled.
No storage-wide access is requested. Photos are opened using Android's document
picker. Sending an explicit engine job transfers its input to your local computer.
Optional Firebase diagnostics require consent and can transmit pseudonymous
app/device/crash information; review the in-app disclosure before enabling them.

## Implementation limits

Version 0.8.0 implements these software paths; real model outputs and device
latency/thermals are unverified. CPU image generation offers 256px previews or
512px outputs with conservative RAM guards. Fast mid-range-phone generation
and native full-frame 8K denoising are not promised. Computer tiled refinement
can produce an 8K-sized result; Android retains its original file for export.

LTX motion requires a compatible CUDA computer and complete local weights. It
denoises at 768×448 before scaling to 1080p; model quality, transitions and
ultra-realism remain unevaluated. Still reveal is a separate animation of an image.

Advanced typography/print tools, reviewed medical/scientific content,
independently evaluated family/content safety and complete vector/guide/export parity remain unfinished. There is no
unrestricted Adult Mode or certified child-safe mode. See the audit for blockers.

## Documentation and validation

- [Android packaging and installation](docs/ANDROID_BUILD.md)
- [Android features: complete code and setup reference](docs/android-features/IMPLEMENTATION.md)
- [Family access and parent PIN](docs/FAMILY_ACCESS.md)
- [Prompt controls and local image guidance](docs/GENERATION_CONTROLS.md)
- [Editor, raster layers, masks and folders](docs/EDITOR.md)
- [Science Explorer and content sources](docs/SCIENCE_EXPLORER.md)
- [Complete master-specification coverage](docs/MASTER_SPEC_AUDIT.md)
- [Current feature status](docs/FEATURE_STATUS.md)
- [Validation boundary](VALIDATION.md)
- [Primary sources](docs/android-features/SOURCES.md)

See [VALIDATION.md](VALIDATION.md) for exact CI evidence and the distinction
between automated tests and real-model/device evaluation. The manual physical
workflow requires a connected phone and imported weights; normal CI skips it.

Source is Apache-2.0. Third-party tools and model weights retain their own licenses.
There are no paid APIs, ads, mandatory subscriptions or paid distribution tools.
Hosted CI quotas and external free-service terms remain provider-controlled.

## Audit and branding

See [the professional audit](docs/AUDIT.md) for corrected defects, validation
and remaining release blockers. Your supplied artwork is included unchanged in
`branding/`, with launcher-safe crops and Android/iOS assets. The repository and
artwork use StudyFrog; the existing product display name remains StudyForge.
