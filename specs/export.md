# Export

Three independent exports: the full history as a CSV file, a single
activity as a CSV file, and a single activity as an image. Wisp never
writes to a fixed location — every export offers the user a choice of
**Share** (the platform's share sheet, handing the file to another app —
email, messaging, cloud storage, etc.) or **Save to device** (the
platform's own file/folder picker, writing directly to local storage
with no other app involved). Both read the same generated content and
produce the same file(s), named the same way (see below) — the
difference is only where they end up. Save to device exists specifically
so getting a local copy never requires sharing to a cloud app and
downloading it back from there.

## File names

Every exported file's name carries a timestamp, so a folder holding
several exports (the same activity exported twice, or history exported
last week and again today) shows at a glance which file is which — and
sorting by name sorts them chronologically:

- **History** exports are stamped with the moment of the export — a later
  export is the more complete one.
- **Single activity** exports (CSV and image) are stamped with the
  activity's start time — the same activity always gets the same name,
  and different activities never collide.

The timestamp is in the device's local time zone (it's what the user saw
on screen, unlike the UTC times *inside* the CSV — see
[Format](#format)), formatted `yyyy-MM-dd_HH-mm-ss` — no characters that
are illegal in file names on common platforms. The two files of a CSV
export share one prefix so they sort next to each other:

| Export | Files |
|---|---|
| History as CSV | `wisp-history-<export time>.csv`, `wisp-history-<export time>-track-points.csv` |
| Single activity as CSV | `wisp-activity-<start time>.csv`, `wisp-activity-<start time>-track-points.csv` |
| Single activity as an image | `wisp-activity-<start time>.png` |

E.g. `wisp-activity-2026-09-25_06-51-12.csv`.

## History as CSV

A user can export their full history — including every recorded GPS
point — as CSV files, so it can be opened in a spreadsheet (Excel, Google
Sheets, etc.) or fed into other tools.

### Scope

- Exports **two files together**: session summaries (one row per session)
  and track points (one row per recorded GPS point, across every
  session). Someone who only wants to browse activities uses the
  summaries file; someone who wants route geometry — for a GIS tool, a
  custom analysis, etc. — has it too, without opening each session's map
  individually.
- Exports everything — there is no date-range picker. For a single
  session, see [Single activity as CSV](#single-activity-as-csv) below
  instead.
- Nothing is exported automatically or on a schedule. Export only happens
  when the user explicitly asks for it.

### Trigger

An "Export CSV" action reachable from Home (see
[UI Flows](ui-flows.md#1-home)), near — but visually distinct from — the
feedback link, so it doesn't compete with the primary Start action.
Disabled or hidden when there is no history yet (nothing to export).
Tapping it offers the Share/Save to device choice described above; either
way both files go together, in one action (Save to device asks for one
folder, not one picker per file).

### Format

Both files are UTF-8, comma-separated, with a header row. Times are in
UTC (not the device's local time zone) so the files are unambiguous
regardless of where they're opened. Field names are the header rows
exactly as listed below — stable, so a spreadsheet formula or script
built against one export keeps working against a later one.

**Session summaries** — one data row per session, most recent first (same
order as the history list):

| Column | Source | Format |
|---|---|---|
| `started_at` | `Session.startedAt` | ISO 8601, e.g. `2026-09-22T14:03:00Z` |
| `distance_km` | `Session.distanceMeters` | number, meters / 1000, 2 decimals |
| `duration_seconds` | `Session.durationSeconds` | integer |
| `average_speed_kmh` | `Session.averageSpeedMps` | number, ×3.6, 1 decimal |
| `max_speed_kmh` | `Session.maxSpeedMps` | number, ×3.6, 1 decimal |
| `steps` | `Session.steps` | integer; 0 if no step sensor/permission was available (see [Tracking](tracking.md#step-count)) |

**Track points** — one data row per point, grouped by session (most
recent session first) and in recorded order within each session:

| Column | Source | Format |
|---|---|---|
| `session_started_at` | `Session.startedAt` | ISO 8601 — matches that point's session in the summaries file |
| `timestamp` | `TrackPoint.timestamp` | ISO 8601 |
| `latitude` | `TrackPoint.latitude` | number, degrees, 6 decimals |
| `longitude` | `TrackPoint.longitude` | number, degrees, 6 decimals |
| `speed_kmh` | `TrackPoint.speedMps` | number, ×3.6, 1 decimal; blank if the platform didn't report a speed for that point |
| `is_noise` | `TrackPoint.isNoise` | `true`/`false` — see [Tracking](tracking.md#noise). Every point Wisp ever recorded is exported, not just the ones it trusts; this column lets a user's own tooling decide what to include. A file exported before this column existed has no way to tell — treat a missing column the same as `false` (every point in it was already noise-filtered, the only kind that existed then) |
| `accuracy_m` | `TrackPoint.accuracyMeters` | number, meters, 1 decimal — the platform's reported accuracy radius for that fix. Exported so a user can see whether a `poor_accuracy` flag was borderline or hopeless |
| `noise_reason` | `TrackPoint.noiseReason` | the reason name(s), `\|`-separated — see [Tracking](tracking.md#noise-reasons); blank on a non-noise point, and on a noise point recorded before Wisp kept reasons |
| `segment_start` | `TrackPoint.segmentStart` | `true`/`false` — true on the session's first point and on the first point(s) after a pause, i.e. where the route is broken and no distance is counted from the previous point |
| `pause_cause` | `TrackPoint.pauseCause` | `manual` or `auto` on a point that starts a segment after a pause, blank otherwise — see [Tracking](tracking.md#auto-pause) |
| `steps` | `TrackPoint.steps` | integer — the session's running step count when the point was recorded, so a stretch with steps but no distance is visible |

These last five exist for troubleshooting and tuning the noise filter
(see [Tracking](tracking.md#noise)): with them, an export alone is enough
to tell why a stretch of a run went uncounted. They come after `is_noise`,
so the earlier columns keep their positions; a file exported before they
existed simply doesn't have them.

## Single activity as CSV

Same two-file format as [History as CSV](#history-as-csv) above (same
columns, same header rows), scoped to just the one activity the user is
currently looking at — a session summaries file with exactly one data
row, and a track points file with just that session's points.

### Trigger

An "Export CSV" action on [Detail](ui-flows.md#3-detail-a-past-or-just-finished-session),
alongside Export Image and Delete. Not available for an activity still
in progress, same as Export Image.

## Single activity as an image

A user can export one activity — the one they're currently looking at —
as a single image, suitable for sharing outside the app (messaging,
social, saving to photos).

### Trigger

An "Export Image" action on [Detail](ui-flows.md#3-detail-a-past-or-just-finished-session),
alongside Delete. Not available for an activity still in progress — export
a finished one from its Detail screen (the same screen Stop lands on).

### Content

One image containing:

- The activity's route drawn on the map, with the start and end/current
  markers (see [Accessibility](accessibility.md#map-markers-and-route)) —
  the same route rendering used on screen, not a separate simplified
  drawing.
- The same summary stats shown on Detail: date/time, distance, duration,
  average speed, max speed.

The stats are **not** floating text laid over the map image — they sit in
their own solid-background panel (e.g. below the map area), so legibility
never depends on what happens to be under the text on the map at that
spot. See [Accessibility](accessibility.md) for why.
