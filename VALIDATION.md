# Validation — 0.10.0 in verification, 2026-11-05

Uploaded-video background remix is implemented in the shared local engine and
web/Android/iOS clients. Local verification: 46 engine tests passed before the
optional real-ONNX smoke case was added, six bridge tests and 15 script tests
passed. Synthetic FFmpeg tests confirm changed background, retained foreground,
original soundtrack frequency/sync, trim/silent handling, cancellation, bounded
uploads and one-use input ownership. Native compile/runtime and updated browser
integration CI are pending at publication. Do not treat previous 0.9.0 counts
as verification of these source changes.

The app does not run person remix entirely on a phone; native/mobile clients
use the user's local computer. Output budgets and scope are documented in
[video remix](docs/VIDEO_REMIX.md). Real human-edge quality and physical device
speed are not established by synthetic tests or a model runtime smoke test.
The existing physical-device and independent science/family review requirements
remain pending. No production evidence/approvals have been fabricated.

Previous verified release: [0.9.0 validation](https://github.com/Tajoor621/StudyFrog/blob/c181e8e5708edf573adcc453e4b3f8411050fccd/VALIDATION.md).
