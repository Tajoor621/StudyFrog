# Validation — 0.10.0, 2026-11-05

Verified video-engine/browser source:
`48882345583e810001e0eb2a087a4d35f3f628e8`, Git tree
`8304952043f1037920f6d62b1bc447af303f4db0`.
This report publication changes documentation/checksums only.

**130 unique automated tests passed; four opt-in physical/model tests were
skipped.** Browser integration and service-worker privacy scripts also passed.
Tests and source/fixture checks do not establish physical-device performance,
real human-edge quality, clinical validity or full master-spec completion.

## Verified jobs and source identity

| Job | Source and evidence |
| --- | --- |
| [Latest engine/web](https://github.com/Tajoor621/StudyFrog/actions/runs/41991979723/job/127126098955) | At `4888234`: 47 engine, 15 script/content and six bridge tests passed; browser integration and service-worker privacy checks passed |
| [Android release checks](https://github.com/Tajoor621/StudyFrog/actions/runs/41989760242/job/127119229807) | At `0696876`: lint, 12 JVM tests, instrumentation APK compilation, release APK/AAB assembly and bundletool verification passed |
| [Android emulator](https://github.com/Tajoor621/StudyFrog/actions/runs/41989760326/job/127119149487) | At `0696876`: parsed XML records 33 cases, 31 passed, two physical/model skips, zero failures/errors |
| [iOS simulator](https://github.com/Tajoor621/StudyFrog/actions/runs/41989526550/job/127118159033) | At `1b09223`: native framework/app build and 21 cases, 19 passed, two physical/model skips, zero failures |
| [Windows companion](https://github.com/Tajoor621/StudyFrog/actions/runs/41989526584/job/127118158928) | At `1b09223`: launcher check, three benchmark-tool and six bridge tests passed; these nine repeat cases above and are not counted twice |

Compared with `4888234`, the Android build inputs at `0696876` are byte-identical:
no changes to app/shared/Gradle sources, Android workflow or packaging scripts.
The later changes affect only video-engine import/timing handling, its tests,
video documentation and checksums. iOS build inputs and Windows launcher/bridge/
benchmark-test inputs are likewise unchanged since `1b09223`. Git diffs verified
these boundaries. A redundant Android release build at `4888234` was still
running when this report was prepared; the completed identical-input build
above supplies native build evidence. This report does not claim that duplicate
run has completed.

Windows correctly reported optional FFmpeg/ffprobe/Tesseract absent. Its job
checks launcher/transport behaviour, not a Windows media encode or physical-host
benchmark. Actual codec/model runtime tests ran in the Linux engine job.

## Video-remix runtime evidence

The Linux job explicitly downloaded the official Apache-licensed PPHumanSeg
FP32 ONNX into temporary storage and verified SHA-256
`552d8a984054e59b5d773d24b9b12022b22046ceb2bbc4c9aaeaceb36a9ddf24`.
The real model loaded and completed a person-mode video encode. Synthetic
input verifies runtime compatibility; it does not validate hair, crowds,
occlusion, fast motion, identity preservation in every scene or device speed.

Real FFmpeg synthetic-media tests verify replaced background pixels, retained
foreground pixels, H.264/AAC output, frame count, silent sources and trimming.
A visual marker and audio pulse share a source timestamp; their output onsets
remain within the test's 120 ms tolerance. The 440 Hz soundtrack frequency and
video/audio durations are also checked. Audio-first source stream ordering,
expired upload rejection, permission/profile bounds, one-use upload ownership,
authentication, cancellation and cleanup pass.

Browser tests check permission/profile rejection before upload, the raw upload
route and submitted background/clip payload using explicit network fixtures.
iOS tests verify bounded file-provider copying, byte preservation and cleanup
for oversized/empty inputs. Android and iOS compile their new native controls;
physical native picker/network/codec qualification remains pending.

See [setup, limits and privacy](docs/VIDEO_REMIX.md). All clients use the same
free local computer processor. Mobile/laptop/desktop budgets are enforced caps,
not measured speed guarantees. Standalone phone person remix is not provided.
No paid API/cloud service, new mobile SDK or broad media permission was added.

## Packaging and repository integrity

Downloaded report ZIP digests match GitHub artifact metadata:

- Android emulator: `5179ae3bb6263028d47db5758dc6394bb084798b9eababbbfd6fcc23add6ed5f`.
- Android release validation: `a2da03463323c70ae82d68722160cd4938d82b235a7ffe12fcdf204cddac4662`.

Parsed XML confirms 12 JVM passes and 31 instrumentation passes plus two skips.
The iOS log records 21 tests with two skips and zero failures. Release logs
confirm generated `split="config.xxhdpi"`, `splitTypes="base__density"`,
`android:hasCode="false"` and base `android:extractNativeLibs="true"`.
Source manifests still contain no generated split metadata or Play-derived id.
Packaging uses an ephemeral test key. Owner-signed release/configured Firebase
builds and physical iOS distribution were not executed.

Repository integrity, structural audit, whitespace checks and all 247
SHA256SUMS entries pass across 248 project files. The structural audit checks
merge markers, duplicate Kotlin identities, manifest components, JNI links,
dependency/version/ABI alignment, icons, documentation links and secret-file
exclusions. These are static assertions, not additional unit tests or proof of
every runtime interaction.

## Remaining qualification

The production evidence register still reports eight pending physical-device/
representative-model and independent science/family-review requirements, with
no invalid records. Strict `--require-ready` intentionally exits 2. Family
review subjects now include the new transfer/remix sources, so future approvals
are bound to this feature's code too. No approvals or physical results have
been invented. The owner-signed release job retains its strict evidence gate.

Physical phone/laptop/desktop performance, real-person edge quality, native
file-provider/network interactions and original user-soundtrack sync require
representative media/hardware testing. Existing advanced graphics/medical and
other master-spec gaps remain in [the coverage audit](docs/MASTER_SPEC_AUDIT.md).
See [device protocols](docs/DEVICE_VALIDATION.md) and
[independent review scope](docs/INDEPENDENT_REVIEW.md).

Previous release: [0.9.0 validation](https://github.com/Tajoor621/StudyFrog/blob/c181e8e5708edf573adcc453e4b3f8411050fccd/VALIDATION.md).
