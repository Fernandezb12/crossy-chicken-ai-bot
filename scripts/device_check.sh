#!/usr/bin/env bash
set -euo pipefail
# Read-only diagnostics plus explicitly requested APK installation and app launch.
# Usage: scripts/device_check.sh [path/to/app-debug.apk]
adb get-state
adb shell getprop ro.product.model
adb shell getprop ro.build.version.sdk
adb shell wm size
adb shell wm density
if [[ $# -gt 0 ]]; then
  adb install -r "$1"
  adb shell am start -W -n com.crossybot/.ui.MainActivity
fi
adb shell dumpsys package com.crossybot | sed -n '/requested permissions:/,/User 0:/p'
