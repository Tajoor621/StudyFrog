> Reference document from the feature integration package. Its source additions are applied in this repository; the root README and build files describe the integrated app.

# Integration contract

This package is an implementation reference and source overlay for an existing
Android Studio project. It is not a replacement for the original app and does
not contain its generation engine, native libraries, signing key or Firebase
configuration. The actual com.scenio.mth repository was not supplied.
No files were pushed to GitHub, and no Android build is claimed.

Assumptions: app module :app, namespace/applicationId com.scenio.mth,
original studio activity com.scenio.mth.MainActivity, minSdk 24, compile/target
35, JDK 17, compatible Gradle 8.9 / AGP 8.7.3 / Kotlin 2.0.21 baseline.
If your project already uses newer supported SDK/build versions, preserve them
and resolve the dependency set in its actual toolchain instead of downgrading.
Progress/preferences introduced here use distinct storage names and do not
migrate or overwrite existing app data.

1. Copy app/src/main/java/com/scenio/mth/features and sf_* resources into the
   app module. Copy src/test and src/androidTest additions alongside existing tests.
2. Merge integration/root-build.gradle.kts into the root plugin declarations.
   Merge integration/app-build.gradle.kts into the app build script. These are
   merge inputs, not scripts to apply via apply(from). Keep your original
   versionCode/versionName, repositories, native build options, density/ABI
   splitting, CMake/NDK version and other dependencies.
3. Merge integration/gradle.properties. Do not discard other properties.
   The reference baseline requires Gradle 8.9 and Google/Maven Central repositories.
4. Merge manifest-additions.xml rather than replacing the manifest. Move the
   MAIN/LAUNCHER intent-filter from the original MainActivity to FeaturesActivity
   so there is one launcher. Keep original deep-link/file-intent declarations.
   Keep MainActivity registered. FeaturesActivity launches that exact class.
5. If you already have an Application subclass, KEEP it. Add
   `FeatureRuntime.start(this)` to its onCreate after super.onCreate and do not
   set android:name to StudyForgeApplication. Do not create a second Application.
   Audit existing Firebase initialization calls: the new opt-in flow must be the
   only owner of Firebase startup/consent. Other Firebase products that need early
   initialization require coordinated integration, not this provider-removal setup.
6. If existing backup rules exist, merge sf exclusions into those rules instead
   of replacing them or attaching competing rule files. Exclude new database and
   preferences from both cloud backup and device transfer so consent does not
   silently migrate to another device. Preserve existing allowBackup=false if set.
7. Merge gitignore-additions. Keep the existing native libraries and generated
   bundle metadata. Do not add com.android.vending.derived.apk.id to source or
   invent values; no feature depends on it. No split fields are set manually.
8. Add .github/workflows/android-features.yml without deleting existing workflows.
   Its SDK/NDK install list is the reference baseline: align its NDK/CMake pins
   with your actual existing native build if they differ.
9. Build, inspect, then commit generated app/schemas files for future Room
   migration tests. No destructive Room fallback is enabled. Upgrade the new
   database with explicit migrations when its version changes.
10. Call ProgressRepository.recordStep from actual committed engine/learning
    progress events using stable, versioned project IDs. Onboarding records one
    actual setup-completion step. This overlay cannot hook unseen generator code
    automatically. The repository is coroutine-safe and preserves the greatest
    completed step in a transaction.

The new theme applies to the feature screens. To theme the existing studio,
wrap its Compose content in StudyForgeTheme using the same SettingsStore flow.
A legacy View-based studio needs its own AppCompat/Material theme integration;
these files do not claim to recolor an unseen native drawing canvas.

Signing: a self-generated key is suitable for a new clean sideload installation.
Updating an existing installation requires its existing signing identity (and
higher versionCode). A Play-derived metadata value does not grant access to a
Play app-signing key. Do not uninstall an existing app before exporting its data.
This package never automatically uninstalls or clears user data.
