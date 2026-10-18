# Study Frog — remaining implementation and release gates

Updated 2026-10-18. This document distinguishes completed branding from outstanding implementation and verification. See [MASTER_SPEC_AUDIT.md](MASTER_SPEC_AUDIT.md), [FEATURE_STATUS.md](FEATURE_STATUS.md) and [../VALIDATION.md](../VALIDATION.md).

## Branding migration safety

- Public name: **Study Frog**; GitHub slug and Gradle root: `StudyFrog`.
- Android manifest and iOS display name: **Study Frog**.
- Preserve Android `com.scenio.mth`, iOS bundle ID, Swift target/scheme, `ios/StudyForge` source folder, existing class names, JNI symbols, signing aliases and CI artifact paths until a dedicated atomic migration is implemented and validated. Their presence is not itself an app-name conflict.
- Before renaming internal paths, inventory all references and perform the migration in one reviewable change with Android lint/JVM/emulator, iOS simulator, packaging and restore/migration tests.

## Feature gaps — acceptance gates

| Area | Required work | Acceptance evidence |
| --- | --- | --- |
| Real inference | Evaluate imported SD GGUF and LTX weights, local guidance adapters, prompt adherence and 8K tiled seams on target hardware | Reproducible fixtures, model versions, latency/RAM/thermal measurements, qualitative review |
| Device resilience | Exercise interrupted imports, low disk space, process death, rotation, backgrounding, 16 KB page devices and real video encoders | Physical Android and iOS test logs and recovery assertions |
| Editing parity | Add missing iOS vector, typography, SVG/boolean/guide tools; cross-platform editable project interchange | Round-trip tests and iOS/Android edit fidelity fixtures |
| Science quality | Independently review anatomy/mitosis diagrams, citations, labels and medical claims; do not claim diagnostic accuracy | Reviewer sign-off and reference-linked correction log |
| Imaging | Develop and validate OCR, reliable factual labels, CT/MRI overlay and semantic zoom only with suitable datasets and review | Dataset, test cases and domain review; no unsupported clinical claims |
| Family controls | Threat-model bypasses, verify Keychain/PIN persistence, accessibility and child-oriented content on devices | Independent review and documented limitations |
| Production distribution | Validate owner-controlled signing identity and device installation/update on both platforms | Owner-signed Android APK/AAB and iOS distribution evidence; no secrets committed |
| Accessibility | Real TalkBack/VoiceOver, font scaling, contrast, keyboard and reduced-motion checks | Device-based checklist and issues resolved |

## Verification rules

1. No feature is marked complete merely because CI compiles or a placeholder exists.
2. Existing [Android run 39121050440](https://github.com/Tajoor621/StudyFrog/actions/runs/39121050440) and [iOS run 39121520515](https://github.com/Tajoor621/StudyFrog/actions/runs/39121520515) predate this branding change; rerun both on the new commit before declaring a regression-free rename.
3. Preserve data formats and identifiers; add explicit migration tests before altering persistent names or project schemas.
4. Do not claim physical-device validation, production signing, model quality or 10/10 completeness without corresponding evidence.
