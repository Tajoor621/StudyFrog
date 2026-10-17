# Honest implementation status

| Requirement | Current delivery |
| --- | --- |
| Prompt-first Android interface | Implemented source; Android compile pending |
| Six modes | UI choices; most share the same underlying generation pipeline |
| Text-to-image | Computer-local SD 1.x adapter, requires separately downloaded model; not runtime-tested |
| Photo-to-image | Computer-local img2img adapter; not model-tested |
| Offline generation on phone | Geometric schematics only; no mobile diffusion runtime |
| Local model inference | Runs on your computer after download, no paid endpoint |
| Native 8K generation | Pending; no 8K claim is made |
| Tiled 8K img2img refinement | API-only implementation; model/hardware testing pending; not native 8K denoising |
| VAE tiling | Enabled in SD adapter; not equivalent to native 8K diffusion |
| Hierarchical science visuals | Selector and illustrative template only; semantic zoom chains pending |
| Medical images / CT / MRI overlays | Not implemented; no diagnostic functionality |
| Scientific correctness | Human review required; no validator or accuracy slider |
| Drawing layers | Base image + independent ink layer; undo; saved flattened |
| Full layers, masks, blend modes, pressure, tilt | Pending |
| Vectors / typography / booleans / print management | Pending |
| Video 30 sec, 1080p, 24 fps | Verified FFmpeg reveal on computer; Android hardware encoder implementation supplied, device verification pending |
| Ultra-realistic subject motion / camera instructions | Pending, not implemented |
| AnimateDiff / video-model integration | Pending |
| Local history / projects | Android image + prompt library; folders and full editable history pending |
| Adult Mode / age gate / parental PIN | Not implemented; unrestricted explicit-sexual generation excluded |
| Certified child-safe mode | Not claimed; robust safety system pending |
| iOS | Source starter: photos, schematics, saving and PNG export; no Xcode project or build |
| AAB + density + JNI | Source, wrapper, CI and verification delivered; actual Android build pending |
| 16 KB native compatibility | NDK flexible page-size flag; device testing pending |
| Zero paid services | No paid APIs, in-app payments, ads or Play Console integration |

## Priorities before a production release

1. Run the supplied Android workflow and physical-device packaging tests.
2. Integrate and benchmark a supported phone-local inference runtime with
   licensed models; choose realistic RAM, speed, battery and thermal limits.
3. Implement persistent editable layers and lifecycle-aware background jobs.
4. Integrate a still-conditioned open video model, evaluate temporal consistency,
   memory needs and segment transitions before promising thirty seconds of motion.
5. Implement reviewed scientific diagram templates, labels and provenance.
6. Build iOS inference parity and establish a feasible distribution route.
7. Replace simple content checks with evaluated input/output controls before
   offering any child-oriented mode.

Do not advertise this foundation as the complete master specification or a 10/10 product.

## Integrated Android foundations (0.2.0)

Room/DataStore progress, onboarding, Material 3 appearance, settings, local
reminders, opt-in telemetry, accessibility semantics, test sources, CI and
environment-based release signing are now wired into the Android app.
Package: com.scenio.mth. Build/device verification is still required.
The new feature screens use Compose; the studio retains its original native
view implementation. Project images remain flattened on disk.
