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

## Start gating

Tapping Start creates the session immediately (see above), but the timer,
distance, and track don't start immediately with it — the tap goes through
two gates first:

1. **Locating**: until the first fix that passes the accuracy filter in
   [Location sampling](#location-sampling) arrives, there's nothing real
   to show yet, so show a loading state, not a map. This avoids showing a
   meaningless default view (e.g. a map centered far from the user) while
   GPS acquires a fix.
2. **Waiting for movement**: once that fix arrives, show the current
   position on the map, but keep the timer and distance at zero and tell
   the user recording will begin once they're moving. A person who just
   tapped Start is often still fumbling with their phone, walking to a
   trailhead, or waiting at a crosswalk — starting the clock at that exact
   tap would count that dead time as part of the activity.

Recording (the timer and the track) actually starts the moment a
subsequent fix implies movement at or above a walking pace (roughly
0.8 m/s / ~3 km/h) relative to that first position — using the platform's
reported instantaneous speed where available, otherwise distance ÷ time
between fixes.

This gating applies only to a session's initial Start — resuming after
Pause does not re-require movement, since the user has already
demonstrated they're active.

If Stop is tapped before movement is ever confirmed, the session is
discarded entirely rather than saved — nothing meaningful was recorded
(no points, zero distance and duration), so saving it would only add a
broken-looking blank entry to history. The user returns to Home, not to
that session's Detail screen.

## Location sampling

- Request the highest-accuracy location updates the platform offers for
  foreground navigation/fitness use (not passive/low-power mode).
- Target update interval: **every 2–5 seconds**, or on ~10 meter movement,
  whichever the platform's location API prefers — the goal is a smooth
  route line without excessive battery drain or storage bloat.
- Discard fixes with poor accuracy (accuracy radius worse than ~30 meters)
  rather than letting them distort the route or spike the speed reading.
- Discard a fix whose implied speed from the previous accepted point
  (distance ÷ time between them) is implausible for any activity Wisp is
  used for — roughly 55 m/s / ~200 km/h — even if its reported accuracy
  passed the filter above. A sudden jump that fast is a GPS glitch
  (multipath reflection off buildings, a bad fix), not real movement, and
  letting it through would spike distance, average speed, and max speed
  with a value nobody actually reached. Generous on purpose: it must
  never reject genuine fast movement (running, cycling, even a car), only
  the kind of jump no tracked activity can produce.
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

## Km splits

- A **split** is the time it took to cover one complete kilometer of a
  session — shown on [Detail](ui-flows.md#3-detail-a-past-or-just-finished-session)
  so a user can see which parts of the activity were faster or slower.
- Computed from the session's recorded points on demand (not stored
  alongside the session, unlike distance/duration/speed) — points are
  already loaded to draw the route on Detail, so there's nothing extra to
  fetch.
- Paused time and distance are excluded the same way they are from the
  session totals (see [Distance calculation](#distance-calculation)): a
  pause never counts toward completing a split.
- Only complete kilometers are listed — a session that ends partway
  through one (e.g. 3.4 km) shows 3 splits, not a fractional 4th one.
- A session under 1 km has no splits to show.

## What must survive interruption

- If the app is killed by the OS while recording, the session recorded so
  far must not be lost. Points already accepted are persisted incrementally
  during recording, not only at stop.
- If the device reboots or the app is force-closed mid-recording, on next
  launch the app should treat the last unstopped session as stopped at its
  last recorded point, rather than silently discarding it — unless it has
  no recorded points at all (interrupted while still "locating" or
  "waiting for movement", see [Start gating](#start-gating)), in which
  case there's no point to stop at, and it's discarded like any other
  never-moved session.
