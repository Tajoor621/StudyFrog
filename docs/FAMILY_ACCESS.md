# Family access controls — 0.6.0

The creative workspace uses adult self-declaration, not verified age detection. A parent can configure a six-digit PIN and enable a separate study-only workspace on Android or iOS. This is local app access control, not an unrestricted Adult Mode or a certified content filter.

## Set up and use

Open Settings → Family access. Choose and confirm the PIN, then enable study-only access. Unlocking or changing the PIN requires the current PIN. Five failed attempts create a persisted 15-minute cooldown. Changing the PIN preserves the current restricted/unrestricted state.

Study-only access exposes bundled Science Explorer views, labels and anatomy. General prompts, arbitrary photos, private projects, generation, computer transfers, editor handoff and source-browser links are hidden. Source titles remain available for teacher review. Projects/models are preserved.

Android checks launcher/studio entry, studio resume, computer requests and generation workers. Enabling restriction cancels the native request and unique WorkManager job, and restricted background results are not displayed. Consent observers force Android telemetry off while restricted, including restart; previous consent settings remain stored. Previously enabled generic local reminders can still run.

iOS switches its root workspace and cancels departed-studio tasks. Native results and computer requests also check access. The read-only catalog omits editor/prompt/browser actions. Its computer client uses an ephemeral session and refuses HTTP redirects. iOS has no cloud analytics SDK.

A computer may already have received a request before locking. Cancellation reaches model execution at a safe checkpoint. This PIN does not control another engine client, exported files, the computer's filesystem or the operating system.

## Storage and recovery

Android stores random 128-bit salts and 256-bit PBKDF2-derived values with 210,000 iterations: SHA-256 on API 26+, SHA-1 for API 24–25 compatibility. The policy is private, backup-excluded and atomically written; damaged records fail closed. PIN entry is not saved in view state and its screen prevents screenshots.

iOS uses PBKDF2-HMAC-SHA256 with the same salt/output/iteration sizes and ThisDeviceOnly Keychain storage. Keychain items can survive reinstall. PINs are not sent to a server or included in project backups.

There is no account or remote PIN recovery. Keep the PIN safe and export important projects first. Do not assume iOS reinstall clears it. Device owners/rooted environments can alter app data; clock changes can affect a local cooldown. OS parental controls provide stronger device restrictions. Resetting/uninstalling can destroy private projects; this workflow does not do so.

## Source, permissions and verification

Android: the four classes in app/src/main/java/com/scenio/mth/family/, with integration in FeaturesActivity, MainActivity, StudyExplorerActivity, LocalGenerationWorker, FeatureRuntime, SettingsScreen and AndroidManifest.xml. Family activities are non-exported. iOS: FamilyAccess.swift, StudyForgeApp.swift, StudyExplorerView.swift and ComputerEngine.swift.

No Android dependency/permission or backend is added. iOS uses system Security/CommonCrypto. Tests cover PIN format, salted verification, changed PINs, persistence, cooldown and corrupt records. iOS vault tests use isolated memory storage; actual device Keychain/signing behavior remains to be evaluated.

Before release, test Back/notifications/restart while restricted; absent browser/editor actions; five failed PINs and restart cooldown; lock during native/computer jobs; rotation/backgrounding; TalkBack/VoiceOver and large fonts. Bundled diagrams are conceptual and not independently peer-reviewed. Parent/teacher review, physical bypass testing and child-safety evaluation remain outstanding.
