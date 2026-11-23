#!/bin/bash
# Takes the store screenshots (metadata/en-US/images/phoneScreenshots) on a
# running emulator with a DEBUG build of OneBit installed (the debug adb extras
# set presets and icon styles; they are saved like a user change).
#
#   tools/store_screenshots.sh <output dir> [package to hide ...]
#
# Packages given after the output dir are disabled while shooting (private apps
# that must not show in public screenshots) and enabled again at the end. The
# home app is saved first and restored, because disabling the home app clears it.
set -e
export PATH=$PATH:~/Android/Sdk/platform-tools
OUT=$1; shift
HIDE=("$@")
A=com.onebitlauncher.app/com.asnidev.onebit.MainActivity
mkdir -p "$OUT"

HOME_APP=$(adb shell cmd role get-role-holders android.app.role.HOME | tr -d '\r')
restore() {
    d exit || true
    for p in "${HIDE[@]}"; do adb shell pm enable --user 0 "$p" >/dev/null || true; done
    [ -n "$HOME_APP" ] && adb shell cmd role add-role-holder android.app.role.HOME "$HOME_APP" 0 || true
}
trap restore EXIT
for p in "${HIDE[@]}"; do adb shell pm disable-user --user 0 "$p" >/dev/null; done

# Clean status bar (demo mode); the clock follows the real time, like the menu bar clock.
adb shell settings put global sysui_demo_allowed 1
d() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
d enter; d battery -e level 100 -e plugged false; d network -e wifi show -e level 4 -e mobile hide; d notifications -e visible false

adb shell am force-stop com.onebitlauncher.app
# adb shell joins its arguments, so names with a space need inner quotes.
start() { adb shell am start -n $A "$@" >/dev/null 2>&1; sleep 4; }
shot() { d clock -e hhmm "$(adb shell date +%H%M | tr -d '\r')"; sleep 1; adb exec-out screencap -p > "$OUT/$1.png"; }

start --es preset "'Green desk'" --es icons dither; adb shell input keyevent BACK; start; shot 01_green_desk
start --es preset "Amber" --es icons threshold; shot 02_amber_line_art
start --es preset "Night" --es icons mono; shot 03_night_silhouettes
start --es preset "'Gray desk'" --es icons dither --ez drawer true; shot 04_app_list
adb shell input keyevent BACK; sleep 1
start --es preset "Cream" --ez options true; shot 05_options
adb shell input keyevent BACK; sleep 1
start --es preset "'Blue screen'" --es icons colors16; shot 06_blue_16_colours
start --es preset "Phosphor" --es icons grain; shot 07_phosphor_grain
start --es preset "'Green desk'" --es icons dither
echo "screenshots in $OUT"
