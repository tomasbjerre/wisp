# Accessibility

Cross-cutting requirements that apply everywhere Wisp shows a map or
information about an activity — not tied to one screen. When adding a new
screen or overlay, it must follow these too, not just the ones that
happened to exist when this was written.

## Map markers and route

- While recording, the map shows a marker for the user's current position
  — distinct from the route line itself, so "where am I right now" reads
  at a glance rather than requiring the user to find the end of a line.
- The route line is drawn with enough contrast to stay legible regardless
  of what's underneath it on the map (water, parks, roads, satellite
  imagery) — not just "a color", but a treatment that holds up against
  varying backgrounds (e.g. an outline/halo behind the line).
- A finished activity's route (Detail, and the exported image) shows
  distinct start and end markers, so the shape of the activity is
  readable without needing to trace the line by eye.
- This applies equally live (Tracking) and after the fact (Detail, the
  exported image) — see [UI Flows](ui-flows.md) and [Export](export.md).

## Text contrast

- All text presenting activity information must meet WCAG AA contrast
  against its background: **4.5:1** for normal text, **3:1** for large
  text (roughly 18pt+, or 14pt+ bold).
- A map is not a solid background — its color varies by location, zoom,
  and map style. Text is never placed directly over map pixels relying on
  a single fixed color to "probably" contrast; where activity info needs
  to be near a map (e.g. the exported image), it sits on its own
  solid-background panel instead.
- That panel stays solid while the user interacts with the map: the map
  is confined to its own area, so panning, flinging or zooming it never
  draws map tiles or route over a neighboring panel's text or controls
  (Tracking's stats and buttons, Detail's summary) — not even
  temporarily mid-gesture.
- Meeting the contrast requirement and looking good for users who don't
  need it are the same goal, not a trade-off — pick a palette that
  satisfies both rather than defaulting to plain black-on-white only
  where accessibility is being checked.
