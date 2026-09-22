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

`distanceMeters`, `durationSeconds`, `averageSpeedMps`, and `maxSpeedMps`
are derived from the session's points but should be stored (not
recomputed on every read) so history lists stay cheap to render.

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

Points recorded while a session is paused are never created — see
[Tracking](tracking.md). `segmentStart` marks where a pause broke the track,
so distance/duration/route-drawing never bridge across a pause: the point
pair `(previous point, a `segmentStart` point)` is never connected by a
line, and the time/distance between them is never added to the session's
totals.

## Required queries

Any implementation's storage layer must support:

1. Create a session and append points to it incrementally while recording
   (not just a single bulk write at stop).
2. List all sessions, most recent first, with enough fields to render a
   history row (start time, distance, duration, average speed) without
   loading every point.
3. Load one session with all of its points, in `sequence` order, to draw
   its route and show its detail summary.
4. Delete a session and all of its points.
5. Find the most recent session that has no `endedAt` (to recover from an
   interrupted recording — see [Tracking](tracking.md)).
