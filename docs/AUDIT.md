# Repository audit — 2026-10-17

Scope: Android, JNI/AAB packaging, local Python engine, project storage, privacy,
CI, iOS app, branding and the original product requirements. Static review
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

## Subsequent corrections — 0.4.0

- Integrated pinned CPU inference on Android/iOS, private GGUF import, native
  bounds checks, RAM/resolution guards and request-scoped Android cancellation.
- Persisted requests outside WorkManager's small Data limit, recovered results,
  and preserved full-resolution computer output for export.
- Added schema 3 independent ink layers, opacity/blend/visibility, layer erasing,
  geometry/text and SVG, with legacy project reads.
- Added local LTX still-conditioned motion chaining and explicit missing-model
  failure; no automatic still-reveal substitution.
- Added a generated Xcode project/native framework, simulator tests, PencilKit
  layers, editable projects/drafts and video implementations. Bounded photo
  decoding and propagated video cancellation; deferred composite export until needed.
- Added private benchmark export and a manual real-device inference workflow.

## Remaining release blockers

- Real SD/LTX inference, quality, latency, memory, thermal/battery and GPU behavior
  need target hardware/model tests. Native compilation is not a benchmark.
- Physical encoders, 16 KB page-size devices, interrupted imports, process death,
  low storage and actual TalkBack/VoiceOver remain unverified.
- Full arbitrary raster/mask, boolean vector, typography/print tools remain
  incomplete beyond the current ink/vector layer features.
- Reviewed medical/science data, semantic zoom chains, CT/MRI and evaluated
  parental controls are missing. No diagnostic or child-safe guarantee.
- iOS still needs complete Android parity and a demonstrated physical release.
- Durable Android updates require the owner's original key. Ephemeral CI
  packaging keys cannot update a differently signed installation.

These gaps prevent an honest “10/10” or complete-master-specification claim.
No paid API/distribution console was added. The supplied icon remains integrated.
See [VALIDATION.md](../VALIDATION.md) and [LOCAL_MODELS.md](LOCAL_MODELS.md) for
exact evidence, testing commands and explicit hardware/model limitations.
