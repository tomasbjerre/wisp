# Wisp — Privacy Policy

_Last updated: 2026-09-24_

Wisp is a GPS tracker for any activity — running, cycling, walking, or
anything else. This page explains what data it accesses and what happens
to it. It exists to satisfy Google Play's privacy policy requirement and
must stay in sync with [`specs/permissions-and-privacy.md`](specs/permissions-and-privacy.md),
which is the source of truth for Wisp's data-handling behavior.

## Data Wisp accesses

- **Precise location**, while you're recording an activity, to draw your
  route.
- **Background location** ("allow all the time"), only while a recording
  is active, so tracking continues with the screen off or the app in the
  background.
- The session data this produces: your route (a series of GPS points),
  distance, speed, duration, and timestamps.
- **Step count**, from your device's step-count sensor, only while a
  recording is active. Not every device has this sensor, and you can
  decline the permission it needs on versions of Android that require
  one — either way, recording still works, that session just has no step
  count.
- **Heart rate**, only if you turn on the Heart rate monitor setting, from
  a Bluetooth heart rate monitor you own, only while a recording is
  active. This needs Bluetooth access, which Wisp asks for only when you
  turn the setting on. Without a monitor or the permission, recording
  still works, that session just has no heart rate.

## Where your data goes

- Everything above stays **on your device**, in a local database. Wisp has
  no backend, no server, and no account system — there is nothing for it
  to upload data to, even if it wanted to.
- One exception: when a recording finishes, Wisp sends that session's
  start coordinates to your device's built-in geocoding service (on most
  Android devices, a Google service) to look up a city name to label the
  session with. This is best-effort — if it fails, the session just has no
  city name. That request is handled by the platform, under its own
  privacy policy, not by Wisp.
- Wisp does not use analytics, crash reporting, or advertising SDKs of any
  kind, and does not sell or share your data with anyone.

## Your controls

- Deleting a session in the app removes its route data immediately —
  nothing orphaned is left behind.
- Uninstalling Wisp removes all of its local data from your device.

## Changes

If what Wisp collects or does with it ever changes, this page will be
updated in the same change that changes the behavior (see
[`AGENTS.md`](AGENTS.md)'s "specs first" rule) — it will never describe
behavior the app doesn't actually have.

## Contact

Questions about this policy or Wisp's data handling: open an issue at
<https://github.com/tomasbjerre/wisp/issues>.
