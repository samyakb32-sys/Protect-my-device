#!/usr/bin/env bash
# Exact per-part battery use for this app, straight from Android's own battery stats.
#
# Needs: adb, and a phone with USB debugging on, plugged in and authorised. Nothing is installed on it.
#
#   scripts/battery-report.sh            # since the phone was last fully charged
#   scripts/battery-report.sh --reset    # zero the phone's battery stats now; unplug, use it, run again later
#   scripts/battery-report.sh --raw      # also print Android's own line for this app, unparsed
#   scripts/battery-report.sh --bugreport  # save a bug report to load into Battery Historian
#
# Options: -s SERIAL (or ANDROID_SERIAL) picks a phone when several are connected; -p PACKAGE checks
# another build (default com.protectmydevice). --from-file FILE parses saved `dumpsys batterystats`
# output instead of asking a phone (used to test this script).
#
# The numbers are Android's estimate in mAh, from the phone's power profile, since the last full charge
# (or the last --reset). Charging time doesn't count. They're as close as you can get without lab gear,
# and only cover this app's uid.
set -euo pipefail

PKG=com.protectmydevice
SERIAL=${ANDROID_SERIAL:-}
MODE=report
RAW=0
FROM_FILE=

while [[ $# -gt 0 ]]; do
  case "$1" in
    -p) PKG=$2; shift 2 ;;
    -s) SERIAL=$2; shift 2 ;;
    --reset) MODE=reset; shift ;;
    --bugreport) MODE=bugreport; shift ;;
    --raw) RAW=1; shift ;;
    --from-file) FROM_FILE=$2; shift 2 ;;
    -h|--help) sed -n '2,17p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown option: $1 (try --help)" >&2; exit 2 ;;
  esac
done

die() { echo "battery-report: $*" >&2; exit 1; }

adb_() { if [[ -n "$SERIAL" ]]; then adb -s "$SERIAL" "$@"; else adb "$@"; fi; }

# u0a234-style name that batterystats uses for an app uid (user 0, app id 10234).
uid_name() {
  local uid=$1 user=$(( $1 / 100000 )) app=$(( $1 % 100000 ))
  if (( app >= 10000 )); then echo "u${user}a$(( app - 10000 ))"; else echo "$uid"; fi
}

if [[ -z "$FROM_FILE" ]]; then
  command -v adb >/dev/null || die "adb not found. install android platform-tools and put adb on your PATH."
  state=$(adb_ get-state 2>/dev/null || true)
  [[ "$state" == device ]] || die "no phone ready. plug it in, turn on USB debugging, accept the prompt, then check 'adb devices'. with several phones, pass -s SERIAL."

  case "$MODE" in
    reset)
      adb_ shell dumpsys batterystats --reset >/dev/null
      echo "battery stats reset. unplug the phone, use it as normal (a few hours or a day), then run this again."
      exit 0 ;;
    bugreport)
      out="bugreport-$(date +%Y%m%d-%H%M).zip"
      echo "saving $out (takes a minute or two)…"
      adb_ bugreport "$out"
      echo "done. load $out into Battery Historian (github.com/google/battery-historian) and pick $PKG."
      exit 0 ;;
  esac

  uid=$(adb_ shell cmd package list packages -U "$PKG" 2>/dev/null | tr -d '\r' | grep -E "^package:$PKG uid:" | sed -E 's/.* uid:([0-9]+).*/\1/' | head -1)
  if [[ -z "$uid" ]]; then
    uid=$(adb_ shell dumpsys package "$PKG" 2>/dev/null | tr -d '\r' | grep -m1 -oE 'userId=[0-9]+' | cut -d= -f2 || true)
  fi
  [[ -n "$uid" ]] || die "$PKG isn't installed on this phone (use -p for another package name)."
  name=$(uid_name "$uid")
  stats=$(adb_ shell dumpsys batterystats --charged | tr -d '\r')
else
  [[ -r "$FROM_FILE" ]] || die "can't read $FROM_FILE"
  # Offline: there is no phone to ask for the uid, so it has to be given.
  name=${BATTERY_UID:-}
  stats=$(tr -d '\r' < "$FROM_FILE")
  [[ -n "$name" ]] || die "with --from-file, set BATTERY_UID (e.g. BATTERY_UID=u0a234)"
  uid=$name
fi

# Android 12+:  "UID u0a234: 1.23 fg: 0.4 bg: 0.8 ( cpu=0.9 (3m 2s) cpu:fg=0.3 wifi=0.1 gps=0.2 )"
# Android 9-11: "Uid u0a234: 1.23 ( cpu=0.9 wake=0.01 radio=0.2 wifi=0.1 gps=0.2 ) Including smearing: …"
line=$(printf '%s\n' "$stats" | awk -v want="$name" '
  /Estimated power use/ { inside = 1; next }
  inside && tolower($1) == "uid" && $2 == want ":" { print; exit }
  inside && /^  [^ ]/ { inside = 0 }   # next top-level section
')

if [[ -z "$line" ]]; then
  echo "$PKG ($name): Android has no battery use on record for it since the last full charge."
  echo "that's normal right after charging or a --reset. use the phone unplugged for a while and try again."
  exit 0
fi

total=$(printf '%s\n' "$line" | sed -E 's/^[[:space:]]*[Uu][Ii][Dd] [^:]+: ([0-9.]+).*/\1/')
capacity=$(printf '%s\n' "$stats" | grep -m1 -oE 'Capacity: [0-9]+' | grep -oE '[0-9]+' || true)

label() {
  case "$1" in
    cpu) echo "processor" ;;
    gps|gnss) echo "location (gps)" ;;
    wifi|wifi_scan) echo "wifi" ;;
    radio|mobile_radio|cell|phone) echo "mobile data" ;;
    bluetooth|ble_scan) echo "bluetooth" ;;
    camera) echo "camera" ;;
    flashlight) echo "flashlight" ;;
    audio) echo "sound" ;;
    video) echo "video" ;;
    wake|wakelock) echo "kept awake (wakelocks)" ;;
    sensor|sensors) echo "other sensors" ;;
    screen) echo "screen" ;;
    *) echo "$1" ;;
  esac
}

echo "$PKG ($name), since the last full charge:"
printf '  %-26s %8s mAh\n' "total" "$total"
if [[ -n "$capacity" && "$capacity" != 0 ]]; then
  awk -v t="$total" -v c="$capacity" 'BEGIN { printf "  %-26s %8.2f %%\n", "share of a full battery", t / c * 100 }'
fi
echo "  by part:"
# Drop nested "(3m 2s)" durations and fg/bg splits ("cpu:fg="), keep "name=number" pairs, biggest first.
printf '%s\n' "$line" | sed -E 's/\([0-9][^()]*\)//g; s/Including smearing.*//' \
  | grep -oE '(^|[ (])[a-z_]+=[0-9.]+' | sed -E 's/^[ (]//' \
  | awk -F= '$2 > 0' | sort -t= -k2 -gr \
  | while IFS== read -r part mah; do printf '    %-24s %8s mAh\n' "$(label "$part")" "$mah"; done

if (( RAW )); then echo; echo "android's own line:"; echo "$line"; fi
