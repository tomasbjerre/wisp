#!/usr/bin/env bash
# Feeds the emulator the real recorded walk from issue #121 (the same data
# ScreenshotTest seeds Detail/Home with), one GPS fix every 2 seconds — about the
# spacing the recording itself had — so live Tracking shows a real route and
# realistic speed instead of a synthetic straight line.
#
#   scripts/replay-real-track.sh [--first-fix-only] [max-points] [stop-file]
#
# --first-fix-only just sets the starting position and returns (so Tracking has
# somewhere to be instead of "Finding your location…"). Otherwise it replays up to
# max-points (default 200) fixes, stopping early once stop-file (default
# /tmp/wisp-walk-stop) exists.
set -u
csv="$(dirname "$0")/../app/src/androidTest/assets/real-running-activity-track-points.csv"
# Columns: session_started_at,timestamp,latitude,longitude,speed_kmh. Note that
# `adb emu geo fix` takes longitude first.
fixes() { tail -n +2 "$csv" | awk -F, '{print $4, $3}'; }

if [ "${1:-}" = "--first-fix-only" ]; then
  adb emu geo fix $(fixes | head -n 1)
  exit 0
fi

max="${1:-200}"
stop="${2:-/tmp/wisp-walk-stop}"
total=$(fixes | head -n "$max" | wc -l)
# `adb emu geo fix`'s own output is a bare "OK" per call, with nothing to tell one
# of ~200 identical lines apart from the next — printed a CI run's own progress
# (point count, coordinates) instead, so a slow or stuck run is visible from the
# log alone rather than looking like a long silent hang.
i=0
fixes | head -n "$max" | while read -r lon lat; do
  [ -f "$stop" ] && exit 0
  i=$((i + 1))
  echo "[replay-real-track] point $i/$total: lon=$lon lat=$lat"
  adb emu geo fix "$lon" "$lat"
  sleep 2
done
