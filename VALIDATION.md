# Validation — 2026-10-17

## Verified in GitHub Actions

[Successful workflow run](https://github.com/Tajoor621/StudyFrog/actions/runs/39045549980) for source commit
`c5d24edb32c3c778da6609638b58f96de84156e2`, using JDK 17, SDK 35, NDK 27.2.12479018 and Gradle 8.9.

- Android lint, seven JVM tests, release APK assembly and release AAB assembly pass.
- Eleven engine tests and four manifest-parser tests pass.
- All eight Android instrumentation tests pass on an API 35 x86_64 emulator:
  onboarding recreation, accessible switches at double font scale, concurrent
  Room updates, database reopening, disabled reminders, editable ZIP backup
  round trips, traversal rejection and native-studio edit-state recreation.
- Total: **30 automated tests pass**.
- Generated density manifest: `split="config.xxhdpi"`,
  `splitTypes="base__density"`, `android:hasCode=false`.
- Base application manifest: `android:extractNativeLibs=true`.
- JNI library is present, density APK contains no DEX, and the studio JNI
  method loads successfully in the emulator.
- CI artifacts contain the packaging-test APK set, AAB and validation reports.
  Packaging-test APKs use a disposable key, not a durable release identity.

The stable signed-release job was intentionally skipped: it requires the
owner's protected signing secrets and an explicit workflow dispatch. Optional
Firebase-enabled builds require the owner's configuration and remain unverified.

## Other checks

Repository integrity, XML parsing, backup exclusions and shell syntax were
checked during the audit. Asset generation is reproducible from the committed
original artwork with `scripts/generate_branding.py`.

Engine tests include authentication, input limits, persistence, missing-model
failure, cancellation cleanup, deletion protection, tiled composition using a
mock inference callback, and an actual FFmpeg video with 72 frames. An earlier
full-duration FFmpeg check produced 1920×1080, 24 fps, 720 frames and 30 seconds.
These checks do not establish AI quality or phone video-encoder compatibility.

## Physical-device and model testing still required

Real diffusion models, CUDA hardware, Xcode and an ARM64 phone were not available
for validation. Phone-local diffusion is not implemented. Model quality,
scientific accuracy, real 8K refinement, thermal/battery behavior and realistic
subject-motion video are not validated. Android MediaCodec reveal encoding
compiles but requires actual encoder/device testing.

The emulator suite is not a full TalkBack, contrast, OS-version, process-death,
storage-exhaustion or low-memory certification. iOS remains an uncompiled starter.

No production signing keys, Firebase configuration, real bearer tokens, user
projects or model weights are committed. Exported Room schemas are available
in the packaging artifact; retain schema history before database migrations.
