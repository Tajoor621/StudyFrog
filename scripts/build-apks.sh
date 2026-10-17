#!/usr/bin/env bash
set -euo pipefail
: "${BUNDLETOOL_JAR:?Set BUNDLETOOL_JAR to bundletool-all-1.18.2.jar}"
: "${ANDROID_HOME:?Set ANDROID_HOME to your SDK directory}"
: "${STUDYFORGE_KEYSTORE:?Set STUDYFORGE_KEYSTORE to your local signing keystore}"
: "${STUDYFORGE_KEY_ALIAS:?Set STUDYFORGE_KEY_ALIAS}"
: "${STUDYFORGE_STORE_PASSWORD:?Set STUDYFORGE_STORE_PASSWORD}"
: "${STUDYFORGE_KEY_PASSWORD:?Set STUDYFORGE_KEY_PASSWORD}"
./gradlew --no-daemon :app:bundleRelease
# bundletool supports pass: and file: (not apksigner's env: syntax).
# Restrict permissions and avoid placing passwords in process arguments.
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
rm -rf apks
unzip -q app.apks -d apks
python3 scripts/verify_splits.py apks --aapt2 "$ANDROID_HOME/build-tools/35.0.0/aapt2"
