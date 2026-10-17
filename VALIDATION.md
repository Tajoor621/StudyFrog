# Validation

## Locally checked

- Eleven Python engine tests pass, covering API authorization, input validation,
  persistence, missing-model errors, diagram output, tiled composition mechanics
  with a mock inference callback, actual encoded video frames, cancellation cleanup, active-job deletion protection, and strength/guidance limits.
- The earlier full-length FFmpeg validation produced 1920x1080, 24 fps,
  720 frames and exactly 30 seconds.
- Integrated XML/YAML, resource references, package paths and shell syntax are
  statically checked before export.

## Requires CI or target hardware

The local environment has no Android SDK/NDK, javac or installed Gradle toolchain.
Android compilation, lint, JVM/Compose tests, JNI loading, MediaCodec encoding,
generated AAB split manifests, signing and physical-device behavior must be
verified by the supplied workflow and a device. No successful Android build is
claimed merely because the files were exported to GitHub.

Real diffusion models, CUDA hardware and Xcode are unavailable here. Model
quality, speed, 8K refinement, scientific accuracy and iOS compilation remain
unverified. Tiled mock tests do not establish real-model quality or performance.

The integrated source uses Room 2.7.2 with Kotlin 2.0/KSP2. JNI legacy packaging
is configured through AGP's supported DSL; the removed legacy Gradle property
is not required by this build. Native JNI symbols are updated for com.scenio.mth.
There is one launcher, FeaturesActivity, and it opens the native studio activity.

No real signing keys, Firebase configuration, bearer tokens, user projects or
model weights are committed. The packaging-test workflow key is ephemeral.
