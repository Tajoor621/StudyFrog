# Repository audit — 2026-10-17

Scope: Android, JNI/AAB packaging, local Python engine, project storage, privacy,
CI, iOS app, branding and the original product requirements. Static review
is not a substitute for device, model-quality or accessibility evaluation.

Historical findings below refer to the version named in each section. Current coverage is recorded in [MASTER_SPEC_AUDIT.md](MASTER_SPEC_AUDIT.md).

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
- Broad reviewed medical/science data, AI semantic zoom, CT/MRI and evaluated
  parental controls are missing. No diagnostic or child-safe guarantee.
- iOS still needs complete Android parity and a demonstrated physical release.
- Durable Android updates require the owner's original key. Ephemeral CI
  packaging keys cannot update a differently signed installation.

These gaps prevent an honest “10/10” or complete-master-specification claim.
No paid API/distribution console was added. The supplied icon remains integrated.
See [VALIDATION.md](../VALIDATION.md) and [LOCAL_MODELS.md](LOCAL_MODELS.md) for
exact evidence, testing commands and explicit hardware/model limitations.


## Science Explorer continuation — 0.4.1

Added an original offline catalog of ten linked views with textbook references,
readable descriptions, label visibility and prompt-context handoff on both
platforms. Android study labels survive editable project serialization and can
be renamed/repositioned. Existing-image replacement requires confirmation.
Android navigation selection survives recreation. Content contract tests guard
platform-copy drift, references, graph cycles and geometry bounds.

This replaces the previous hierarchy-only selector for the included example
paths. It does not establish a complete anatomy atlas, AI semantic zoom,
independent scientific review, clinical accuracy or iOS editable diagram text.
See [SCIENCE_EXPLORER.md](SCIENCE_EXPLORER.md) for scope and provenance.


## Integration audit — 2026-10-18 (Asia/Dubai)

| Finding | Correction |
| --- | --- |
| Native image task can outlive coroutine cancellation and race output deletion | Signal cancellation, join native work in a non-cancellable cleanup section, then delete output; check cancellation after JNI returns |
| Cached SD pipeline can occupy device memory while LTX loads | Release cached image pipeline and unused CUDA allocations before and after motion jobs; failed CUDA cleanup cannot terminate the worker |
| SVG text/brush marks omitted alpha; single-point marks omitted pressure | Export opacity on all mark types, pressure-scaled dots and the initial brush point; XML/geometry regression coverage |
| Benchmark documentation promised a model hash absent from worker report | Include imported model SHA-256 or explicit unknown marker |
| Interrupted Science Explorer audit/docs were not published | Include current coverage, source references, validation links and updated tree/checksums |
| Cross-file consistency depended on manual checks | Add automated repository guard for manifest classes, launcher/JNI identities, version pins, ABIs, icons, merge markers, secret-file exclusions and documentation links |

The model handoff deliberately reloads the image model on the next image job;
this trades warm-cache speed for lower retained GPU memory between image/video
jobs. Actual GPU memory and native cancellation responsiveness need hardware
validation. These fixes do not remove the outstanding product requirements.
See [MASTER_SPEC_AUDIT.md](MASTER_SPEC_AUDIT.md) for the full requirement matrix.


## Advanced editor and folder integration — 0.5.0

- Added bounded raster-layer import and non-destructive hide/restore masks;
  keep immutable layer assets outside JSON and use UUID-only asset references.
- Unified canvas, PNG and video compositing in `LayerRenderer`; SVG streams raster
  data and preserves transforms, masks and vector geometry.
- Extended undo from ink-only actions to complete editor snapshots with limits
  on action count, points and retained raster pixels. Loading a project resets
  history, while undo/redo restores content without resetting the opposite stack.
- Added move/scale/rotate, boolean shape combinations, grid/ruler/perspective,
  mirrored marks, adjustable smoothing, optional tilt width and system fonts.
- Corrected multiply compositing: Android 10+ native blend mode and a strip-based
  software fallback on Android 7–9 preserve the base outside transparent marks.
- Added folder assignment/filter/move on Android/iOS without interpreting folder
  names as filesystem paths; older project formats remain readable.
- Enforced layer/document/ZIP/image bounds, rejected malformed raster references,
  preserved new assets in backups and tightened odd-dimension photo downsampling.

See [EDITOR.md](EDITOR.md) for exact limits and platform differences. Regression
coverage exercises rendering, non-destructive restoration, full-action history,
serialization/backup round trips, folder persistence and legacy decoding.
This addition does not establish real-model quality, clinical accuracy, child
safety, physical stylus feel or complete original-specification delivery.


## Family, generation and iOS layer integration — 0.6.0

- Added private parent-PIN storage, persisted cooldown and restricted entry/worker checks on Android; Keychain-backed root gating on iOS. Bundled study/anatomy remains available; private data/general inputs/browser links are hidden.
- Android consent observers force telemetry off while restricted, including restart. Adult self-declaration is not verified age or child-safety certification.
- Saved bounded visual-direction profiles on both platforms and exposed/persisted iOS inference parameters.
- Added local-only SD 1.x ControlNet/IP-Adapter image paths, explicit encoder loading, cache-mode replacement and request validation; iOS computer image jobs use an ephemeral, no-redirect session.
- Replaced iOS parallel editor arrays with one layer model; added raster/mask/transform/order/document-undo support and bounded native assets with legacy defaults.
- Mask-render failures cannot silently export unmasked pixels. Project snapshots encode off the UI thread; pending folders stay out of the library. Stale rename updates preserve current folder metadata.
- Android spinner selections/dropdowns use explicit contrasting colors. The iOS permission description includes image and motion transfers.
- Push CI filters relevant platform inputs; full PR/manual checks remain. No build/test checks were removed.

See [FAMILY_ACCESS.md](FAMILY_ACCESS.md), [GENERATION_CONTROLS.md](GENERATION_CONTROLS.md), [EDITOR.md](EDITOR.md) and current [VALIDATION.md](../VALIDATION.md). Real models, physical devices, deliberate bypass testing and independent medical review remain outstanding.
