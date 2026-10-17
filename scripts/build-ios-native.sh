#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
sdk=${1:-iphonesimulator}
if [ "$sdk" = iphoneos ]; then arch=arm64; else arch=$(uname -m); fi
build="$root/build/ios-$sdk"
cmake -S "$root/shared" -B "$build" -G Ninja \
  -DCMAKE_BUILD_TYPE=Release -DCMAKE_SYSTEM_NAME=iOS \
  -DCMAKE_OSX_SYSROOT="$sdk" -DCMAKE_OSX_ARCHITECTURES="$arch" \
  -DCMAKE_OSX_DEPLOYMENT_TARGET=17.0 -DCMAKE_XCODE_ATTRIBUTE_CODE_SIGNING_ALLOWED=NO
cmake --build "$build" --target sf_engine --parallel 2
framework=$(find "$build" -type d -name sf_engine.framework | head -n 1)
test -n "$framework"
mkdir -p "$root/ios/NativeBuild"
rm -rf "$root/ios/NativeBuild/sf_engine.framework"
cp -R "$framework" "$root/ios/NativeBuild/"
mkdir -p "$root/ios/NativeBuild/sf_engine.framework/Modules"
printf '%s\n' 'framework module sf_engine { umbrella header "sf_engine.h" export * module * { export * } }' > "$root/ios/NativeBuild/sf_engine.framework/Modules/module.modulemap"
install_name_tool -id '@rpath/sf_engine.framework/sf_engine' "$root/ios/NativeBuild/sf_engine.framework/sf_engine"
