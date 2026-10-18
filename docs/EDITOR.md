# Editable layers and folders — 0.5.0

Generation remains the main workflow. These Android tools refine generated or
imported images. They use Android graphics, system fonts and local files; no new
SDK, backend, permission or paid service is required.

## Android controls

- Import a base photo, generate an image or open a study diagram, then choose
  **Import photo as editable layer** to add a separate raster layer.
- Open **Layers** to select, rename, hide, change opacity/blend, reorder or delete
  layers. **Move, scale and rotate** changes the entire selected layer, including
  its marks and mask. Reset restores its transform.
- With drawing enabled, **Mask: hide** removes visibility from the selected
  layer; **Mask: restore** restores it. Layer controls can disable or clear the
  mask. The imported raster is retained. **Erase prior marks** instead erases
  content already drawn on that layer; it does not erase the base image.
- **Guides, symmetry and stabilization** offers 5/10/20-part grids, a percentage
  ruler, one-point perspective lines, horizontal/vertical/both mirror drawing,
  and smoothing. Guides appear only in the editor. Mirrored marks are saved and
  exported as ordinary editable marks. The Perspective ray tool points toward
  the fixed vanishing point at 50% width, 35% height.
- Stylus pressure controls stroke width. The optional tilt switch also adjusts
  width using reported stylus tilt. Device support must be tested physically.
- Sans serif, serif and monospace select Android system font families for new
  labels. Text and position remain editable. There is no custom-font import or
  professional typesetting/CMYK workflow.
- **Combine last two closed shapes** applies union, intersection, subtraction or
  XOR to the last two rectangles, ellipses or combined paths in the active layer.
  The result is a filled vector shape, represented by bounded contour samples.
  Undo restores the originals. This is not a complete vector-node editor.
- **Undo edit / Redo edit** covers completed drawing gestures, masks, text edits,
  layer operations, transforms, guide changes and shape combinations. Loading or
  replacing the base image starts a new edit history.

PNG and video compositing use the same layer renderer as the canvas. SVG export
preserves embedded raster assets, geometry, labels, layer transforms, opacity,
blends and masks. SVG bitmaps stream to the output writer to avoid retaining a
second full-size base64 copy. Viewer support for blend modes can vary. Android 10+ uses the native source-over
multiply blend. Android 7–9 uses a bounded strip compositor because legacy
PorterDuff multiply modulates alpha and can erase the base outside marks; its
preview can cost more CPU time. Regression coverage compares both paths.

## Persistence and resource limits

Android writes schema 4 projects. Readers retain support for legacy ink arrays
and schema 3 layer documents. Raster files use canonical UUID-derived names
(`layer-<uuid>.png`) beside `base.png`, `image.png`, `ink.json`, and `project.json`.
Raster assets are never paths supplied by the backup author. Project saves stage
all files in a temporary directory and rename the completed directory.

There are at most 16 editable layers, 512 marks, 100,000 total points, 5,000 points
per mark, 64 contours per combined path, 200 characters per label and 80 per layer
name. Imported layer images are at most 1024 pixels per side, with at most
8,000,000 raster pixels in the current document. The base photo is independently
bounded to 2048 pixels per side for editing. Original generated files remain
available through original-resolution export.

History retains at most 32 undo actions and 250,000 stored undo points; it drops
older states when raster references across the editor history exceed 16,000,000
pixels. This deliberately trades unlimited history for bounded mobile memory.
Bitmaps shared by snapshots are treated as immutable.

ZIP imports reject paths, directories, duplicates, unknown assets, unsupported
versions and missing raster references. Limits are 20 files, 64 MB total,
24 MB per base/preview, 5 MB per layer asset, 8 MB ink and 512 KB metadata. Export
also rejects projects above 64 MB rather than creating an unimportable archive.
Backups are local exports and may contain private photos, prompts and folder
names; the user chooses their destination through the document picker.

## Folders on Android and iOS

Choose a folder for new saves; a blank name means Unfiled. Library filters show
All folders, Unfiled and existing folder names. Search matches titles or prompts.
Existing projects can be moved through Android's project menu or iOS's context
menu. Folder names are metadata, never filesystem paths; changes use atomic
metadata writes. Names are trimmed, limited to 80 characters and reject control
characters. Legacy projects without folder metadata remain Unfiled.

Android ZIP backups preserve folder metadata. iOS folders persist in its own
editable project format and drafts. Android ZIP and PencilKit projects are not
interchangeable. Raster/mask/transform/vector tools described above are Android
features; full iOS editor parity remains unfinished.

## Verification

`AdvancedEditorTest` checks non-destructive raster masks, mask restoration,
transforms and guide exclusion from rendered export, document/SVG serialization,
undo/redo across layer and boolean edits, mirrored gesture history and malformed
references/transforms, plus native/legacy multiply alpha preservation. `ProjectBackupTest` checks folder/raster/transform/mask
metadata through ZIP export and import. iOS store tests check folder persistence,
invalid names and decoding old records without folder metadata.

Run Android instrumentation on a development device or emulator and iOS tests
on macOS as described in the root validation guide. Stylus feel, accessibility
interaction, low-memory behavior and SVG viewer parity still need device checks.

Rendering references: Android's [BlendMode API](https://developer.android.com/reference/android/graphics/BlendMode#MULTIPLY)
and [PorterDuff.Mode API](https://developer.android.com/reference/android/graphics/PorterDuff.Mode#MULTIPLY).
