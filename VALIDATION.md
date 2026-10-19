# Validation — 0.9.0 work in progress

New source adds Android/iOS setup/import safeguards, runtime diagnostics,
opt-in physical tests, a Windows companion workflow and review evidence gates.
Local script/content tests (15), benchmark-tool tests (3), repository audit and
whitespace checks pass. Native and Windows CI verification is pending.
Physical model/device tests and independent reviews have not been executed.
The previous 0.8.0 validation below is historical and does not verify this code.

## Previous verified release

# Validation — 0.8.0, 2026-10-19

Verified application/test source: `92e785db39ad305a99b0336cb5c400a23da6121a`.
Its complete Git tree matches the locally reviewed tree
`ceb48949fde2a63c9185d68d9770c0496ddb6085`. The subsequent validation publication
changes documentation and checksums only; application, test and build inputs
remain identical.

**104 automated tests passed; one opt-in real-model test was skipped.**
Two additional browser/service-worker check scripts passed. This is source,
fixture and platform-runtime evidence, not real-model quality certification.

| Verified job | Evidence |
| --- | --- |
| [Android release checks](https://github.com/Tajoor621/StudyFrog/actions/runs/39254623506/job/118066822853) | Lint, 12 JVM tests, instrumentation APK compilation, release APK/AAB assembly and actual bundletool split inspection passed |
| [Android emulator suite](https://github.com/Tajoor621/StudyFrog/actions/runs/39254623587) | 28 cases: 27 passed, one opt-in real-model skip, zero failures/errors |
| [iOS simulator](https://github.com/Tajoor621/StudyFrog/actions/runs/39254623612) | Shared native framework and app build, 15 tests, zero failures |
| [Engine/web](https://github.com/Tajoor621/StudyFrog/actions/runs/39254623506/job/118066822485) | 35 engine, 10 script/content and five bridge tests; browser integration and service-worker privacy checks passed |

The Android combined workflow also repeats the emulator suite after release
assembly; the table uses the already completed independent suite at the exact
same source SHA, without counting duplicate cases. Owner-signed release and
configured Firebase builds were not run. Packaging uses a clearly labeled
ephemeral test key, which must not be used as the owner's stable update key.

## Portable project evidence

Both platforms consume byte-identical, independently generated JSON/PNG fixtures.
Each native suite adds three tests: layered pixels/settings/transforms and
native persistence; bad versions/checksums/dimensions/settings and oversized
input rejection; export as a raster copy without mutating original native ink.
Two script tests verify fixture identity and PNG/checksum contract.

The file cap is 16 MB with early export budgeting, a 16-level nesting cap, up to
16 layers and eight million layer pixels. This reduces JSON/Base64 overhead;
it is not a measured low-memory-device guarantee. See
[portable project scope and manual checklist](docs/PORTABLE_PROJECTS.md).

Android report XML was downloaded and parsed: 28 cases, 27 passes, one skip,
zero failures/errors. Release JVM XML was also parsed: 12 passes, no skips or
failures. Archive digests match GitHub's recorded digests:

- Android emulator reports: `34e816b91ddb6f1bfa13e67450413498ec355a72e2ea9fc472b00c6b7a4fa5dd`.
- Release validation reports: `8704f84c7affc976d77139855c9394e6350d0f2a62840f599454b1d9a8332663`.

The iOS log explicitly records all 15 tests passing, including the new three
portable-project tests. The Android release log confirms generated
`split="config.xxhdpi"`, `splitTypes="base__density"`, `android:hasCode="false"`
and base `android:extractNativeLibs="true"`. No source split metadata or
Play-derived APK id was added.

## Repository integration audit

`python3 scripts/audit_repository.py`: **441 structural assertions**, covering
222 project files. The checks include merge markers, duplicate Kotlin type
identities, manifest component resolution, JNI links, dependency/version/ABI
alignment, icons, local documentation links and secret-file exclusions.
`git diff --check`, repository integrity and all 221 SHA256SUMS entries pass.
These assertions are not additional unit tests and do not prove every runtime
branch or conflict-free behavior under every condition.

## Remaining limits

Portable exchange preserves separate raster layers, not original strokes,
vector/text nodes, disabled mask paths, guides or undo history. There has been
no physical Android-to-iOS file-provider transfer test, low-storage/low-memory
stress certification or calibrated print-color comparison.

Real SD/ControlNet/depth/pose/IP-Adapter/LTX weights, phone/CUDA performance,
physical encoders, medical accuracy, broad medical imaging/atlas content,
advanced vector/typography/print tools, independent family-control evaluation,
owner-signed Android updates and physical iOS distribution remain outstanding.
The full master specification is not complete; see
[the coverage audit](docs/MASTER_SPEC_AUDIT.md).

Previous release evidence remains available in
[the 0.7.0 validation record](https://github.com/Tajoor621/StudyFrog/blob/121465087e4d64dfd38b9d0d192fb2f06cde9ac6/VALIDATION.md).
