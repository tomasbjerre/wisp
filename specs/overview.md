# Overview

## Goal

Wisp lets a person record a GPS track of any outdoor activity — running,
cycling, walking, or anything else that moves them from one place to
another — see their current speed while moving, and review distance/route/
speed for past activities later. The app doesn't care what you're doing,
only that you're moving. It should be trivial to start: open the app, tap
start, go.

## In scope

- Start/pause/stop recording a GPS track.
- Show current speed, elapsed distance, and elapsed time live while
  recording.
- Draw the recorded route on a map, live and in history.
- Persist every recorded session locally on the device.
- Browse a list of past sessions and open one to see its route, distance,
  duration, and average/max speed.
- Delete a past session.
- Export the full history as a CSV file (see
  [Export](export.md)), so it can be opened in a spreadsheet.

## Explicitly out of scope (for now)

- Classifying or asking the user to pick an activity type. Wisp tracks
  *movement*, not sport-specific metrics — no cadence, heart rate,
  elevation gain targets, laps, or training plans, and no per-activity
  configuration of any kind.
- Social features, accounts, sync, or cloud backup.
- Turn-by-turn navigation or route planning.
- Multi-device sync. History lives on the device that recorded it.

If a future need arises for any of the above, it should be added as a new
spec document rather than bent into the existing ones.

## Design principle

Simplicity beats configurability. When a decision could be a user-facing
setting or a fixed sane default, prefer the fixed default.
