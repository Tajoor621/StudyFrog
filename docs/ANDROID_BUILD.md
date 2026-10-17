# Free local Android AAB → split APK workflow

## Prerequisites

Use Android Studio's free SDK Manager to install Android SDK 35,
Build Tools 35.0.0, NDK 27.2.12479018 and CMake 3.22.1. Use JDK 17.
The official Gradle 8.9 wrapper JAR is bundled. Its first run downloads Gradle;
initial dependency resolution also needs internet. Cached builds can use
`./gradlew --offline :app:bundleRelease` after the first successful build.
No Google Play Console or paid distribution service is involved.

```bash
# In the StudyForge project directory:
export ANDROID_HOME="$HOME/Android/Sdk"  # adjust to your SDK location
export PATH="$ANDROID_HOME/platform-tools:$PATH"
chmod +x gradlew
./gradlew --no-daemon :app:bundleRelease
# Result: app/build/outputs/bundle/release/app-release.aab

# Obtain the pinned free bundletool release:
curl -fL https://github.com/google/bundletool/releases/download/1.18.2/bundletool-all-1.18.2.jar -o bundletool-all-1.18.2.jar
export BUNDLETOOL_JAR="$PWD/bundletool-all-1.18.2.jar"
```

The release AAB is unsigned intentionally. Bundletool signs its generated APKs
with your own key; signing the AAB is unnecessary for this local route.
Keep the same key for subsequent sideload updates. Never commit it or its password.

```bash
# Interactive; choose your own passwords when prompted.
keytool -genkeypair -keystore studyforge-local.jks -alias studyforge \
  -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=StudyForge Local'
export STUDYFORGE_KEYSTORE="$PWD/studyforge-local.jks"
export STUDYFORGE_KEY_ALIAS=studyforge
read -rsp 'Keystore password: ' STUDYFORGE_STORE_PASSWORD
export STUDYFORGE_STORE_PASSWORD
read -rsp 'Key password: ' STUDYFORGE_KEY_PASSWORD
export STUDYFORGE_KEY_PASSWORD

umask 077
password_dir=$(mktemp -d)
trap 'rm -rf "$password_dir"' EXIT
printf '%s' "$STUDYFORGE_STORE_PASSWORD" > "$password_dir/store"
printf '%s' "$STUDYFORGE_KEY_PASSWORD" > "$password_dir/key"
java -jar "$BUNDLETOOL_JAR" build-apks \
  --bundle=app/build/outputs/bundle/release/app-release.aab \
  --output=app.apks --mode=default --overwrite \
  --ks="$STUDYFORGE_KEYSTORE" --ks-key-alias="$STUDYFORGE_KEY_ALIAS" \
  --ks-pass="file:$password_dir/store" --key-pass="file:$password_dir/key"
unzip app.apks -d apks
python3 scripts/verify_splits.py apks \
  --aapt2 "$ANDROID_HOME/build-tools/35.0.0/aapt2"
```

Alternatively, after setting those variables: `bash scripts/build-apks.sh`.
It builds, extracts and asserts the actual compiled manifest attributes.

## Requested verification example and the filename distinction

If `bundletool` is an alias for `java -jar /absolute/path/bundletool.jar`,
the requested minimal example is:

```bash
bundletool build-apks --bundle=app-release.aab --output=app.apks --mode=default
unzip app.apks -d apks
aapt2 dump xmltree apks/splits/config.xxhdpi.apk --file AndroidManifest.xml
```

This is an illustrative example, not a reliable literal path: bundletool
normally emits `apks/splits/base-xxhdpi.apk`. The manifest's `split` attribute
is `config.xxhdpi`; the file itself need not have that name. The minimal
command also relies on bundletool's default debug signing key. Prefer the
explicit signing command above.

For the usual output filenames, the working inspection commands are:

```bash
"$ANDROID_HOME/build-tools/35.0.0/aapt2" dump xmltree \
  apks/splits/base-xxhdpi.apk --file AndroidManifest.xml
"$ANDROID_HOME/build-tools/35.0.0/aapt2" dump xmltree \
  apks/splits/base-master.apk --file AndroidManifest.xml
```

Look for these logical values (aapt2 prints booleans as literals or typed hexadecimal):

| Location | Attribute | Required value |
| --- | --- | --- |
| density manifest | split | config.xxhdpi |
| density manifest | splitTypes | base__density |
| density application | android:hasCode | false / 0x0 |
| base application | android:extractNativeLibs | true / 0xffffffff |

Do not expect extractNativeLibs in the resource-only density manifest.
No split attributes or Play-derived metadata are set in source. If the
assertions fail, the packaging requirement has not passed; keep the output
for inspection rather than editing generated manifests or bypassing checks.

## Install on a physical Android device or emulator

Enable USB debugging and authorize your computer. Install the device-matched
selection from the APK set, including base, ABI and density resources:

```bash
adb devices
java -jar "$BUNDLETOOL_JAR" install-apks --apks=app.apks
adb shell am start -n com.scenio.mth/com.scenio.mth.features.FeaturesActivity
# Optional offline local engine over USB:
adb reverse tcp:8765 tcp:8765
```

For a manual selection (arm64-v8a, xxhdpi only), inspect actual paths first:

```bash
adb install-multiple -r apks/splits/base-master.apk \
  apks/splits/base-arm64_v8a.apk apks/splits/base-xxhdpi.apk
```

Prefer `install-apks`; arbitrary split selection can miss required variants.
The build supports arm64 phones and x86_64 emulators. ARMv7 is not included.

## Why these settings matter

An AAB is a packaging input, not directly installable. Bundletool produces
base plus configuration APKs locally. Density configurations deliver suitable
image resources without code; the one xxhdpi-only fixture is used by an
ImageView in MainActivity. MainActivity also displays a string from JNI.

`extractNativeLibs=true` makes the installer extract the native `.so` file
for loading. `packaging.jniLibs.useLegacyPackaging=true` configures legacy
compressed JNI packaging; the same setting initializes bundle-native packaging for this pinned AGP. The native CMake target and
System.loadLibrary call make this behavior observable. NDK 27's flexible
page-size option is enabled, but 16 KB runtime device verification is pending.

The included GitHub Actions workflow uses an ephemeral test keystore, builds
the AAB, generates APKs with bundletool, and fails if manifest checks fail.
It is for testing, not stable update signing. GitHub free-tier quotas are
provider-controlled; the local workflow remains available without Actions.
