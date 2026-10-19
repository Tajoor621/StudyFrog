# Study Frog web edition

The root `index.html` is an offline-first static PWA intended for GitHub Pages at `/StudyFrog/`. It adds a functional canvas-based creative studio, local project persistence, a basic science topic browser and an in-app Help section. It preserves the existing Android and iOS source trees.

## Functional scope

- Import PNG/JPEG/WebP up to 12 MB; aspect-fit on a 960×540 canvas.
- Add centered text labels, undo up to 20 prior canvas states, clear, and export a genuine PNG.
- Store up to 12 project snapshots locally (browser storage quotas apply), reopen or delete.
- Edit/save prompt, negative prompt and visual-direction metadata.
- Choose an introductory science topic to prefill the prompt editor.
- Installable PWA metadata and service-worker cache for the static interface.
- Contextual in-app Help for every available web tool.

## Honest limits

The static GitHub Pages editor has no browser AI inference or required backend. Through the optional [local bridge](local-engine/README.md), the web UI now connects your computer’s SD/ControlNet/LTX engine for image/video/refinement jobs and reviewed OCR. It also retains the Ollama text-to-image adapter. There are no user accounts or cross-device sync. Prompts alone do not generate images without a configured engine. LocalStorage is not a reliable long-term backup; export important PNGs. The science cards are introductory summaries, not reviewed medical diagrams. Offline caching does not include any model weights. Existing Android/iOS native capabilities remain separate.

## Manual verification checklist

1. Open the GitHub Pages URL and confirm Studio, Projects, Science and Help navigation.
2. Import a valid image, add a label, undo, and export PNG.
3. Save a project, reload the browser, open the saved project, then delete it.
4. Send a Science topic to Studio and confirm prompt transfer.
5. Check offline reload after the first successful load, and install when supported.
6. Confirm the original Android/iOS CI still passes; no native source was modified.

These are **manual test instructions**, not a claim that browser/device testing has been executed. Native AI generation requires a supported locally imported model and the native app or configured computer-local engine; see [local model instructions](../docs/LOCAL_MODELS.md).
