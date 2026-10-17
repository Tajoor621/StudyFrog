#!/usr/bin/env bash
set -euo pipefail
: "${BUNDLETOOL_JAR:?Set BUNDLETOOL_JAR to bundletool-all-1.18.2.jar}"
: "${ANDROID_HOME:?Set ANDROID_HOME to your SDK directory}"
: "${STUDYFORGE_KEYSTORE:?Set STUDYFORGE_KEYSTORE to your local signing keystore}"
: "${STUDYFORGE_KEY_ALIAS:?Set STUDYFORGE_KEY_ALIAS}"
: "${STUDYFORGE_STORE_PASSWORD:?Set STUDYFORGE_STORE_PASSWORD}"
: "${STUDYFORGE_KEY_PASSWORD:?Set STUDYFORGE_KEY_PASSWORD}"
./gradlew --no-daemon :app:bundleRelease
java -jar "$BUNDLETOOL_JAR" build-apks \
  --bundle=app/build/outputs/bundle/release/app-release.aab \
  --output=app.apks --mode=default --overwrite \
  --ks="$STUDYFORGE_KEYSTORE" --ks-key-alias="$STUDYFORGE_KEY_ALIAS" \
  --ks-pass=env:STUDYFORGE_STORE_PASSWORD --key-pass=env:STUDYFORGE_KEY_PASSWORD
rm -rf apks
unzip -q app.apks -d apks
python3 scripts/verify_splits.py apks --aapt2 "$ANDROID_HOME/build-tools/35.0.0/aapt2"
