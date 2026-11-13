# Study Frog

![Study Frog mascot, a frog in graduation attire with the words Learn, Explore, Excel](branding/studyfrog-banner.webp)

**Study Frog** is an offline-first creative study workspace. You can draw and label study graphics, keep projects on the device, and explore referenced science schematics. Optional image, video, and text-reading tools run on a phone, or on a computer you control. The web edition does not contain an AI image model, and GitHub Pages does not run one.

The live web edition is <https://tajoor621.github.io/StudyFrog/>.

The GitHub repository is named `StudyFrog` because repository URLs cannot contain spaces. Android and iOS show the name **Study Frog**. The supplied artwork also carries the **AiSi Study** mark. Legacy internal build identifiers, the iOS target and directories, and some older in-app strings still use `StudyForge` so existing builds and saved data keep working. The Android application ID remains `com.scenio.mth`.

## Web edition

The site is a static, installable workspace. After the first successful load it works offline.

- Import a PNG, JPEG, or WebP up to 12 MB, place labels, and draw with pen, line, rectangle, ellipse, or eraser.
- Undo, clear, and export the canvas as a real PNG.
- Save up to 12 projects in the browser. Search, rename, duplicate, delete, and download or import a JSON backup.
- Open the same ten offline science schematics as the Android and iOS apps, read their descriptions and OpenStax sources, send a view to the prompt, or place the schematic on the canvas.
- Switch among system, light, and dark appearance.
- Use optional local generation only through the [computer bridge](web/local-engine/README.md). That path is separate from the static site.

[Media Learning](docs/MEDIA_LEARNING.md) optionally converts photos and lectures into searchable local study notes, citations, and review questions. It runs through the computer bridge without a paid API or mandatory model.

Scope and limits: [web edition notes](web/README.md).

## Creative Studio

The optional [Creative Studio](studio-next/README.md) lives in `studio-next/`. It includes local FLUX image creation, image-to-video handoff, a configurable video provider waterfall, browser clip stitching, and optional Supabase Community sharing. The Android, iOS, static web and Python workflows keep their existing entry points.

## Included functionality

Version 0.11.1 adds a private phone-render journal, a recoverable latest completed video, exportable per-attempt timing, memory, and thermal reports, interrupted-job cleanup, and explicit history clearing. A failed job keeps the previous successful result. The physical Android workflow can select phone-remix smoke tests without an SD GGUF file. Android build, packaging and engine CI passed; native emulator qualification is tracked separately. Physical and human reviews remain pending.

Version 0.11.0 adds native Android and iOS offline phone video remix: explicit verified person-model import, CPU person or green-screen removal, bounded frame streaming, original AAC audio, cancellation, and memory and thermal guards. Native builds and synthetic real-ONNX and codec tests pass. Physical speed, edge quality, and independent reviews remain pending. See [phone remix setup](docs/PHONE_VIDEO_REMIX.md).

Version 0.10.0 adds [uploaded-video background remix](docs/VIDEO_REMIX.md) across the web, Android, and iOS clients. It keeps the source people, faces, performance, and original first audio track while replacing the scene with the canvas. Free local CPU processing supports explicit Mobile, Laptop, and Desktop budgets, model-free green screen, or a separately configured open PPHumanSeg ONNX model. That computer-remix path uses your own computer. Version 0.11.0 adds the separate native offline-phone path. Voice and face reenactment are not implemented.

Version 0.9.0 adds [opt-in physical-device and model validation tools](docs/DEVICE_VALIDATION.md), safer first-run and model imports with progress, cancellation, and checksums, [independent-review evidence gates](docs/INDEPENDENT_REVIEW.md), and a [Windows web-companion decision and launcher](docs/WINDOWS.md). Hardware measurements and independent reviews remain pending. Simulator and fixture tests do not establish them.

Version 0.8.0 adds [portable layered raster project exchange](docs/PORTABLE_PROJECTS.md) between Android and iOS. Native ink projects remain unchanged. Stroke, text, and mask paths are rasterized only in the exported copy.

