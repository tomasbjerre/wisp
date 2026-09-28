# UI Flows

Wisp has three main screens — Home, Tracking, Detail — plus a Km splits
view reached from Detail, a Weight view reached from Home (see
[Calories burned](calories.md)), and a Voice feedback settings view reached
from Tracking (see [Voice feedback](voice-feedback.md), and
[Overview](overview.md#design-principle) for why this one narrow
exception exists). No onboarding wizard, no account/login — simplicity
is a feature.

Whenever a screen/view listed here is added, renamed, or removed, update
the "which screen" explainer and dropdown in
[`.github/ISSUE_TEMPLATE/`](../.github/ISSUE_TEMPLATE/) (feature_request,
bug_report, support) to match — those exist so issue reporters can name
the right screen, and go stale silently otherwise.

## 1. Home

The app's entry point.

- A prominent **Start** button/action, always available when idle.
- Directly below it, an **activity type** choice — **Walking**, **Running**
  or **Cycling** (see [Calories burned](calories.md#activity-type)) — with
  the last used one selected.
- A **Weight** action that opens the [Weight](#1a-weight) view, in the top
  bar next to Export and the information dialog.
- A list of past sessions (most recent first), each row showing:
  date/time, place name if known (see
  [Data Model](data-model.md#place-name)), distance, duration, average speed,
  and calories when the session has them (see
  [Calories burned](calories.md#where-it-is-shown)).
- Tapping a row opens that session's **Detail** screen.
- Each row also has a **Delete** action of its own (e.g. a trash icon),
  with the same confirmation step as Detail's Delete, so a session can be
  removed without opening it first.
- An empty state ("No activities yet — tap Start to record your first
  route.") when there is no history.
- An **Export CSV** action (see [Export](export.md)), disabled or hidden
  when there's no history yet.
- If there's an interrupted session recovered on launch (see
  [Tracking](tracking.md)), it simply appears in the list like any other
  finished session — no special dialog or interruption.

## 1a. Weight

Opened from the **Weight** action on [Home](#1-home). See
[Calories burned](calories.md#weight).

- A title and a back control returning to Home (system back does the
  same).
- One numeric field, labelled with its unit (kg, or lb under imperial),
  and a line explaining that the weight is used to calculate calories and
  that calories are not shown while it is empty.
- The field reflects and immediately persists what is typed — no separate
  Save action. A value that is not a positive number is not stored;
  clearing the field removes the weight.

## 2. Tracking (active recording)

Entered by tapping Start on Home. Stays active in the background/locked
screen while recording.

- The system/gesture back action behaves exactly like tapping Stop (see
  [Navigation](#navigation) below) — same finalize-or-discard logic, same
  destination (that session's Detail screen, or Home if movement was
  never confirmed). It does not simply return to Home on its own: leaving
  the session's recording ongoing but no longer reachable from the UI
  would leave it to linger unfinished — see
  [Tracking](tracking.md#what-must-survive-interruption).
- Entering this screen doesn't show the map and controls right away — see
  [Tracking](tracking.md#start-gating) for the "locating" (a loading
  state, no map yet) and "waiting for movement" states shown first, each
  telling the user what it's waiting for.
- A map filling most of the screen, centered on the current location,
  drawing the route as it's recorded, with a marker for the current
  position (see [Accessibility](accessibility.md#map-markers-and-route)).
  Zooming out is capped at roughly 100 km of width — not the entire
  world — so a stray pinch-out never leaves the user looking at a
  near-blank globe with their tiny route lost in the middle of it. This
  cap applies to Detail's map too (below), since both use the same map
  component.
- A small control over a corner of the map switches between the standard
  street map and a satellite view, so the terrain around a route can be
  inspected either way (see [Accessibility](accessibility.md#map-markers-and-route)
  for why the route/markers stay legible on either). Its current choice
  only lasts for this viewing of the screen — it isn't remembered between
  recordings. The same control, with the same behavior, is offered on
  Detail's map (below).
- A settings control over another corner of the map opens
  [Voice feedback settings](#2a-voice-feedback-settings) — see
  [Voice feedback](voice-feedback.md).
- A live stats panel below the map (not laid over it — see
  [Accessibility](accessibility.md#text-contrast)): current speed,
  elapsed distance, elapsed time — all zero while waiting for movement.
  Also steps per minute (see [Tracking](tracking.md#step-count)), the
  most recently completed kilometer split, and the fastest complete km
  so far (see [Tracking](tracking.md#km-splits)) — each omitted until
  there's one to show, same as Detail. Unlike Detail, only the latest
  and fastest splits are shown, not the full list — there's no room for
  a growing list on this screen, and "how was that last km, and is it
  my best one" is what's actually useful mid-run.
  Also the current and maximum heart rate when the heart rate monitor
  setting is on (see [Heart rate](heart-rate.md#display)), and the
  calories burned so far when the session has them (see
  [Calories burned](calories.md#where-it-is-shown)).
- A **Heart rate monitor** switch, off by default, in the stats panel
  above the controls, visible while waiting for movement as well as while
  recording; disabled when no monitor is available (see
  [Heart rate](heart-rate.md#setting)).
- A **Pause**/**Continue** control (labeled Continue while paused) and a
  **Stop** control, both visible together at all times once recording has
  actually started — recording or paused, it's always exactly these two
  controls, no intermediate confirmation step. Also flips to paused on
  its own after a sustained stop, and flips back to recording on its own
  once real movement resumes after such an automatic pause (a manual
  pause waits for Continue) — see [Tracking](tracking.md#auto-pause).
  Same controls either way; Continue works at any time.
- While waiting for movement, **Pause** is replaced by **Force start**
  (see [Tracking](tracking.md#force-start)) — **Stop** stays in its
  place, so it's always exactly two controls regardless of state. A line
  of text above the controls explains that recording starts
  automatically once moving, and that Force start begins it immediately
  instead.
- Tapping Stop finalizes the session immediately and navigates to that
  session's Detail screen — unless movement was never confirmed (see
  [Tracking](tracking.md#start-gating)), in which case the session is
  discarded and Stop returns to Home instead, since there's no Detail
  screen worth showing for it.
- If location permission is missing or denied, this screen must explain
  what's needed and offer a way to grant it, rather than silently
  recording nothing (see [Permissions & Privacy](permissions-and-privacy.md)).
- If notifications are turned off for Wisp, this screen says so and offers
  a way to turn them on (see
  [Permissions & Privacy](permissions-and-privacy.md#required-access)) —
  advisory, not blocking. The recording notification is what a user swipes
  down to see, and what opens this screen again.
- If Wisp isn't exempt from battery optimization, this screen says so and
  offers a way to fix it (see
  [Permissions & Privacy](permissions-and-privacy.md#required-access)) —
  advisory, not blocking: recording still works either way.

## 2a. Voice feedback settings

Opened from the settings control on [Tracking](#2-tracking-active-recording)'s
map, over that screen. See [Voice feedback](voice-feedback.md) for the
full contract.

- A title and a back control returning to Tracking (system back does the
  same).
- A **Voice feedback** master switch.
- Five switches choosing what each announcement includes: **Kilometers
  completed**, **Average speed per kilometer**, **Steps per kilometer**,
  **Elapsed time per kilometer**, **Total elapsed time**.
- Every switch reflects and immediately persists its current setting —
  no separate Save action.

## 3. Detail (a past or just-finished session)

- A map showing the full recorded route, with start and end markers (see
  [Accessibility](accessibility.md#map-markers-and-route)), and the same
  standard/satellite toggle described under [Tracking](#2-tracking-active-recording)
  above, over a corner of the map.
- Summary stats below the map, in their own panel (see
  [Accessibility](accessibility.md#text-contrast)): date/time,
  place name if known (see [Data Model](data-model.md#place-name)), distance,
  duration, average speed, max speed, steps per minute (see
  [Tracking](tracking.md#step-count) — omitted entirely when the session
  has no step count), and average time per kilometer plus the fastest
  one's own time (see [Tracking](tracking.md#km-splits) — average
  omitted with no complete km at all, fastest omitted with fewer than
  two), max heart rate (see [Heart rate](heart-rate.md#display) —
  omitted when the session has none), and calories burned (see
  [Calories burned](calories.md#where-it-is-shown) — omitted when the
  session has none).
- Below the summary stats, a **Km splits (N)** link — N being the number
  of complete kilometers (miles under imperial, where it reads **Mile
  splits (N)**) — opening the [Km splits](#4-km-splits) view.
  The splits themselves aren't listed here: this panel shares the screen
  with the map, and a list long enough to be useful would take the map's
  room. For a session under one full unit there is no complete split to
  count, so the link reads just **Km splits** / **Mile splits** and opens
  the view with only the [partial split](tracking.md#km-splits) — which
  is what an imperial user sees for a 1–1.6 km session, instead of the
  link vanishing. Omitted entirely only when there is no partial split
  either (under 10 m).
- Below that, two rows of two controls: **Export CSV** (see
  [Export](export.md#single-activity-as-csv)) and **Export Image** (see
  [Export](export.md#single-activity-as-an-image)) on top, **Back** and
  **Delete** below — the two actions taken while reviewing an activity
  grouped together, above the two that leave the screen either way.
- Delete has a confirmation step before it actually deletes.

## 4. Km splits

Reached from Detail's Km splits link, for analyzing a finished session
kilometer by kilometer (see [Tracking](tracking.md#km-splits) for how
splits are computed).

- A title and a back control returning to Detail (system back does the
  same).
- The **fastest** and **slowest** complete kilometer, each with its split
  time — only when there are at least two complete kilometers to compare.
- A table, one row per complete kilometer in order, numbered from 1: the
  split time, the average speed over that kilometer, and the number of
  steps taken over that kilometer (see [Tracking](tracking.md#km-splits))
  — the whole Steps column is omitted when the session has no steps per
  split. Just these four columns (Km, Time, Speed, Steps) — no other
  decoration on the row.
- After those, the partial km (if any), labeled with its distance (e.g.
  `+0.40`) and visually set apart from the full kilometers, since its
  time (and steps) cover a shorter distance than theirs; its speed is
  directly comparable.
- Long sessions scroll within the table.

## Feedback and support

It must be clear to a user how to report feedback, problems, or feature
requests, and how to find technical details (like the app version) that
issue reports ask for. Wisp has no in-app support flow or settings screen
of its own, so this is a single icon on Home, kept out of the way of the
Start button and history list, opening an **Information** view — not
another full screen/navigation destination, since there's nothing here
that needs one; a dialog over Home is enough. It shows:

- The app's version and the device model/Android version, so a user
  filing an issue doesn't have to go digging for either elsewhere — and
  can copy them straight into the matching fields the issue templates
  already ask for.
- A link to https://github.com/tomasbjerre/wisp/issues, opening in the
  user's browser, to report a problem or request a feature.
- A link to the [user manual](https://github.com/tomasbjerre/wisp/blob/main/docs/user-manual.md),
  also opening in the user's browser.

## Navigation

```
Home ──(tap Start)──▶ Tracking ──(tap Stop)──▶ Detail ──(tap Km splits)──▶ Km splits
  │                       │  ▲                    ▲
  │                       │  │                     │
  │        (tap settings) ▼  │ (back)               │
  │             Voice feedback settings              │
  │                                                 ▲
  └──────────────────(tap a history row)────────────┘

Home ──(tap Weight)──▶ Weight ──(back)──▶ Home
```
