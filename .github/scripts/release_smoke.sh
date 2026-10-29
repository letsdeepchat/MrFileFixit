#!/usr/bin/env bash
# Installs the minified release APK on the running emulator, opens every main tab and Settings, and fails if the
# app crashes or dies. Catches R8 keep-rule problems (serialization routes, PDFBox, ML Kit) that debug tests miss.
set -euo pipefail
apk="$1"
pkg=com.mrfilefixit.app

adb install -r "$apk"
adb logcat -c
adb shell am start -W -n "$pkg/.MainActivity"
sleep 5

tap() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null
  bounds=$(adb shell cat /sdcard/ui.xml | sed 's/>/>\n/g' | grep "resource-id=\"$1\"" | head -n 1 |
    sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' || true)
  if [ -z "$bounds" ]; then
    echo "::error::Release smoke test: '$1' not found on screen"
    exit 1
  fi
  read -r x1 y1 x2 y2 <<< "$bounds"
  adb shell input tap $(((x1 + x2) / 2)) $(((y1 + y2) / 2))
  sleep 3
}

alive() {
  if ! adb shell pidof "$pkg" > /dev/null; then
    echo "::error::Release build is no longer running after: $1"
    adb logcat -d -b crash || true
    exit 1
  fi
}

alive "launch"
for tab in nav_exam nav_pdf nav_image nav_scan nav_resume nav_home; do
  tap "$tab"
  alive "$tab"
done
tap home_settings
alive "settings"

if adb logcat -d -b crash | grep -q "$pkg"; then
  echo "::error::Release build logged a crash"
  adb logcat -d -b crash
  exit 1
fi
echo "Release build launched and opened every tab without crashing."
