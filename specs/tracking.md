# Tracking

## Session lifecycle

A **session** is one continuous recording, from tap-to-start to tap-to-stop.

States: `idle → recording → (paused ⇄ recording) → stopped`.

- **Start**: begins acquiring location and appending points to the session.
  A session is created as soon as start is tapped, even before the first
  fix arrives, so nothing is lost if the app is killed a moment later.
- **Pause**: stops appending points without ending the session. Elapsed
  time while paused does not count toward duration or average speed.
- **Resume**: continues appending points to the same session.
- **Stop**: ends the session and finalizes its summary (see
  [Data Model](data-model.md)). A stopped session cannot be resumed.

Recording must continue while the app is backgrounded or the screen is
off — a person tracking a run will lock their phone or switch apps.

## Location sampling

- Request the highest-accuracy location updates the platform offers for
  foreground navigation/fitness use (not passive/low-power mode).
- Target update interval: **every 2–5 seconds**, or on ~10 meter movement,
  whichever the platform's location API prefers — the goal is a smooth
  route line without excessive battery drain or storage bloat.
- Discard fixes with poor accuracy (accuracy radius worse than ~30 meters)
  rather than letting them distort the route or spike the speed reading.
- Each accepted fix becomes one point on the current session's track.

## Distance calculation

- Distance is the sum of the great-circle (haversine) distance between
  each consecutive pair of accepted points in a session.
- Points recorded while paused are excluded from the track entirely (not
  just from the distance sum) — pausing should leave a visible gap, not a
  straight line jump, when the route is drawn later.
- A minimum-movement threshold (a few meters) between consecutive points
  may be applied before adding to the distance sum, to avoid GPS jitter
  accumulating distance while stationary.

## Speed calculation

- **Current speed**: derived from the platform's instantaneous speed
  reading when available; otherwise computed from the distance and time
  between the last two accepted points. Smooth/average over a short
  rolling window (a few seconds) so it doesn't jump erratically.
- **Average speed** (shown in history/summary): total session distance
  divided by total recording time (excluding paused time).
- **Max speed**: the highest current-speed sample recorded during the
  session.
- Speed and distance are stored internally in SI units (meters, meters per
  second) and formatted for display at the UI layer.

## What must survive interruption

- If the app is killed by the OS while recording, the session recorded so
  far must not be lost. Points already accepted are persisted incrementally
  during recording, not only at stop.
- If the device reboots or the app is force-closed mid-recording, on next
  launch the app should treat the last unstopped session as stopped at its
  last recorded point, rather than silently discarding it.
