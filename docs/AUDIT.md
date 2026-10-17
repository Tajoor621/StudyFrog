# Repository audit — 2026-10-17

Scope: Android, JNI/AAB packaging, local Python engine, project storage, privacy,
CI, iOS starter, branding and the original product requirements. Static review
is not a substitute for device, model-quality or accessibility evaluation.

## Defects corrected

| Priority | Finding | Correction |
| --- | --- | --- |
| Critical | CI SDK setup requested the removed `tools` package | Request platform-tools explicitly |
| Critical | Initial export omitted Android data-layer files | Restore Room/DataStore sources; add integrity check |
| High | Network-security XML omitted mandatory lint attributes | Explicitly disable subdomains for loopback hosts |
| High | bundletool rejected apksigner-style `env:` passwords | Use restricted temporary password files with cleanup |
| High | Backup rules excluded settings but left photos/prompts eligible for some device transfers | Exclude every storage domain from cloud and device transfer; explicit editable backups remain user controlled |
| High | Concurrent Room schema export could produce an empty JSON file | Use the Room Gradle plugin to manage schema outputs |
| High | Portrait photos were stretched to a landscape viewport/output | Aspect-fit geometry shared by canvas and video; source-sized PNG export |
| High | Imported EXIF orientation was ignored | Bounded decoding and all EXIF transforms |
| High | Project saves flattened annotations irreversibly | Schema 2 stores base, independent ink, preview and metadata; legacy reads supported |
| High | Image compression and project I/O blocked the UI | Move rendering, encoding, storage and result transfer to worker threads |
| High | Video download/export retained entire files in RAM | Stream bounded results and export cache files through the document picker |
| High | Encoder setup failures could leak resources | Clean up codec, surface, muxer and EGL on every exit |
| High | Editing while a project save completed could clear the newer dirty state | Clear dirty state only when the saved edit revision still matches |
| Medium | Rotation lost editing state | ViewModel session; app-private draft recovery on backgrounding |
| Medium | No editable backup or library management | Bounded ZIP import/export, search, rename and confirmed deletion |
| Medium | Engine had no job lifecycle controls | Authenticated history, cancellation checkpoints and deletion of finished results |
| Medium | Engine model initialization could leave a failed cached pipeline | Assign pipeline only after successful device transfer |
| Medium | Native editor ignored saved appearance | Observe DataStore appearance and dynamic accent preferences |
| Medium | Manifest verifier assumed one namespace and boolean rendering format | Parse actual attribute values, including URI prefixes and literal/typed booleans; add regression tests |
| Medium | Canvas allocated geometry every redraw | Cache viewport geometry when size/image changes |
| Medium | iOS starter generated a literal UUID filename suffix | Correct UUID interpolation to avoid rapid-save collisions |
| Medium | Brand artwork was not packaged | Android adaptive/round/density icons, in-app artwork, notification avatar, iOS icon set |

## Added regression coverage

Geometry tests check portrait/landscape fit and invalid dimensions. Engine tests
check job cancellation cleanup, active-job deletion rejection and parameter
bounds in addition to authorization, storage, tiling mechanics and actual video
frames. Instrumentation sources check editable backup round trips and traversal
rejection. Repository integrity checks guard required source files and manifests.
CI remains configured to fail on lint errors, rather than suppressing findings.

## Remaining release blockers

- No phone-local diffusion runtime or downloaded-model manager. Current AI
  inference runs on a separately configured local computer.
- No realistic subject-motion video model. The implemented 30-second video is a
  still-image reveal. Device encoder behavior requires physical-device testing.
- No complete layer/mask/blend/vector/text system; one base plus editable ink.
- No reviewed medical atlas, CT/MRI simulation or scientific accuracy validator.
- No evaluated parental safety system; no child-safe guarantee or Adult Mode.
- iOS remains a SwiftUI starter and assets, without an Xcode project or parity.
- Real models, 8K quality, thermals, latency and memory have not been benchmarked.
- Device accessibility, large text, TalkBack drawing alternatives, process death,
  storage exhaustion and low-memory behavior need release testing.
- Signing continuity requires the existing app's actual signing key. CI test
  keys are disposable; signing secrets must never be committed.

These gaps prevent an honest “10/10” or complete-master-specification claim.
All implementation changes use local or free/open tooling and no paid API.

Platform backup behavior was checked against the [Android backup guide](https://developer.android.com/identity/data/autobackup). Room schema configuration follows the [Room migration guide](https://developer.android.com/training/data-storage/room/migrating-db-versions). Dependencies remain pinned; Dependabot opens reviewable updates.

## Verification result

[Workflow 39045549980](https://github.com/Tajoor621/StudyFrog/actions/runs/39045549980) passes for source `c5d24edb32c3c778da6609638b58f96de84156e2`:
Android lint, release APK/AAB assembly, split/native packaging verification,
seven JVM tests, fifteen Python tests and eight API 35 emulator tests. All
**30 automated tests pass**. The traversal test accepts either Android's ZIP
path guard or the app's validator and verifies that no file escapes the project
folder; the production path checks remain strict.

See [VALIDATION.md](../VALIDATION.md) for evidence, artifact/signing details and
untested hardware/model behavior. These results validate the implemented
foundation and do not remove the product gaps listed above.
