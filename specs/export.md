# Export

Three independent exports: the full history as a CSV file, a single
activity as a CSV file, and a single activity as an image. All hand off
to the platform's share/save mechanism rather than writing to a fixed
location — the user picks where the file goes.

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
Disabled or hidden when there is no history yet (nothing to export). Both
files are handed to the platform's share sheet together, in one action.

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

**Track points** — one data row per point, grouped by session (most
recent session first) and in recorded order within each session:

| Column | Source | Format |
|---|---|---|
| `session_started_at` | `Session.startedAt` | ISO 8601 — matches that point's session in the summaries file |
| `timestamp` | `TrackPoint.timestamp` | ISO 8601 |
| `latitude` | `TrackPoint.latitude` | number, degrees, 6 decimals |
| `longitude` | `TrackPoint.longitude` | number, degrees, 6 decimals |
| `speed_kmh` | `TrackPoint.speedMps` | number, ×3.6, 1 decimal; blank if the platform didn't report a speed for that point |

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
