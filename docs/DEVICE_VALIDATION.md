# Device validation — 0.9.0

Status: **tooling implemented; no physical test or real-weight result is claimed**.
Use existing owned hardware and licensed local weights. No paid device lab,
cloud inference, Play Console or card-required service is needed. Use a dedicated
test phone/profile; do not erase a personal app to resolve signing conflicts.

## Representative matrix

| Platform | Minimum measurement set |
| --- | --- |
| Android | Actual SD 1.x GGUF Q4 and Q8 conversions, each with exact file SHA-256 and source/license; 256px, 10 steps, seed 42, botany and product prompts, two repeats each |
| iOS | The same two compatible GGUF conversions on a physical 6 GB+ device; two prompt cases with XCTest clock/memory metrics; repeat sessions to assess variation |
| Windows | Two local SD 1.x Diffusers safetensors model bundles with safety checkers; 256px baseline and optional 512px; botany/product cases, exact bundle inventory, CPU/CUDA configuration |

These are representative **profiles**, not bundled or verified model downloads.
Record the actual checkpoint, conversion/runtime revision, quantization, license,
hash and hardware. Compare quantizations of one checkpoint separately from
comparisons between different trained checkpoints. A GGUF signature does not
prove diffusion compatibility. Trained weights have not been supplied here.

Native calls include model loading; they do not claim a warm persistent cache.
Computer reports tag first-load versus reused-pipeline calls. Timing includes
output handling, so it is end-to-end work rather than kernel-only time. Do not
mix model types, resolutions or cold/reused calls into a performance claim.
Record room conditions, charging state, battery/low-power setting and idle time.
Start cool; stop on heat, OS low-memory signals or repeated allocation failures.

## Android on a USB-connected phone

Builds need JDK 17, the pinned free SDK/NDK/Gradle toolchain and USB debugging.
Import a trusted model through the app first; confirm its actual SHA-256.
Connect exactly one dedicated, authorized physical phone. If an existing app has
a different signing key, use the matching key or a separate test device/profile;
these instructions never uninstall it or clear its data.

```bash
./gradlew assembleDebug -PfirebaseEnabled=false
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew connectedDebugAndroidTest -PfirebaseEnabled=false \
  -Pandroid.testInstrumentationRunnerArguments.class=com.scenio.mth.features.RealModelDeviceTest \
  -Pandroid.testInstrumentationRunnerArguments.runRealInference=true \
  -Pandroid.testInstrumentationRunnerArguments.expectedModelSha256="$MODEL_SHA256" \
  -Pandroid.testInstrumentationRunnerArguments.benchmarkSize=256 \
  -Pandroid.testInstrumentationRunnerArguments.benchmarkRepeats=2
mkdir -p device-evidence
adb exec-out run-as com.scenio.mth cat files/device-test/report.json > device-evidence/android-benchmark.json
```

`MODEL_SHA256` must be the actual 64-character lowercase model digest, not a
sample value. Repeat after explicitly importing the second model. Preserve each
report with a distinct filename. The test hashes the actual installed bytes
before timing; it captures both failed and successful attempts, seed/settings,
sampled peak process PSS, before/after available RAM, native/Java heap and OS
thermal/power state. Sampling is 500 ms and can miss peaks. SDKs below 29 report
thermal status as unknown, not nominal. Debug instrumentation overhead is part
of the result; compare like build modes and use Android Studio profiling for
release-oriented CPU/memory analysis.

For the opt-in encoder/layer stress suite:

```bash
./gradlew connectedDebugAndroidTest -PfirebaseEnabled=false \
  -Pandroid.testInstrumentationRunnerArguments.class=com.scenio.mth.features.DeviceStressTest \
  -Pandroid.testInstrumentationRunnerArguments.runDeviceStress=true
adb exec-out run-as com.scenio.mth cat files/device-test/stress.json > device-evidence/android-stress.json
```

It renders two real 30-second H.264 files, checks 1920×1080, duration and 720
samples using MediaExtractor, deletes its temporary videos, then performs five
16-layer import/export cycles. This is bounded allocation stress, **not** OS
memory-pressure or recovery certification. Run manual recovery checks below.
The manual `physical-device.yml` workflow requires a self-hosted device runner;
it is not dispatched automatically. Uploading diagnostic artifacts is off by
default. JSON and public-prompt outputs may still disclose hardware/model data;
review them before sharing. Failed measurements must be retained, not deleted
from performance summaries.

## iOS physical device

Use the existing native-framework/XcodeGen build setup and a free Xcode personal
team where applicable. Physical signing/provisioning remains owner-supplied;
unsigned simulator builds are not physical-device evidence. Import the model in
the app on the test phone before running its hosted tests. Do not treat a model
path on the Mac as a file inside the iPhone's sandbox.

In Xcode, Edit Scheme → Test → Arguments → Environment Variables, explicitly set
`STUDYFORGE_RUN_REAL_MODEL=1`, `STUDYFORGE_MODEL_SHA256` to the actual imported
file digest and `STUDYFORGE_BENCHMARK_SIZE=256`. Run
`PhysicalDeviceTests.testRealModelBenchmark` on the connected iPhone/iPad.
XCTest records clock and memory metrics; its JSON attachment records model,
settings, attempt outcomes and device snapshots. Use a second imported model
and repeat; retain `.xcresult` files and export the JSON attachments from Xcode.