Version 0.7.0 adds [local photo depth and body-pose preparation and reviewed OCR](docs/PHOTO_STUDY_WORKFLOW.md), plus [web SD, ControlNet, and LTX jobs](web/local-engine/README.md), original-file export, and computer-job management. The native Android and iOS applications are preserved. Real-weight quality and physical-device validation remain outstanding.

- Android and iOS parent PIN, study-only workspaces, a persisted retry cooldown, and an adult self-declaration.
- Saved style, detail, realism, and scientific-intent prompt directions, with a preview.
- Computer-local ControlNet Canny, depth, and pose guidance, and IP-Adapter image guidance, with explicit local weights.
- iOS raster layers, reversible masks, move, scale, rotate, document undo, and background project snapshots.
- Offline Science Explorer on Android, iOS, and the web edition: ten linked views, body-to-cell and mitosis paths, source references, accessible descriptions, and prompt handoff.
- Android text-label editing and positioning, and persistent study-label layers.
- First-use onboarding and navigation to the creative studio.
- Room-backed progress, transactional updates, and DataStore preferences.
- Material 3 system, light, and dark appearance, and Android 12+ dynamic color for the feature screens and native studio.
- Optional local study reminders, with a contextual notification permission.
- Separate, default-off analytics and crash-reporting controls. Firebase is optional and is not initialized until an explicit opt-in.
- Local schematic templates, photo import, ink annotation, undo and redo, pressure-aware ink, editable project saving, search, rename, delete, ZIP backups, and aspect-preserving PNG export.
- EXIF-aware photo import, rotation-state retention, and background draft recovery.
- Authenticated local-engine job history, cancellation, and result deletion.
- Native CPU SD 1.x GGUF inference on Android and iOS, manual model import, 256 and 512 px resolution controls, Android foreground jobs, and benchmark export.
- Multiple editable ink and raster layers, opacity, blending, visibility, layer erasing, shape and text annotations, streamed SVG, and backward-compatible schema 4 projects.
- Android imported raster layers, reversible masks, move, scale, rotate, boolean shape operations, grids, ruler, perspective guides, symmetry, and adjustable smoothing.
- Android and iOS project folders, folder filtering, and moving existing projects.
- Phone-local 30-second 1080p still-reveal video encoder implementations.
- Optional computer-local SD 1.x text-to-image and img2img, and tiled refinement, requiring separately downloaded model files.
- Optional computer-local LTX image-to-video, with five-segment chaining into a 30-second 1080p delivery file, and a separate FFmpeg still reveal.
- A buildable SwiftUI and PencilKit iOS app, shared native runtime, projects and drafts, photo and PNG export, local reminders, appearance settings, and video implementations.
- A CMake JNI library, a density-only xxhdpi fixture, AAB ABI and density splits, and scripts that inspect the generated binary manifests.
- JUnit 5 and Turbine, Room, WorkManager, and Compose test sources, plus GitHub Actions lint, test, build, split verification, and opt-in signed-release configuration.

## Build locally

Open the repository in Android Studio. Install SDK 35, Build Tools 35.0.0, NDK 27.2.12479018, and CMake 3.22.1. Select JDK 17.

```bash
chmod +x gradlew
./gradlew lintDebug testDebugUnitTest assembleDebug -PfirebaseEnabled=false
```

