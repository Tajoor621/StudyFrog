# Validation — 0.11.0, 2026-11-05

Verified native phone implementation/tests:
`2742df290026e7017729517290191981bd0f7a6f`, Git tree
`192369334eaf5a36de6fa9ee7b83cb1e9ed21c72`.
This publication updates documentation, static web Help wording/cache version
and checksums. Native implementation, dependencies and test inputs remain
identical to the revisions listed below. Static JavaScript syntax and the
service-worker privacy test pass after the Help/cache changes.

**137 unique automated tests passed; four opt-in physical/model tests were
skipped.** Browser integration and service-worker privacy scripts also passed.
This establishes source/fixture and emulator/simulator runtime evidence, not
physical-device speed, real-human segmentation quality, clinical accuracy or
full master-spec completion.

## Verified runs

| Job | Evidence |
| --- | --- |
| [Android release](https://github.com/Tajoor621/StudyFrog/actions/runs/42003848035/job/127165532207) | At `82874a8`: lint, 12 JVM tests, instrumentation APK compilation, release APK/AAB assembly and bundletool inspection passed |
| [Android emulator](https://github.com/Tajoor621/StudyFrog/actions/runs/42004166401/job/127166722564) | At `2742df2`: 37 cases, 35 passed, two opt-in physical/model skips, zero failures/errors |
| [iOS simulator](https://github.com/Tajoor621/StudyFrog/actions/runs/42004166303/job/127166858138) | At `2742df2`: native framework/app build, 24 cases, 22 passed, two opt-in physical/model skips, zero failures |
| [Engine/web](https://github.com/Tajoor621/StudyFrog/actions/runs/42003848035/job/127165532598) | At `82874a8`: 47 engine, 15 script/content and six bridge tests passed, including the real computer ONNX smoke case; browser integration passed |

The diff from `82874a8` to `2742df2` changes only two native test files and
checksums. Production Android/iOS/engine sources, dependency versions and build
scripts are byte-identical. Latest native suites execute the added rejection
assertions. The earlier failed Android run exposed an ONNX Java buffer-access
call mismatch; `82874a8` corrected it and the completed runs above supersede it.
The initial canceled/failed runs are not included in pass totals.

Windows remains the previously tested web companion. No native Windows shell
or new Windows codec qualification is claimed. The laptop/desktop computer
remix implementation and budgets are retained. Prior Windows launcher/bridge
checks remain [0.10.0 evidence](https://github.com/Tajoor621/StudyFrog/blob/fbda07d6bc6965007b5819a0566ef6816f8e0e63/VALIDATION.md), not additional unique tests.

## Actual native phone runtime coverage

Both native suites load the real checksum-pinned PPHumanSeg FP32 ONNX using
ONNX Runtime 1.20.0. Synthetic native person-mode videos encode successfully;
silent source footage stays silent. Green-screen tests verify blue replacement
pixels and retained red foreground, original AAC-track presence, dimensions,
duration, trim, cancellation rejection/output cleanup and portrait output.
The portrait fixture contains an actual 90-degree display matrix, verified by
ffprobe before use. Native model import tests reject invalid bytes while
preserving the previously installed checksum-valid model.

The Android suite adds four test cases; iOS adds three. Inputs are synthetic
colours/geometry with a timed tone/marker, not a real person, licensed user
recording or physical-device measurement. Model loading/encoding success does
not prove hair/crowd/occlusion quality, identity preservation in every scene,
realtime speed, long-GOP performance, lip sync on all recordings or 16 KB-page
hardware compatibility.

The new native path uses no computer/upload request during rendering, has a
15-second/480p/15fps/64 MB mobile cap, and retains the first AAC source track.
Other sound formats, larger inputs and higher budgets use the existing local
computer engine. Person weights are explicitly imported and checksum-verified;
production app bundles do not contain the test model/footage. No paid SDK/API,
account, broad media permission or new camera/microphone permission was added.
See [phone setup and scope](docs/PHONE_VIDEO_REMIX.md).

Computer-engine tests still verify audiovisual marker timing (120 ms synthetic
onset tolerance), source audio frequency/duration, one-use authenticated upload,
expiry, profile limits and cleanup. Those tests are separate from native codec
or physical lip-sync qualification. See [computer remix scope](docs/VIDEO_REMIX.md).

## Packaging and integrity evidence

Downloaded ZIP digests match GitHub's artifact metadata:

- Android emulator reports: `74e7f024599a835720f8acd561d0dc46801296ab00a7295963918996a3cd78e4`.
- Android release reports: `346a1614eec1f45f038aa59d7aee4bd9b27c8955d0d22d4de1d37dfa47b88340`.

Parsed XML confirms 12 JVM passes and 37 instrumentation cases with two skips,
no failures/errors, including all four phone-remix cases. The iOS log explicitly
records 24 cases with two skips and all three new phone cases passing.
Release logs confirm generated `split="config.xxhdpi"`,
`splitTypes="base__density"`, `android:hasCode="false"` and base
`android:extractNativeLibs="true"`, with the added native ONNX SDK packaged.
No generated split metadata or Play-derived id was hardcoded in source.

Repository integrity, structural integration audit, whitespace checks and all
259 SHA256SUMS entries pass across 260 project files. The audit checks merge
markers, Kotlin identity conflicts, manifest components, JNI, dependency/ABI/
version alignment, icons, documentation links and secret-file exclusions.
These are static assertions, not extra unit tests or a universal conflict-free
runtime guarantee. Packaging uses an ephemeral test key; owner-signed release,
configured Firebase builds and physical iOS distribution were not executed.

## Evidence still pending

The production register has eight pending representative generative-model/
physical-session and independent science/family-review requirements, with no
invalid records. Strict `--require-ready` intentionally exits 2; the owner-signed
release remains gated. The native person-segmentation smoke case does not replace
representative SD/LTX model benchmarks. New phone-transfer/render sources are
included in the family-review subject digest. No physical run or human approval
has been invented.

Actual phones/laptops/desktops, qualified independent reviewers and representative
user-authorised media are needed for the remaining physical/quality gates.
Use the [device protocols](docs/DEVICE_VALIDATION.md) and
[independent review pack](docs/INDEPENDENT_REVIEW.md). Advanced graphics, medical
atlas/imaging and other unmet master requirements remain in
[the coverage audit](docs/MASTER_SPEC_AUDIT.md). No complete-master or 10/10
certification is claimed.