For encoder/layer stress enable `STUDYFORGE_RUN_DEVICE_STRESS=1` and run
`PhysicalDeviceTests.testRepeatedEncoderAndLayerStress`. All these tests skip
when the explicit flags are absent and reject the simulator when enabled.
The normal app can export its latest local-generation diagnostic JSON. Resident
memory snapshots differ from XCTest peak metrics; do not label them equivalent.
Native generation observes serious thermal state and critical memory-pressure
signals and requests cancellation; OS termination/recovery still requires tests.

## Windows/computer model measurements

See [Windows decision and setup](WINDOWS.md). Stop other engine generation before
benchmarking; the benchmark runs its own local pipeline and never downloads
weights. In PowerShell, `MODEL_DIRECTORY`, `MODEL_ID`, `MODEL_SOURCE_URL` and
`MODEL_LICENSE_URL` must contain your actual selected local model/provenance.

```powershell
.venv\Scripts\python.exe engine/benchmark.py --model-directory "$env:MODEL_DIRECTORY" --model-id "$env:MODEL_ID" --source-url "$env:MODEL_SOURCE_URL" --license-url "$env:MODEL_LICENSE_URL" --output benchmark-output/windows-model.json
# First command fingerprints/preflights only. Opt in to real inference:
.venv\Scripts\python.exe engine/benchmark.py --model-directory "$env:MODEL_DIRECTORY" --model-id "$env:MODEL_ID" --source-url "$env:MODEL_SOURCE_URL" --license-url "$env:MODEL_LICENSE_URL" --output benchmark-output/windows-model.json --physical-host --run
```

`--physical-host` is your attestation that this is physical hardware, not a VM.
The bundle digest hashes a sorted inventory of actual local model, config, tokenizer and license files. Provenance
URLs are recorded, not fetched or independently license-verified. Reports include
system/Python/accelerator, load/reuse state, timing, failure types, process
lifetime peak RSS, and CUDA peak allocated/reserved bytes when CUDA is present.
Lifetime RSS is not a per-call sampled peak. Desktop thermal/battery readings
are not supplied by this tool; record them manually with existing system tools.
Two-repeat median/p95 descriptions are not statistically stable population
percentiles. Images are saved for a human to review; no automated realism,
scientific accuracy or clinical claim is made.

## Physical accessibility and recovery sessions

For each platform, record the build version, hardware/OS, date, operator,
actual settings, observations, screenshots/video and failures. Never mark a
check passed simply because automated semantics or simulator tests pass.

| Check | Physical acceptance procedure |
| --- | --- |
| Screen reader | TalkBack on Android, VoiceOver on iOS, Narrator/keyboard on Windows. Navigate onboarding, model choice/import/cancel/errors, prompt, layers, library, settings and family PIN. Verify labels, focus order, progress announcements and modal escape. |
| Font scaling | Largest supported system font; Windows browser 200% zoom and narrow viewport. No clipped actionable text, overlapping controls or unreachable confirmation/cancel actions. Include landscape and keyboard-visible states. |
| Contrast/targets | Measure actual light/dark/dynamic-color states: 4.5:1 normal text, 3:1 large text and essential non-text controls. Check 48dp Android / 44pt iOS targets and keyboard focus visibility; record any failures. |
| Low-memory recovery | Save a project, exercise 16 layers repeatedly, background/foreground during import/generation/export and inspect OS memory/termination logs. Verify completed projects reopen and cancelled imports preserve the old model. Compare constrained physical devices. |
| Encoder repeat | Inspect two full exports with real playback and frame/duration checks; cancel an export, rotate/background and test low-storage errors. Preserve project data and remove partial files; record actual behavior, not assumed support. |

Android `adb shell am send-trim-memory com.scenio.mth RUNNING_CRITICAL` and Xcode's
Simulate Memory Warning can exercise warning paths; they do not reproduce
actual memory exhaustion or jetsam. Do not fill an everyday phone's storage or
run an unbounded allocator. Use a dedicated test device and controlled profiles.
Inspect logcat/ApplicationExitInfo or Xcode device logs for actual OS termination.

Use `validation/device-session.schema.json` for manual session records. Add
checksum references to `validation/release-evidence.json` only after execution.
Benchmark/model records and all independent reviews are initially empty/pending.
The gate is evidence tracking, not a guarantee that hardware, reviewers or
science content have been independently certified.

Sources: [Android performance testing](https://developer.android.com/training/testing/instrumented-tests/performance),
[Android thermal APIs](https://developer.android.com/games/optimize/adpf/thermal),
[Apple thermal state](https://developer.apple.com/documentation/foundation/processinfo/thermalstate-swift.enum),
[Apple memory-pressure source](https://developer.apple.com/documentation/dispatch/dispatchsource/makememorypressuresource(eventmask:queue:)).

Accessibility references: [WCAG text contrast](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html),
[Android touch targets](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views),
[Apple button hit regions](https://developer.apple.com/design/human-interface-guidelines/buttons).
