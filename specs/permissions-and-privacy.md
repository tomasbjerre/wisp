# Permissions & Privacy

## Required access

- **Precise/fine location**, while the app is in use, to record a track at
  all.
- **Background location** ("allow all the time"), because recording must
  continue with the screen off or the app backgrounded (see
  [Tracking](tracking.md)). Request this only when the user starts a
  recording, not at first launch, and explain why before the platform
  permission prompt appears.
- A way to keep the OS from killing recording while backgrounded (e.g. a
  foreground/long-running-task mechanism with a persistent, low-noise
  notification showing "Recording — <distance> · <time>"). The user should
  be able to tap that notification to return to the Tracking screen.
- Notification permission, on platforms that require it to show that
  in-progress-recording indicator.
- An exemption from the platform's battery optimization for the app, since
  aggressive battery management is a common real-world cause of
  background recording being paused or killed even with the above in
  place (e.g. phone in a pocket, screen off). Offer this from the
  Tracking screen when it isn't already granted (see
  [UI Flows](ui-flows.md#2-tracking-active-recording)); this is advisory,
  not a permission — recording still works if the user declines.

## Denied or restricted permission

- If location permission is denied, the Tracking screen must say so
  clearly and offer a direct way to grant it (deep link to app settings if
  the platform requires that after a permanent denial). It must not
  silently show an empty map or zero stats.
- If background location specifically is unavailable (only "while using
  the app" was granted), recording should still work while the app is in
  the foreground, but the user should be told recording may stop if they
  leave the app.

## Data handling

- All data (sessions, points) stays on-device. Nothing is uploaded anywhere
  by default — there is no backend in scope for Wisp (see
  [Overview](overview.md)).
- One exception: looking up the nearest city name for a finished session
  (see [Data Model](data-model.md#session)) sends that session's start
  coordinates to the platform's geocoding service, which on most devices
  means a Google server. This is best-effort — if it fails (no network, no
  geocoding backend on the device) the session simply has no city name,
  nothing else about it is affected.
- No analytics or crash reporting that transmits location data.
- Deleting a session (see [UI Flows](ui-flows.md)) must remove its points
  too — no orphaned data left behind.
