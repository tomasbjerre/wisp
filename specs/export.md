# Export

Two independent exports: the full history as a CSV file, and a single
activity as an image. Both hand off to the platform's share/save
mechanism rather than writing to a fixed location — the user picks where
the file goes.

## History as CSV

A user can export their full history as a single CSV file, so it can be
opened in a spreadsheet (Excel, Google Sheets, etc.).

### Scope

- Exports the session **summaries** in history — one row per session — not
  the raw per-point GPS track. A spreadsheet is for scanning/sorting a list
  of activities, not for route geometry; someone who wants the full track
  data for one session already has it via that session's map (see
  [UI Flows](ui-flows.md#3-detail-a-past-or-just-finished-session)).
- Exports everything — there is no date-range or per-session picker. The
  history list is already the way to work with a subset (open one session
  at a time). If a real need for partial export shows up later, it should
  be added here as its own requirement rather than assumed now.
- Nothing is exported automatically or on a schedule. Export only happens
  when the user explicitly asks for it.

### Trigger

An "Export CSV" action reachable from Home (see
[UI Flows](ui-flows.md#1-home)), near — but visually distinct from — the
feedback link, so it doesn't compete with the primary Start action.
Disabled or hidden when there is no history yet (nothing to export).

### Format

One CSV file, UTF-8, comma-separated, with a header row. One data row per
session, most recent first (same order as the history list).

| Column | Source | Format |
|---|---|---|
| `started_at` | `Session.startedAt` | ISO 8601, e.g. `2026-09-22T14:03:00Z` |
| `distance_km` | `Session.distanceMeters` | number, meters / 1000, 2 decimals |
| `duration_seconds` | `Session.durationSeconds` | integer |
| `average_speed_kmh` | `Session.averageSpeedMps` | number, ×3.6, 1 decimal |
| `max_speed_kmh` | `Session.maxSpeedMps` | number, ×3.6, 1 decimal |

Times are in UTC (not the device's local time zone) so the file is
unambiguous regardless of where it's opened. Field names are the header
row exactly as listed — stable, so a spreadsheet formula or script built
against one export keeps working against a later one.

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
