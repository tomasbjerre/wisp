# Calories burned

Wisp can estimate the calories a session burned. It is optional: nothing
about it is shown until the user has entered their weight.

## Design principle exception

See [Overview](overview.md#design-principle). An estimate needs the
person's body weight, which Wisp cannot know and which differs for every
user, so this is a deliberate, narrow exception like
[Units](units.md) — there is no fixed default that would be right.

## Activity type

Every session has an **activity type**: **walking**, **running** or
**cycling**. It picks the MET table below, and nothing else — it does not
change how a session is recorded.

- Chosen on [Tracking](ui-flows.md#2-tracking-active-recording), where the
  session is recorded — not on Home, which only shows it.
- A new session starts with the activity type of the most recent session
  that has one — so it is the type of your last activity, and if that
  activity was deleted, of the one before it. **Walking** when there is
  none. Nothing else is remembered: a session that was discarded (see
  [Tracking](tracking.md#start-gating)) never counts, whatever type it had.
  It can be changed at any time while the session is being recorded,
  waiting for movement and paused included.
- Stored on the session (see [Data Model](data-model.md#session)) as soon as
  the session exists, and again whenever it is changed, so a session that
  continues after the app was killed (see
  [Tracking](tracking.md#what-must-survive-interruption)) keeps it. Once
  the session has stopped it cannot be changed: [Detail](ui-flows.md#3-detail-a-past-or-just-finished-session)
  shows it.
- A session recorded before this existed has no activity type.
- Its calories are always calculated for the whole session from its
  current type (see [Calculation](#calculation)) — changing the type
  halfway re-estimates the session as if it had been that type throughout.

## Weight

- Entered on [Tracking](ui-flows.md#2-tracking-active-recording), where
  the session is recorded — same as [activity type](#activity-type), and
  for the same reason: it's the one place that already has the session
  whose calories it affects, rather than a separate settings screen with
  no session to apply it to. Shown and entered in kilograms under metric
  and pounds under imperial (see [Units](units.md)); stored in kilograms.
- Empty means not configured. Clearing the field removes the weight.
- Stored on the session (see [Data Model](data-model.md#session)) as soon
  as the session exists, and again whenever it is changed — same as
  activity type — so a session that continues after the app was killed
  (see [Tracking](tracking.md#what-must-survive-interruption)) keeps it.
  It can be changed at any time while the session is being recorded,
  waiting for movement and paused included, taking effect immediately on
  the calories shown. Once the session has stopped it cannot be changed.
- Also remembered as the default the next session starts with (unlike
  activity type, which instead starts as the previous session's own type
  — see [Activity type](#activity-type)), so a session started with no
  weight ever entered has none, but one recorded after a weight was set
  — on this session or an earlier one — starts with it already filled
  in.
- A session recorded before this existed, or stopped before any weight
  was ever configured, has no weight and never has calories, even if a
  weight is entered afterwards.

## Calculation

```
calories (kcal) = MET × weight (kg) × time (hours)
```

- **Time** is the session's recorded duration — paused time is not part of
  it (see [Tracking](tracking.md#session-lifecycle)).
- **MET** is looked up in the table below from the session's activity type
  and its **average speed** (distance divided by that same duration).
  A speed falls in the row with the highest lower bound it reaches. The
  values are from the Compendium of Physical Activities.
- A session with no duration has no calories.
- Displayed rounded to whole kilocalories, e.g. `312 kcal`. Calories are
  never stored — they are calculated from the stored weight, activity
  type, distance and duration whenever they are shown, live on Tracking
  from the same figures.

| Speed from (km/h) | Walking | Running | Cycling |
|---|---|---|---|
| 0 | 2.0 | 6.0 | 4.0 |
| 3.2 | 2.8 | 6.0 | 4.0 |
| 4.0 | 3.0 | 6.0 | 4.0 |
| 4.8 | 3.5 | 6.0 | 4.0 |
| 5.6 | 4.3 | 6.0 | 4.0 |
| 6.4 | 5.0 | 6.0 | 4.0 |
| 7.2 | 7.0 | 6.0 | 4.0 |
| 8.0 | 8.3 | 8.3 | 4.0 |
| 8.4 | 8.3 | 9.0 | 4.0 |
| 9.7 | 8.3 | 9.8 | 4.0 |
| 10.8 | 8.3 | 10.5 | 4.0 |
| 11.3 | 8.3 | 11.0 | 4.0 |
| 12.1 | 8.3 | 11.5 | 4.0 |
| 12.9 | 8.3 | 11.8 | 4.0 |
| 13.8 | 8.3 | 12.3 | 4.0 |
| 14.5 | 8.3 | 12.8 | 4.0 |
| 16.1 | 8.3 | 14.5 | 6.8 |
| 19.3 | 8.3 | 19.0 | 8.0 |
| 22.5 | 8.3 | 19.0 | 10.0 |
| 25.7 | 8.3 | 19.0 | 12.0 |
| 32.2 | 8.3 | 19.0 | 15.8 |

## Where it is shown

The activity type itself is shown on
[Home](ui-flows.md#1-home)'s history rows and on
[Detail](ui-flows.md#3-detail-a-past-or-just-finished-session) for every
session that has one, whether or not it has a weight.

Calories are shown only for a session that has an activity type and a
stored weight:

- [Home](ui-flows.md#1-home)'s history rows, after the average speed.
- [Detail](ui-flows.md#3-detail-a-past-or-just-finished-session)'s summary.
- Live on [Tracking](ui-flows.md#2-tracking-active-recording).

Omitted entirely otherwise, never shown as `0 kcal`.

Calories are not part of [Export](export.md).
