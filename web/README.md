# Study Frog web edition

The root `index.html` is an offline-first static app for GitHub Pages at `/StudyFrog/`. It is a canvas studio, a local project library, the bundled Science Explorer, and an in-app tool guide. Android and iOS source trees stay in place.

Live site: <https://tajoor621.github.io/StudyFrog/>.

## Functional scope

- Import PNG, JPEG, or WebP up to 12 MB. The photo is aspect-fit on a 960×540 canvas.
- Add a centered label, or place it at the last canvas click. Undo keeps up to 20 canvas states. Clear and export a genuine PNG.
- Draw pen, line, rectangle, and ellipse marks, or paint white with the eraser. An alignment grid can be shown and is not part of the export.
- Store up to 12 project snapshots in this browser. Search, rename, duplicate, reopen, or delete them. Download or import a JSON backup of those PNG snapshots.
- Edit and save the prompt, negative prompt, and visual direction with the project.
- Open the offline study catalog: ten linked body-organization and mitosis schematics, descriptions, OpenStax links, prompt handoff, and placement on the canvas. If that file cannot be read, five short topic summaries remain.
- Choose system, light, or dark appearance. The choice is stored locally.
- Installable PWA metadata and a service-worker cache for the static interface, catalog, and icons.
- A tool guide for the controls on this page.
- Optional local SD, ControlNet, LTX, Ollama image, video-remix, and reviewed OCR jobs through the [local bridge](local-engine/README.md).

## Honest limits

[Media Learning](../docs/MEDIA_LEARNING.md) is an optional computer-bridge workspace for OCR, indexed lecture notes, citations, and review questions. It does not run on GitHub Pages.

The static GitHub Pages editor has no browser AI inference and no required backend. Prompts do not generate images unless a configured engine on your computer accepts the job. The bridge serves only the public web files. There are no accounts and no cross-device sync.

Local storage is not a reliable long-term backup. Export important PNGs. The science diagrams are original simplified teaching drawings, not reviewed medical images and not to scale. Offline caching does not include model weights, prompts sent to the computer, or generated media. Android and iOS capabilities remain separate. See [local model instructions](../docs/LOCAL_MODELS.md) and [Science Explorer](../docs/SCIENCE_EXPLORER.md).

## Manual verification checklist

1. Open the GitHub Pages URL and move among Studio, Projects, Science, and Help with the keyboard and the pointer.
2. Import a valid image, add a label, draw a mark, undo, and export a PNG.
3. Save a project, reload, search, rename, open, and delete it. Download a backup and import it.
4. Open a science view, read its description, send it to Studio, and place the schematic on the canvas.
5. Reload offline after the first successful load, and install the app when the browser offers it.
6. Switch the theme and confirm text and controls remain readable.
7. Confirm Android and iOS behavior you rely on still matches the native docs. This web pass does not replace device tests.

These are manual checks, not a claim that every browser has been exercised. Automated coverage is `web/tests/science.test.cjs`, `web/tests/service-worker.test.cjs`, `web/tests/test_bridge.py`, and `web/tests/browser.cjs` against engine fixtures. Fixture tests do not measure model quality.
