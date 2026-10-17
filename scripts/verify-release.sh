#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_HOME:?Set ANDROID_HOME to the installed Android SDK}"
: "${BUNDLETOOL_JAR:?Set BUNDLETOOL_JAR to bundletool-all-1.18.2.jar}"
: "${SF_KEYSTORE_PATH:?Set SF_KEYSTORE_PATH}"
: "${SF_KEY_ALIAS:?Set SF_KEY_ALIAS}"
: "${SF_STORE_PASSWORD:?Set SF_STORE_PASSWORD}"
: "${SF_KEY_PASSWORD:?Set SF_KEY_PASSWORD}"
java -jar "$BUNDLETOOL_JAR" build-apks   --bundle=app/build/outputs/bundle/release/app-release.aab   --output=studyforge.apks --mode=default --overwrite   --ks="$SF_KEYSTORE_PATH" --ks-key-alias="$SF_KEY_ALIAS"   --ks-pass=env:SF_STORE_PASSWORD --key-pass=env:SF_KEY_PASSWORD
unzip -q -o studyforge.apks -d split-inspection
find split-inspection -name '*.apk' -print
"$ANDROID_HOME/build-tools/35.0.0/aapt2" dump xmltree   split-inspection/splits/base-master.apk --file AndroidManifest.xml
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs   app/build/outputs/apk/release/app-release.apk
java -jar "$BUNDLETOOL_JAR" install-apks --apks=studyforge.apks
