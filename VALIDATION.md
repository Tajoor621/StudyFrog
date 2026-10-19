# Validation — 0.9.0, 2026-10-19

Verified application/test source: `b530b97cf5b9d7fe0f3487b1d76f3260b2b6ebb3`.
Its Git tree matches the reviewed local tree
`1e301d65d420998f647b0fc42a3b3c18d26eaab9`. This validation publication
changes documentation and checksums only; application, test and build inputs
remain identical.

**119 unique automated tests passed; four opt-in physical/model tests were
skipped.** Browser integration and service-worker privacy scripts also passed.
These results verify source, fixtures and emulator/simulator/hosted runtimes;
they do not establish real-model quality or physical-device performance.

| Verified job | Evidence |
| --- | --- |
| [Android release checks](https://github.com/Tajoor621/StudyFrog/actions/runs/39275953671/job/118140776938) | Lint, 12 JVM tests, instrumentation APK compilation, release APK/AAB assembly and bundletool inspection passed |
| [Android emulator](https://github.com/Tajoor621/StudyFrog/actions/runs/39275953591/job/118140584421) | Parsed XML: 33 cases, 31 passed, two physical/model skips, zero failures/errors |
| [iOS simulator](https://github.com/Tajoor621/StudyFrog/actions/runs/39275953602/job/118140181526) | Shared native framework and app build; 20 cases, 18 passed, two physical/model skips, zero failures |
| [Engine/web](https://github.com/Tajoor621/StudyFrog/actions/runs/39275953671/job/118140777291) | 38 engine, 15 script/content and five bridge tests passed; browser integration and service-worker privacy checks passed |
| [Windows companion](https://github.com/Tajoor621/StudyFrog/actions/runs/39274892749/job/118136503874) | PowerShell launcher check, three benchmark-tool and five bridge tests passed on Windows; these eight cases repeat tests above and are not counted twice |

Windows ran at `b6b2dd400277e9d9da923644e527f39dd22646dc`. The subsequent
source diff contains no Windows launcher, workflow, benchmark-tool or bridge
changes. Its optional FFmpeg/Tesseract checks correctly reported those tools
absent; this is not an encoder, OCR or actual-model Windows benchmark.

## Packaging and report verification

Downloaded report ZIP bytes match GitHub's artifact SHA-256 digests:

- Android emulator reports: `338d1f3f7b7909ea163f5bd8980a00da7f69a8a61d09445724b6996618b4325e`.
- Release validation reports: `2d994ad9c1e19365f162696004a7f954c5460a2ba16f717ce5ad535401ce37e5`.

JUnit XML confirms 12 JVM passes and 31 instrumentation passes plus two skips.
The iOS log records 20 tests with two skips and zero failures. Engine/script/
bridge logs report their complete suites passing without dependency skips.
The release log confirms generated `split="config.xxhdpi"`,
`splitTypes="base__density"`, `android:hasCode="false"` and base
`android:extractNativeLibs="true"`. No source split metadata or Play-derived
APK id was added. Packaging uses an ephemeral test key; owner-signed release,
configured Firebase builds and physical iOS distribution were not executed.

## New setup and validation coverage

Android/iOS import tests cover rejected headers, cancellation before and during
copy, checksum mismatch, preservation of an installed model, orphan staging
cleanup and successful import of a synthetic transport fixture. That fixture
is not a trained model and does not prove inference compatibility. Android's
first-run Compose screen passes at 200% font scale. Process diagnostic fields
are checked in both native suites; physical screen-reader use remains pending.

Physical benchmark tools require explicit opt-in and actual model hashes, keep
failed attempts, and distinguish cached import receipts from newly hashed model
bytes. Stress tools exercise repeated 30-second encodes and bounded layer
roundtrips when explicitly run on hardware. These tests were skipped here.
Windows remains a web companion for 0.9.0; see [the decision](docs/WINDOWS.md).

## Repository integration audit

The structural audit passes 486 assertions across 241 project files. Repository
integrity, whitespace checks and all 240 SHA256SUMS entries pass. The structural audit checks merge markers, duplicate
Kotlin identities, manifest components, JNI links, dependency/version/ABI
alignment, icons, documentation links and secret-file exclusions. These checks
are not additional unit tests or proof of every runtime interaction.

## Physical and independent evidence still required

`python3 scripts/release_readiness.py` reports eight pending requirements and
no invalid records. `--require-ready` correctly exits 2. The production evidence
register contains no fabricated model measurements, physical sessions or human
reviews. The protected owner-signed release job is gated on this evidence;
normal developer builds remain available.

Pending: two representative model digests and a physical accessibility/memory/
encoder session on each of Android, iOS and Windows, plus qualified independent
science and family-control reviews. No physical hardware, trained model weights
or independent reviewers were available in this session. Automated font/layout
checks, hosted Windows checks and simulator tests cannot substitute for them.
See [device protocols and commands](docs/DEVICE_VALIDATION.md) and
[independent review scope](docs/INDEPENDENT_REVIEW.md).

Advanced vector/typography/print tools, broad medical imaging/atlas content,
real-model quality/performance targets and other master-spec gaps remain listed
in [the coverage audit](docs/MASTER_SPEC_AUDIT.md). Portable exchange preserves
raster layers, not original strokes, vector/text nodes or undo history. The full
master specification is not complete and no 10/10 certification is claimed.

Previous evidence: [0.8.0 validation](https://github.com/Tajoor621/StudyFrog/blob/b6b2dd400277e9d9da923644e527f39dd22646dc/VALIDATION.md).
