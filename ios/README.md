# StudyForge iOS — 0.6.0

Buildable SwiftUI/PencilKit app with a shared native CPU image runtime, photo
import, editable layers/projects/drafts, PNG export, onboarding/appearance,
opt-in reminders, AVAssetWriter reveal video and optional computer motion client.
Projects include folder assignment, library filtering and move actions. Legacy
records without folder metadata remain supported. Raster/mask/transform layers and document undo are now ported; advanced vector,
guide and interchange tools are not yet ported; full Android parity and a production-signed
physical release remain incomplete.

On a Mac install free Xcode/iOS 17+ simulator, CMake/Ninja/Git and
[XcodeGen 2.42.0](https://github.com/yonaskolb/XcodeGen/releases/tag/2.42.0).
From the repository root:

```bash
bash scripts/build-ios-native.sh
xcodegen generate --spec ios/project.yml
xcodebuild -project ios/StudyForge.xcodeproj -scheme StudyForge -showdestinations
# Replace SIMULATOR_UDID with an available id from the preceding output:
xcodebuild -project ios/StudyForge.xcodeproj -scheme StudyForge -destination 'platform=iOS Simulator,id=SIMULATOR_UDID' -derivedDataPath build/ios-derived CODE_SIGNING_ALLOWED=NO test
```

The iOS workflow automatically selects a simulator and uploads app/test artifacts.
The generated Xcode project and NativeBuild are ignored; `project.yml` is
source-of-truth. Initial downloads require internet. For device builds, run
`bash scripts/build-ios-native.sh iphoneos`, regenerate/open the project and
configure your own signing team in Xcode. Default simulator CI disables signing.
Personal-device signing has Apple-imposed conditions; permanent broad iOS
distribution cannot be universally promised free. Simulator work needs no paid
Apple membership.

Import a compatible complete SD 1.x GGUF through Settings; see
[LOCAL_MODELS.md](../docs/LOCAL_MODELS.md). CPU inference is 256/512px and keeps
the app awake; stay in the app. Real weights, latency, physical-device memory
and encoders remain unverified. Simulator compilation is not a phone benchmark.

Models/projects are private and backup-excluded. PhotoPicker avoids broad
library access. Notification permission is requested when enabling reminders.
There is no iOS analytics SDK. Optional computer motion requires consent to
transfer prompt/photo to a local host, using a memory-only token. The supplied
artwork remains integrated in icons and in-app assets.


Science Explorer includes the same ten bundled conceptual views as Android,
with source references, descriptions, navigation, label visibility and prompt
handoff. iOS labels are flattened into the diagram base; PencilKit annotations
remain independent. See [SCIENCE_EXPLORER.md](../docs/SCIENCE_EXPLORER.md).


Family access uses Keychain-backed PIN/cooldown and a study-only root workspace; see [FAMILY_ACCESS.md](../docs/FAMILY_ACCESS.md). Prompt controls, saved inference settings and optional computer image/guidance jobs accompany motion; see [GENERATION_CONTROLS.md](../docs/GENERATION_CONTROLS.md).

The editor now supports photo layers, black/white masks, opacity/blend, transforms, ordering/deletion and document undo. Assets save independently off the main thread and legacy records remain readable; see [EDITOR.md](../docs/EDITOR.md). Actual Keychain/signing, physical encoders/accessibility and real weights remain unverified.
