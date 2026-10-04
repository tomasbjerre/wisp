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
  - The notification shows the distance in the user's chosen unit (see
    [Units](units.md)) and the elapsed time, followed by "paused" or
    "waiting to move" while the session is in either state. It stays until
    the recording stops.
  - Tapping it opens [Tracking](ui-flows.md#2-tracking-active-recording)
    on the session being recorded — not Home — whatever the app was
    showing, or whether it was in the background.
- Notification permission, on platforms that require it to show that
  in-progress-recording indicator. Without it the notification is not
  shown, so it is requested together with the location access when the
  user starts a recording — and, for a user who granted location before
  this was asked for, the next time [Tracking](ui-flows.md#2-tracking-active-recording)
  opens. It is advisory: recording works without it.
- An exemption from the platform's battery optimization for the app, since
  aggressive battery management is a common real-world cause of
  background recording being paused or killed even with the above in
  place (e.g. phone in a pocket, screen off). Offer this from the
  Tracking screen when it isn't already granted (see
  [UI Flows](ui-flows.md#2-tracking-active-recording)); this is advisory,
  not a permission — recording still works if the user declines.
- **Activity recognition** (required by the platform, on versions that
  require it, to read the device's step-count sensor), requested
  opportunistically alongside location when a recording starts. Unlike
  location, this is never required for recording to work: if declined, or
  the device has no step-count sensor, that session's step count is
  simply 0 (see [Tracking](tracking.md#step-count)) — no explanation, no
  retry prompt, nothing blocking.

- **Bluetooth** (nearby devices scan and connect, on versions of Android
  that require them), to find and read a BLE heart rate monitor. Requested
  only when the user turns on the Heart rate monitor setting on Tracking (see
  [Heart rate](heart-rate.md#setting)) — never at first launch. If
  declined, the setting stays off and nothing else is affected.

- **Vibration**, to pulse the [paused session reminder](tracking.md#paused-session-reminder).
  No runtime request and nothing sensitive: it reads nothing from the device, transmits
  nothing, and is switched off entirely by the user's own setting on
  [Settings](ui-flows.md#2a-settings).

## Denied or restricted permission

- If location permission is denied, the Tracking screen must say so
  clearly and offer a direct way to grant it (deep link to app settings if
  the platform requires that after a permanent denial). It must not
  silently show an empty map or zero stats.
- If background location specifically is unavailable (only "while using
  the app" was granted), recording should still work while the app is in
  the foreground, but the user should be told recording may stop if they
  leave the app, and offered a direct way to fix it — a deep link to the
  app's own settings, since the platform's runtime permission dialog
  generally won't ask for background location a second time once denied.

## Data handling

- All data (sessions, points) stays on-device. Nothing is uploaded anywhere
  by default — there is no backend in scope for Wisp (see
  [Overview](overview.md)).
- One exception: looking up the place name for a finished session
  (see [Data Model](data-model.md#place-name)) sends that session's start
  coordinates to the platform's geocoding service, which on most devices
  means a Google server. This is best-effort — if it fails (no network, no
  geocoding backend on the device) the session simply has no place name,
  nothing else about it is affected. No other geocoding service or API is
  added to make up for that.
- Location keeps being read for as long as a recording is open, including
  while it's paused (see [Tracking](tracking.md#session-lifecycle)), so
  that what happened during a pause isn't lost and an automatic pause can
  end by itself. Those fixes are stored on the device like any other and
  are excluded from the session's stats.
- No analytics or crash reporting that transmits location data.
- Deleting a session (see [UI Flows](ui-flows.md)) must remove its points
  too — no orphaned data left behind.

## Privacy policy

- Wisp must publish a privacy policy (`PRIVACY.md` at the repo root)
  describing what data is accessed, where it stays, and the one case
  where data leaves the device (reverse geocoding, above). App stores
  (e.g. Google Play) require a URL to this document before they'll
  publish a listing that requests sensitive permissions.
- Whenever a change to this spec's "Required access" or "Data handling"
  sections changes what the app actually does with user data,
  `PRIVACY.md` must be updated in the same change — it must never
  describe behavior the app no longer has, or omit behavior it has
  gained.
