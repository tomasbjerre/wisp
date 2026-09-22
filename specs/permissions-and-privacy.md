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
- No analytics or crash reporting that transmits location data.
- Deleting a session (see [UI Flows](ui-flows.md)) must remove its points
  too — no orphaned data left behind.
