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
0.8 m/s / ~3 km/h) relative to that first position — computed as distance
÷ time between that fix and the first one, never from the platform's own
reported instantaneous speed. A device held still can still report a
brief speed spike well above walking pace (GPS multipath/signal noise,
not real movement), which started sessions with zero actual displacement
when this gate trusted it.

This gating applies only to a session's initial Start — resuming after
Pause does not re-require movement, since the user has already
demonstrated they're active.

If Stop is tapped before movement is ever confirmed, the session is
discarded entirely rather than saved — nothing meaningful was recorded
(no points, zero distance and duration), so saving it would only add a
broken-looking blank entry to history. The user returns to Home, not to
that session's Detail screen.

## Auto-pause

- A session that's actively recording (past start gating, not already
  paused) pauses itself automatically once no real movement has been
  detected for a sustained period — roughly 15 seconds below walking
  pace. Otherwise a red light, a rest stop, or standing around after
  finishing would silently count as part of the activity, the same
  problem [start gating](#start-gating) solves for the very beginning of
  a session, just recurring anywhere in the middle of one.
- Uses the same movement signal and threshold as start gating, just
  applied continuously during recording (a sustained absence of it)
  instead of once at the beginning (a confirmed presence of it).
- Behaves exactly like tapping Pause manually — same Continue control on
  [Tracking](ui-flows.md#2-tracking-active-recording), no distinct
  "auto-paused" indicator. Resuming is always a manual tap; auto-pause
  never auto-resumes itself the moment movement resumes, so a person
  only ever starts the clock again by deliberately choosing to, not by
  an incidental shuffle a few seconds after stopping.

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

## Step count

- Wisp counts steps for a session using the device's step-count sensor
  (where present), so the total can be shown and exported (see
  [Data Model](data-model.md#session) and [Export](export.md#format)).
- Gated the same way the timer and track are (see
  [Start gating](#start-gating)): steps taken before movement is confirmed
  don't count. Steps while paused don't count either, matching how paused
  time is excluded from distance/duration.
- Not every device has a step-count sensor, and the platform may require
  a permission for it the user can decline — either way this is a
  best-effort enhancement, not a requirement for recording: with no
  sensor or no permission, the session's step count is simply 0, and
  nowhere in the UI treats that as an error or asks the user to fix
  anything (see [Permissions & Privacy](permissions-and-privacy.md#required-access)).
- A session recovered after an interruption (see
  [What must survive interruption](#what-must-survive-interruption)) has
  no step count — there's no live sensor to recover it from — same as any
  other best-effort data that depends on the app having been running.
- **Steps per minute**, shown on
  [Detail](ui-flows.md#3-detail-a-past-or-just-finished-session) and live
  on [Tracking](ui-flows.md#2-tracking-active-recording): total steps ÷
  recording-time minutes (excluding paused time, like average speed).
  Omitted entirely when the session's step count is 0.

## Km splits

- A **split** is the time it took to cover one complete distance unit of
  a session, per [Units](units.md) — a kilometer under metric, a **mile**
  under imperial — shown in full on the [Km splits](ui-flows.md#4-km-splits)
  view (reached from Detail; its title, "Fastest"/"Slowest" labels, and
  per-row unit all read "mile" under imperial) and live on
  [Tracking](ui-flows.md#2-tracking-active-recording) (only the most
  recently completed one) so a user can see which parts of the activity
  were faster or slower. Changing the unit setting changes where a split
  boundary itself falls (whole-mile splits, not km splits re-expressed as
  "0.62 mi"), recomputed fresh from the same recorded points — see
  [Units](units.md).
- The **fastest** complete split's own time is also shown live on
  [Tracking](ui-flows.md#2-tracking-active-recording), next to the
  latest split — null (nothing shown) until a second complete split
  exists to compare against, the same threshold as the Km splits view's
  fastest/slowest (below).
- [Detail](ui-flows.md#3-detail-a-past-or-just-finished-session)'s
  summary also shows the session's **average time per split**
  (across every complete one) and, once there are at least two to
  compare, the **fastest** one's own time — the same values the Km
  splits view lets someone read split by split, surfaced without having
  to open it. Omitted entirely with no complete split at all (average)
  or fewer than two (fastest), same as the live Tracking case above.
- Computed from the session's recorded points on demand (not stored
  alongside the session, unlike distance/duration/speed) — points are
  already loaded to draw the route on Detail, and already held in memory
  while recording, so there's nothing extra to fetch either way.
- Paused time and distance are excluded the same way they are from the
  session totals (see [Distance calculation](#distance-calculation)): a
  pause never counts toward completing a split.
- Only complete units count as splits — a session that ends partway
  through one (e.g. 3.4 km under metric) has 3 splits, not a fractional
  4th one.
- The stretch after the last complete split (the 0.4 km above) is the
  **partial split**: its own distance and time, shown after the splits
  on the [Km splits](ui-flows.md#4-km-splits) view but never counted as a
  split itself (not in Detail's split count, not as the live "latest
  split", never the fastest/slowest). Under 10 m — e.g. the few steps
  taken while reaching for Stop — there's no partial split at all,
  regardless of unit.
- A session under one full unit (1 km metric, 1 mile imperial) has no
  splits to show.
- **Steps per split**: each split (and the partial one) also has the
  number of steps taken over it — the difference between the running
  step counts recorded on the points (see
  [Data Model](data-model.md#trackpoint)) at its start and end, with the
  count at a split boundary interpolated within the segment that
  crosses it, the same way the split's time is. Paused steps are already
  excluded from that running count (see [Step count](#step-count)).
  A session whose points never recorded any steps (no step sensor or
  permission, or recorded before points carried step counts) has no
  steps per split at all — omitted, not shown as 0.

## What must survive interruption

- If the app is killed by the OS while recording, the session recorded so
  far must not be lost. Points already accepted are persisted incrementally
  during recording, not only at stop.
- If the device reboots or the app is force-closed mid-recording, on next
  launch the app should treat every unstopped session it finds this way
  (ordinarily just one, but see
  [Data integrity on start](data-model.md#data-integrity-on-start) for
  why the check can't assume that) as stopped at its last recorded
  point, rather than silently discarding it — unless it has no recorded
  points at all (interrupted while still "locating" or "waiting for
  movement", see [Start gating](#start-gating)), in which case there's
  no point to stop at, and it's discarded like any other never-moved
  session.
