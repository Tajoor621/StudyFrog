# Portable layered projects

Android and iOS can exchange local layered raster copies using **Export portable
layers · Android / iOS** and **Import portable layers · Android / iOS**. Share the
resulting `StudyForge.sfgx.json` using any file-transfer method you choose. No
account, network permission, new dependency or cloud service is required.
Android uses the system document picker; iOS uses security-scoped file access.
Family-restricted workspaces do not expose these general project controls.

Save a native project before exporting. Import creates a separate native local
project before replacing the canvas. Invalid imports do not replace the current
canvas. Existing Android schema 1–4 ZIP backups and iOS PencilKit projects keep
their original formats and remain supported.

## What survives

- Base image, prompt, negative prompt, title and folder.
- Up to 16 separate raster layers, in back-to-front order, including hidden layers.
- Layer names, visibility, opacity, normal/multiply/screen, translation, uniform
  scale and rotation, plus the selected layer when opening the exchange file.
- Visible ink, shapes, text, erasures and enabled masks become transparent PNG
  pixels within each layer. These can be painted over, erased and masked again.

This is **layered raster interchange**, not lossless vector/PencilKit interchange.
Individual strokes, text nodes, disabled masks, original mask paths, guides,
undo history and generation profiles/seeds/settings do not transfer. Native
projects remain the source for those edits. Reopening an imported native iOS
project selects the first layer, as with existing native projects.

The base remains at its current editor dimensions (maximum 2048 per side).
Layer pixels are resampled to at most 1024 per side and at most eight million
pixels across all layers. A 16-layer project therefore uses at most 707px on its
longest layer side. Rendering and color management can differ slightly between
platforms; this is not a print-color or exact-pixel archival format.

## Format version 1

The UTF-8 JSON `format` is `studyforge-layered-raster`, `version` is `1`.
`title` (160 UTF-16 units), `prompt` and `negative` (2000 each), `folder` (80),
`selected` (zero-based), `base`, and `layers` are required. Names are nonblank
and at most 80 UTF-16 units. Text allows tab/newline except folder names.

Each image asset contains `width`, `height`, lowercase `sha256` of its original
PNG bytes, and unwrapped standard Base64 `png`. Each layer contains `name`,
`visible`, `opacity` (0–1), `blend`, `transform` and `image`. The transform has
`x`/`y` (−1–1, fractions of canvas width/height), `scale` (0.1–4), and `rotation`
(−180–180 degrees, clockwise in screen coordinates). Transform origin is canvas
center. Layer images are centered and aspect-fitted to the base before the
transform. Opacity and blend apply after the baked ink/mask pixels.

Readers cap the whole input at 64 MB, nesting at 16 levels, the base PNG at
32 MB and layer PNGs at 5 MB each. Dimensions and checksums are checked before
pixel allocation; the combined layer budget is checked incrementally. No paths,
URLs, archives, fonts, scripts or model weights are loaded from this format.
Checksums detect corruption; they are not authentication or encryption.

Both native test suites read identical copies of the independently generated
[shared fixture](../content/portable-project-v1.json). Tests exercise layered
appearance/settings, native persistence, export without mutating original ink,
and rejection of bad versions/checksums/dimensions/transforms/settings. These
checks do not establish physical-device file-provider interoperability or
lossless native stroke exchange.
