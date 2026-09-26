# Data Model

These entities are storage-technology-agnostic. An implementation may use
any local persistence mechanism (SQL database, files, etc.) as long as it
can represent this shape and answer the queries listed at the bottom.

## Session

One recorded activity.

| Field | Type | Notes |
|---|---|---|
| `id` | identifier | unique per session |
| `startedAt` | timestamp | when recording started |
| `endedAt` | timestamp, nullable | null while still recording; set on stop |
| `distanceMeters` | number | total distance, excluding paused segments |
| `durationSeconds` | number | total recording time, excluding paused time |
| `averageSpeedMps` | number | `distanceMeters / durationSeconds` |
| `maxSpeedMps` | number | highest recorded current-speed sample |
| `nearestCity` | text, nullable | name of the city/place nearest the session's start point; null until resolved, or if it couldn't be resolved (see [Permissions & Privacy](permissions-and-privacy.md#data-handling)) |
| `steps` | integer | total steps counted during the session, excluding paused time (see [Tracking](tracking.md#step-count)); 0 if no step sensor/permission was available, or the session was recovered after an interruption |

`distanceMeters`, `durationSeconds`, `averageSpeedMps`, and `maxSpeedMps`
are derived from the session's points but should be stored (not
recomputed on every read) so history lists stay cheap to render.
`nearestCity` is resolved after the session finishes and stored once
known — it never blocks finishing a session or navigating away from it.
`steps` comes from a live sensor reading during recording, not from the
points, so it can't be recomputed later the way the others can. (Each
point also records the running step count at that moment — see
[TrackPoint](#trackpoint) — but only so steps can be split by kilometer,
see [Tracking](tracking.md#km-splits); `Session.steps` stays the total.)

## TrackPoint

One accepted GPS fix belonging to a session.

| Field | Type | Notes |
|---|---|---|
| `id` | identifier | unique per point |
| `sessionId` | identifier | foreign key to Session |
| `sequence` | integer | ordering within the session (timestamps alone are not always monotonic-safe across sources) |
| `timestamp` | timestamp | when the fix was recorded |
| `latitude` | number | degrees |
| `longitude` | number | degrees |
| `accuracyMeters` | number | reported accuracy radius, for debugging/QA, not shown to the user |
| `speedMps` | number, nullable | instantaneous speed at this point, if the platform provided one |
| `segmentStart` | boolean | true if this is the first point of a session, or the first point after a resume |
| `steps` | integer | the session's step count so far when this point was recorded (see [Tracking](tracking.md#step-count)) — a running total, so the steps between any two points are the difference between theirs; 0 on every point if no step sensor/permission was available, or the point predates this field |

Points recorded while a session is paused are never created — see
[Tracking](tracking.md). `segmentStart` marks where a pause broke the track,
so distance/duration/route-drawing never bridge across a pause: the point
pair `(previous point, a `segmentStart` point)` is never connected by a
line, and the time/distance between them is never added to the session's
totals.

## Voice feedback settings

See [Voice feedback](voice-feedback.md) for what each of these controls.
Unlike Session/TrackPoint, this is a single set of key/value flags, not a
growing history — one value per field, no identifier, no relations, no
migrations to worry about beyond "missing means the fixed default below".

| Field | Type | Default |
|---|---|---|
| `enabled` | boolean | `false` |
| `announceKm` | boolean | `true` |
| `announceSpeed` | boolean | `true` |
| `announceSteps` | boolean | `true` |
| `announceElapsedTime` | boolean | `true` |

## Required queries

Any implementation's storage layer must support:

1. Create a session and append points to it incrementally while recording
   (not just a single bulk write at stop).
2. List all **finished** sessions (`endedAt` set), most recent first, with
   enough fields to render a history row (start time, distance, duration,
   average speed) without loading every point. A session that is still
   recording, or one left behind unfinished by an interruption and not
   yet recovered (see [Tracking](tracking.md#what-must-survive-interruption)),
   must never appear in this list — Home only shows past activities (see
   [UI Flows](ui-flows.md#1-home)).
3. Load one session with all of its points, in `sequence` order, to draw
   its route and show its detail summary.
4. Delete a session and all of its points.
5. Find every session that has no `endedAt` (to recover from an
   interrupted recording — see [Tracking](tracking.md)). Ordinarily at
   most one such session exists at a time, but the query itself must not
   assume that — see [Data integrity on start](#data-integrity-on-start).

## Data integrity on start

Stored data an implementation reads must never be trusted blindly just
because it came from local storage — a previous run could have been
killed mid-write, or a future version of Wisp could change what a stored
value is allowed to mean. On every app start:

- Any session left in an impossible state — the clearest example being
  one still marked as recording (`endedAt` still null) when the app
  obviously isn't recording anything, because it just started — is
  fixed if a fix is well-defined, or discarded if it isn't. See
  [Tracking](tracking.md#what-must-survive-interruption) for exactly how
  that's decided for this case: finished at its last recorded point if
  it has any, deleted entirely if it doesn't. This check must cover
  *every* session left in that state, not only the most recently started
  one — e.g. two interrupted in a row before either was ever recovered.
- A storage schema change (a new field, a changed meaning for an
  existing one, a new required relationship) must carry the existing
  data forward — via an explicit, tested migration — rather than
  discarding it. This applies from the first release Wisp actually ships
  to real users onward: once real devices hold real activity history, an
  implementation detail changing shape is never a reason to silently
  erase someone's recorded activities. A schema change with no
  reasonable migration path (rare) must say why in the change itself,
  not leave it unstated.
- Future storage-integrity checks belong here as they're identified —
  this section is the list, not just the one example above.
