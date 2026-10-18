# Offline Science Explorer — 0.4.1

Open **Explore science · linked levels and labeled diagrams** from the Android
or iOS studio. No model, account, download, new dependency or permission is needed.
The catalog and original vector geometry are bundled in the app.

## Included learning paths

- Human body → cardiovascular system → heart chamber flow → cardiac muscle
  tissue → cardiac muscle cell.
- Prophase → prometaphase → metaphase → anaphase → telophase.

These ten views are conceptual schematics, not anatomical tracings, diagnostic
images or a complete atlas. They omit structures and are explicitly not to scale.
The heart flow chart omits valves/vessel detail; the cell-division sequence uses
a small example chromosome count, not the human diploid count. Cytokinesis is
identified separately from nuclear division. A teacher/domain reviewer should
review these introductory teaching aids before formal academic use.

Each view includes a summary, text description, labels and source links. Text
uses native font scaling; the diagram has an equivalent readable description.
Labels can be shown/hidden. Deeper/previous navigation changes the actual
selected study view; Android preserves selection, label visibility and notes
across activity recreation. iOS retains the selected view while its sheet stays open.

**Open diagram in editor** replaces the canvas after confirmation when an image
already exists. Save unsaved work first. On Android, labels are independent text
strokes on the Study labels layer: use **Edit text labels / positions**, opacity,
visibility, layer erasing, PNG/SVG export and editable project backups. On iOS,
the diagram/labels are rendered into the base image; PencilKit annotations remain
independent, but diagram label text is not independently editable there.

**Use this view as generation prompt** preserves the current image and replaces
the prompt with the selected hierarchy context and view instructions. Optional
additional directions are limited to 500 characters. Choose an existing local
or computer generation action afterward. A model may invent or mislabel details;
the bundled references do not validate generated results automatically.

## Content sources and provenance

Facts were checked against these primary textbook sections on 2026-10-17:

- [OpenStax Anatomy and Physiology 2e, 1.2](https://openstax.org/books/anatomy-and-physiology-2e/pages/1-2-structural-organization-of-the-human-body): organization hierarchy.
- [OpenStax Anatomy and Physiology 2e, 19.1](https://openstax.org/books/anatomy-and-physiology-2e/pages/19-1-heart-anatomy): heart chambers and circulation.
- [OpenStax Anatomy and Physiology 2e, 10.1](https://openstax.org/books/anatomy-and-physiology-2e/pages/10-1-overview-of-muscle-tissues): muscle-tissue properties.
- [OpenStax Biology 2e, 10.2](https://openstax.org/books/biology-2e/pages/10-2-the-cell-cycle): mitosis sequence.

Descriptions and geometry are original, simplified app content; no textbook
illustration is copied. Source titles/URLs are bundled for attribution and
further reading. Following a source opens the external browser and requires
connectivity; the learning views themselves never fetch network content.
There is no claim of independent scientific peer review or clinical validation.

## Files and maintenance

Authoritative content: `content/study-catalog.json`. Runtime copies:
`app/src/main/assets/study-catalog.json` and
`ios/StudyForge/Resources/study-catalog.json`.

Android implementation: `science/StudyCatalog.kt`, `StudyExplorerActivity.kt`,
and studio integration in `MainActivity.kt`. iOS: `StudyCatalog.swift`,
`StudyExplorerView.swift` and the existing studio. Platform renderers use the
same normalized primitive coordinates and explicit 1200×900 output dimensions.

After a content edit, update both runtime copies:

```bash
cp content/study-catalog.json app/src/main/assets/study-catalog.json
cp content/study-catalog.json ios/StudyForge/Resources/study-catalog.json
python3 -m unittest discover -s scripts -p 'test_*.py' -v
```

Content tests verify identical copies, primary references, descriptions, bounded
geometry, graph links and absence of parent/phase cycles. Android tests verify
rendering every view with/without labels, editable label serialization and
navigation/recreation. iOS tests verify context prompts and every rendered view.
These checks do not certify scientific accuracy or actual screen-reader usability.

No model inference was added to these deterministic views. No analytics event
or private project upload is triggered by exploring the catalog. Full semantic
AI zoom, multi-system anatomy, CT/MRI, diagnostic use, parental safety controls
and the other audit gaps remain outside this release.
