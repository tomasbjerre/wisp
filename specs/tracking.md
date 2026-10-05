# Tracking

## Session lifecycle

A **session** is one continuous recording, from tap-to-start to tap-to-stop.

States: `idle → recording → (paused ⇄ recording) → stopped`.

- **Start**: begins acquiring location and appending points to the session.
  A session is created as soon as start is tapped, even before the first
  fix arrives, so nothing is lost if the app is killed a moment later.
- **Pause**: stops adding points to the track without ending the session.
  Elapsed time while paused does not count toward duration or average
  speed. Location updates keep arriving while paused, and each one is
  still stored — as a noise point with the reason `paused` (see
  [Noise](#noise)) — so an exported session shows where the person
  actually went during a pause, rather than a silent hole. They count
  toward nothing the app computes or shows.
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
   tap would count that dead time as part of the activity. Every fix
   received during this wait is still recorded, as noise (see
   [Noise](#noise)) — nothing seen during this step is thrown away, it
   just never counts toward the session's own stats.

Recording (the timer and the track) actually starts the moment a
subsequent fix implies movement at or above a walking pace (roughly
0.8 m/s / ~3 km/h) relative to that first position — computed as distance
÷ time between that fix and the first one, never from the platform's own
reported instantaneous speed. A device held still can still report a
brief speed spike well above walking pace (GPS multipath/signal noise,
not real movement), which started sessions with zero actual displacement
when this gate trusted it.

Movement can also be confirmed without ever reaching that pace relative
to the first position: if a person's path curves back toward where they
started (pacing at a trailhead, walking around a parked car), net
displacement from that first fix can stay small even while real ground
is being covered. To catch that case, movement is also confirmed once
the sum of fix-to-fix movement since the first fix reaches roughly 30
meters, regardless of direction — small, GPS-jitter-sized steps (a few
meters or less) aren't counted toward that sum, so standing still never
accumulates into a false positive.

A user who doesn't want to wait for either of the above can skip it —
see [Force start](#force-start).

This gating applies only to a session's initial Start — resuming after
Pause does not re-require movement, since the user has already
demonstrated they're active.

If Stop is tapped before movement is ever confirmed, the session is
discarded entirely rather than saved — nothing meaningful was recorded
(zero distance and duration, and every point it does have is noise — see
[Noise](#noise)), so saving it would only add a broken-looking blank
entry to history. The user returns to Home, not to that session's Detail
screen. The same applies if [Force start](#force-start) was used to skip
this gate and Stop is tapped again before any point was actually
recorded — every point the session has is still noise (or there are none
at all), so it's discarded exactly as if movement had never been
confirmed, force-started or not.

### Force start

While waiting for movement, a **Force start** control (see
[Tracking](ui-flows.md#2-tracking-active-recording)) lets the user skip
both checks above and begin recording immediately — e.g. GPS is slow to
confirm movement, or they'd simply rather not wait. It has no effect once
movement has already been confirmed (or before a session exists at all),
and does not affect [auto-pause](#auto-pause), which only ever applies
once recording has actually started.

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
  "auto-paused" indicator — and tapping Continue resumes at any time.
- **Auto-resume**: unlike a manual pause, an auto-pause also ends by
  itself once real, sustained movement is seen. A pause the app started
  on its own must not depend on the person noticing it: a runner with
  the phone in an armband who pauses at a crossing would otherwise lose
  the rest of the run without ever knowing. A manual pause never
  auto-resumes — the person paused deliberately, so only they end it.
  - "Real, sustained movement" is deliberately stricter than the
    [start gate](#start-gating): the sum of fix-to-fix movement since the
    pause began reaches roughly 30 meters (steps of a few meters or less
    are jitter and don't count), never a speed reading or a displacement
    from one anchor. An incidental shuffle after stopping must not
    resume, or a person standing at a light would flap between paused
    and recording.
  - Only fixes within the [accuracy threshold](#location-sampling) count
    toward it, since it's the fixes' positions that are being summed.
  - The fix that confirms the movement is the first point of the resumed
    segment, exactly as if the person had tapped Continue then. Time and
    distance from the pause up to that fix are not counted (they stay
    noise, as [Pause](#session-lifecycle) says); the export still has
    them.
- The fix that trips auto-pause is recorded like any other fix (as a
  regular point, or as noise if it fails a check — see [Noise](#noise)),
  *before* the pause takes effect. It is never dropped: nothing Wisp
  measures is thrown away.
- A fix too inaccurate to trust (worse than the
  [accuracy threshold](#location-sampling)) can't show that someone has
  stopped, so it never triggers auto-pause, and it restarts the idle
  clock rather than counting toward it. A false pause silently loses
  everything until the person notices and taps Continue, which is far
  worse than a late one.
- Whether a pause was manual or automatic is recorded on the first
  point(s) of the segment that follows it (see `pauseCause` in
  [Data Model](data-model.md#trackpoint)), so an exported track shows
  which gaps were the user's doing and which were auto-pause's — the
  only way to tell a real rest from auto-pause misfiring mid-run.

## Paused session reminder

A session left paused — or one that's been started but never gets
moving — is easy to forget: the phone goes back in a pocket, the screen
goes off, and the session quietly stays open for hours. A periodic
physical reminder solves that — see
[issue #174](https://github.com/tomasbjerre/wisp/issues/174), extended to
the waiting-for-movement case by
[issue #204](https://github.com/tomasbjerre/wisp/issues/204).

- While a session is **paused** — however it came to be paused, manual
  or automatic (see [Auto-pause](#auto-pause)) — or **waiting for its
  first movement** ([Start gating](#start-gating)) — the device gives a
  brief haptic pulse once a minute.
- Up to 20 pulses per stretch, then it stops. A person who hasn't acted
  on twenty minutes of reminders isn't going to, and the pulse shouldn't
  go on indefinitely.
- The count belongs to one paused or waiting stretch: resuming (by
  either means) or confirming movement ends it, and the next pause or
  wait starts a fresh set of 20. It is not a budget shared across a
  session.
- **Suppressed while the app is on screen.** Someone looking at
  [Tracking](ui-flows.md#2-tracking-active-recording) already knows the
  session is paused or still waiting; nothing needs reminding. While
  suppressed the schedule also waits rather than running down: the pulse
  comes one full minute after the app is last put away, never
  immediately on leaving it, and no pulses are silently spent while the
  screen showed the answer.
- One switch turns it off, on by default (see
  [Settings](ui-flows.md#2a-settings)); with it off, Wisp never pulses.
  Changing it takes effect immediately for the session being recorded,
  like every other setting on that screen.
- A single pulse of about two seconds at the device's normal strength;
  the exact duration is an implementation detail.
- Best effort, and never a problem: a device with no vibration hardware
  simply produces nothing, and the session is unaffected — same posture
  as [Voice feedback](voice-feedback.md) taking silence when there's no
  speech engine to speak with.
- Vibration is the whole reminder: the recording notification's text is
  not changed and no additional notification is posted.
- It ends with the stretch it belongs to — on Resume or confirmed
  movement, on Stop, and when the session itself ends. It does not
  survive the app being killed: a recording continued after an
  interruption
  ([What must survive interruption](#what-must-survive-interruption))
  continues as a recording, not a pause or a wait, and so gets no pulses
  until it next pauses.

## Location sampling

- Request the highest-accuracy location updates the platform offers for
  foreground navigation/fitness use (not passive/low-power mode).
- Target update interval: **every 2–5 seconds**, or on ~10 meter movement,
  whichever the platform's location API prefers — the goal is a smooth
  route line without excessive battery drain or storage bloat.
- Fixes with poor accuracy (accuracy radius worse than ~30 meters) are
  noise (see [Noise](#noise)) rather than being let through to distort
  the route or spike the speed reading.
- A fix whose implied speed from the previous accepted point (distance ÷
  time between them) is implausible for any activity Wisp is used for —
  roughly 55 m/s / ~200 km/h — is also noise, even if its reported
  accuracy passed the filter above. A sudden jump that fast is a GPS
  glitch (multipath reflection off buildings, a bad fix), not real
  movement, and letting it through would spike distance, average speed,
  and max speed with a value nobody actually reached. Generous on
  purpose: it must never flag genuine fast movement (running, cycling,
  even a car), only the kind of jump no tracked activity can produce.
- Each fix becomes one point on the current session's track, noise or
  not — see [Noise](#noise) for what that means downstream.

## Noise

A recorded point may be flagged **noise**: it looks like an error (poor
accuracy, an implausible jump — see [Location sampling](#location-sampling)
above), is GPS jitter too small to be real movement (see
[Distance calculation](#distance-calculation) below), or was recorded
before movement was ever confirmed (see [Start gating](#start-gating)
above) — a person is often still fumbling with their phone or walking
to a trailhead at that point, exactly the noise-looking case, not
something wrong with the fix itself — or arrived while the session was
paused (see [Session lifecycle](#session-lifecycle)), which likewise says
nothing bad about the fix.

- A noise point is still stored — nothing Wisp ever measures is thrown
  away — but excluded from everything the app itself computes or shows:
  distance, duration, speed, splits, the drawn route. Functionally, this
  is identical to how earlier versions of Wisp behaved by simply never
  recording these points at all; the difference is only that they're now
  kept, tagged, rather than discarded.
- A noise point never becomes the baseline a later fix is judged against
  (e.g. for the implausible-jump or minimum-movement checks) — exactly
  as if it had never been recorded, same as before this was tracked at
  all.
- Exported as-is (see [Export](export.md#format)), tagged, alongside
  every other point — a user's own tooling can decide what to include,
  rather than Wisp's own judgment call about what counts as noise being
  the only one that ever existed.
- Every noise point also records *why* it was flagged, so a user or
  developer looking at an export can tell a genuinely bad fix from a
  threshold that's tuned too tight — see [Noise reasons](#noise-reasons).
- What counts as noise is an algorithm, not a fixed label — a future
  version of Wisp may judge it differently. Nothing about this contract
  requires re-flagging *already-recorded* points when that happens
  (there is no re-classification pass today), but an implementation must
  not paint itself into a corner where doing so later is impossible.

### Noise reasons

A noise point's reason is one or more of the following names. A point that
trips several checks at once lists all of them, separated by `|` and in
this order (e.g. `poor_accuracy|min_movement`):

| Name | Meaning |
|---|---|
| `poor_accuracy` | reported accuracy worse than the threshold — see [Location sampling](#location-sampling) |
| `implausible_jump` | implied speed from the previous accepted point above the cap — see [Location sampling](#location-sampling) |
| `min_movement` | less than the minimum-movement threshold from the previous accepted point — see [Distance calculation](#distance-calculation) |
| `before_movement` | recorded while waiting for movement to be confirmed — see [Start gating](#start-gating) |
| `paused` | arrived while the session was paused — see [Session lifecycle](#session-lifecycle) |

While waiting for movement, only `poor_accuracy` (when the fix's accuracy
was also too poor) and `before_movement` apply — the other two checks need
an accepted previous point, which doesn't exist yet. Likewise while paused,
only `poor_accuracy` and `paused` apply: a paused fix is never compared
against the track.

## Distance calculation

- Distance is the sum of the great-circle (haversine) distance between
  each consecutive pair of non-noise points in a session.
- Points that arrive while paused are noise (see [Noise](#noise)), so
  they're excluded from the track entirely (not just from the distance
  sum) — pausing should leave a visible gap, not a straight line jump,
  when the route is drawn later.
- A fix less than a minimum-movement threshold (a few meters) from the
  previous accepted point is noise (see [Noise](#noise)) rather than
  being added to the distance sum — otherwise GPS jitter would accumulate
  distance while stationary.

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

## Heart rate

Optionally recorded from a BLE heart rate monitor — see
[Heart rate](heart-rate.md). Like steps, it's gated by
[start gating](#start-gating) and excluded while paused, and it's
best-effort: with no monitor or permission, recording is unaffected.

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
  complete splits, but its partial split is still reachable from
  Detail's [splits link](ui-flows.md#3-detail-a-past-or-just-finished-session)
  — otherwise a 1.2 km session would show splits under metric and
  nothing under imperial.
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
- If the OS then restarts the recording (the process comes back on its own,
  without the user doing anything), the same session **continues** instead
  of being ended at the moment of the kill:
  - Recording carries on as an active session — never paused, even if it
    was paused when the app was killed — with the distance, time, route,
    steps and highest heart rate recorded so far as its starting point.
    Time spent while nothing was recording is not counted.
  - The first point recorded after the restart starts a new segment (see
    [Data Model](data-model.md#trackpoint)), so the gap is never bridged
    by a line and never adds distance or time, and carries the pause cause
    `interrupted`, so it can be told apart from a `manual` or `auto`
    pause, also in the [export](export.md#format).
  - Only a session that had already confirmed movement (see
    [Start gating](#start-gating)) is continued. One that hadn't is
    discarded, as described below.
  - If the OS does not restart the recording within a short time after
    the app process starts, the session is ended at its last recorded
    point, as described below.
- If the device reboots or the app is force-closed mid-recording, on next
  launch the app should treat every unstopped session it finds this way
  (ordinarily just one, but see
  [Data integrity on start](data-model.md#data-integrity-on-start) for
  why the check can't assume that) as stopped at its last recorded
  point, rather than silently discarding it — unless it has no non-noise
  points at all (interrupted while still "locating" or "waiting for
  movement", see [Start gating](#start-gating) and [Noise](#noise)), in
  which case there's no point to stop at, and it's discarded like any
  other never-moved session.
