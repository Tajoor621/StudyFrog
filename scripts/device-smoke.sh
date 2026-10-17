#!/usr/bin/env bash
set -euo pipefail
# Read-only device diagnostics; never uninstalls the app or clears user data.
adb get-state
adb shell getprop ro.product.model
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.cpu.abilist
adb shell am start -W -n com.scenio.mth/com.scenio.mth.features.FeaturesActivity
adb shell dumpsys meminfo com.scenio.mth
adb logcat -d -t 300 AndroidRuntime:E '*:S'
printf '%s\n' 'Import your licensed SD 1.x GGUF, generate once, then use Export device generation benchmark in the studio.'
printf '%s\n' 'A successful emulator build is not a physical-device inference benchmark.'