Install the debug APK on a development device through Android Studio, or:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew connectedDebugAndroidTest -PfirebaseEnabled=false
```

An existing installation signed with another certificate cannot be updated by a new key or a debug build. Keep the required signing key, and export existing data before any separately authorized uninstall. No script in this repository uninstalls user applications.

## CI and signing

[The workflow](.github/workflows/android.yml) runs Android lint, JVM tests, release assembly, AAB packaging verification, and local engine tests. It produces a packaging-test APK set with a temporary test key. That key is not stable between runs. Use your own protected key for durable release updates.

For a stable signed build, create the `android-release` GitHub environment and set `SF_KEYSTORE_B64`, `SF_STORE_PASSWORD`, `SF_KEY_PASSWORD`, and `SF_KEY_ALIAS`. Run the workflow manually on main with `signedRelease=true`. Local signing and split-install commands are documented in [the feature guide](docs/android-features/IMPLEMENTATION.md). The protected signing step does not run on pull requests and does not publish to a store. No paid developer console is used.

Firebase is off by default. To enable it, register `com.scenio.mth` in a free Spark project and supply `app/google-services.json` locally or the optional `GOOGLE_SERVICES_B64` secret. Build with `-PfirebaseEnabled=true`. Do not enable billing-dependent services. Core features work without this configuration.

## Local engine and privacy

See [engine setup](docs/ENGINE.md). The first dependency and model downloads require internet. After preparation, compatible imported GGUF files run on the phone. Optional larger models run on your own computer. See [model setup and testing](docs/LOCAL_MODELS.md). Android can connect to the computer through USB `adb reverse` without cloud inference.

Projects and progress stay in private app storage. App backups are disabled. The app does not request storage-wide access. Photos are opened with Android's document picker. Sending an explicit engine job transfers its input to your local computer. Optional Firebase diagnostics require consent and can transmit pseudonymous app, device, and crash information. Read the in-app disclosure before enabling them.

The web edition stores projects in the browser's local storage. Computer jobs are sent only when you use the local bridge on a machine you control.

## Implementation limits

Version 0.10.0 and later implement the software paths described above. Real model outputs and device latency and thermals are unverified. CPU image generation offers 256 px previews or 512 px outputs with conservative RAM guards. Fast generation on a mid-range phone, and native full-frame 8K denoising, are not promised. Computer tiled refinement can produce an 8K-sized result. Android keeps the original file for export.

LTX motion requires a compatible CUDA computer and complete local weights. It denoises at 768×448 before scaling to 1080p. Model quality, transitions, and ultra-realism remain unevaluated. Still reveal is a separate animation of one image.

Advanced typography and print tools, reviewed medical or scientific content, independently evaluated family and content safety, and complete vector, guide, and export parity remain unfinished. There is no unrestricted Adult Mode and no certified child-safe mode. See the audit for blockers.

## Documentation and validation

- [Android packaging and installation](docs/ANDROID_BUILD.md)
- [Android features: code and setup reference](docs/android-features/IMPLEMENTATION.md)
- [Family access and parent PIN](docs/FAMILY_ACCESS.md)
- [Prompt controls and local image guidance](docs/GENERATION_CONTROLS.md)
- [Editor, raster layers, masks, and folders](docs/EDITOR.md)
- [Science Explorer and content sources](docs/SCIENCE_EXPLORER.md)
- [Master-specification coverage](docs/MASTER_SPEC_AUDIT.md)
- [Current feature status](docs/FEATURE_STATUS.md)
- [Validation boundary](VALIDATION.md)
- [Primary sources](docs/android-features/SOURCES.md)

See [VALIDATION.md](VALIDATION.md) for CI evidence and the difference between automated tests and real-model or device evaluation. The manual physical workflow needs a connected phone and imported weights. Ordinary CI skips it.

Source code is Apache-2.0. Third-party tools and model weights keep their own licenses. There are no paid APIs, ads, mandatory subscriptions, or paid distribution tools. Hosted CI quotas and external free-service terms remain provider-controlled.

## Audit and branding

See [the professional audit](docs/AUDIT.md) for corrected defects, validation, and remaining release blockers. The supplied artwork is in `branding/`, with launcher-safe crops and Android and iOS assets. The public product name is Study Frog. Some in-app strings and internal targets still say StudyForge. Regenerate launcher crops from the original file with `python scripts/generate_branding.py` after installing `engine/requirements.txt`.
