# Units

Every distance, speed, and pace Wisp shows a user is displayed in one of
two unit systems — **Metric** (kilometers, km/h) or **Imperial** (miles,
mph) — chosen with a setting on [Home](ui-flows.md#1-home). Metric is the
default.

## Design principle exception

See [Overview](overview.md#design-principle): Wisp otherwise prefers a
fixed default over a setting. Which unit system reads naturally to someone
is regional/personal — there's no single default that's correct for
everyone the way there is for almost everything else in Wisp — so, like
[Voice feedback](voice-feedback.md), this is a deliberate, narrow
exception, not a precedent for settings in general.

## What's affected

Everything measured in distance or speed, everywhere it's shown:

- **Distance** — [Home](ui-flows.md#1-home)'s history rows,
  [Detail](ui-flows.md#3-detail-a-past-or-just-finished-session)'s summary,
  and live on [Tracking](ui-flows.md#2-tracking-active-recording). Below
  one full unit, shown as meters (metric) or feet (imperial) instead of a
  fraction of a kilometer/mile — same reasoning either way.
- **Speed** — average and max speed everywhere they're shown, and each
  split's own speed.
- **Splits** (see [Tracking](tracking.md#distance-splits)) — a split
  covers one complete kilometer under metric, or one complete **mile**
  under imperial. This changes where a split boundary actually falls, not
  just how an already-computed kilometer is relabeled: an imperial user
  gets whole-mile splits (the
  [Km splits](ui-flows.md#4-km-splits) view's title, "Fastest"/"Slowest"
  labels, and per-row unit all read "mile" instead), not km splits
  awkwardly re-expressed as "0.62 mi" apiece.
- **Average and fastest time per split**
  (see [Tracking](tracking.md#distance-splits)) — same split-boundary
  change as above, so "time per kilometer" reads as "time per mile" under
  imperial, not a converted-but-still-km-shaped number.
- **Voice feedback** (see [Voice feedback](voice-feedback.md)) — an
  announcement fires once per completed split (km or mile, per this
  setting), and says "kilometers"/"kilometers per hour" or
  "miles"/"miles per hour" to match. The **Steps per kilometer**/
  **Average speed per kilometer** switches on
  [Voice feedback settings](ui-flows.md#2a-voice-feedback-settings) relabel
  to say "mile" under imperial, same switches, same stored preference.
- Changing the setting takes effect immediately, including recomputing a
  session actively being recorded — there's no reason a split already
  computed from raw recorded points needs to "start over" in the new
  unit; the underlying points don't change, only how they're presented.

## What's unaffected

- **Steps** and **steps per minute/split** — a count, not a distance;
  identical either way.
- **Time** (duration, elapsed time) — identical either way.
- **CSV export** (see [Export](export.md)) — always metric, regardless of
  this setting. A CSV is a data-interchange format read by spreadsheets
  and other tools expecting one fixed, predictable unit; making it follow
  a display preference would mean two exports of the same activity could
  carry silently different units depending on when they were made.
  **Export Image** is different: it's a picture of what's already on
  screen, so it follows this setting like everything else Detail shows.
- The map itself — a route's shape and the standard/satellite toggle
  don't depend on units.

## Persistence

The chosen unit system persists locally across app restarts, the same
mechanism as [Voice feedback](voice-feedback.md#settings)'s settings —
this never syncs anywhere.
