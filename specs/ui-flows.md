# UI Flows

Wisp has three main screens — Home, Tracking, Detail — plus a Km splits
view reached from Detail, and a Settings view reached from Tracking,
housing the three deliberate, narrow configuration exceptions
[Overview](overview.md#design-principle) describes: voice feedback (see
[Voice feedback](voice-feedback.md)), the heart rate monitor setting,
and weight. No onboarding wizard, no account/login — simplicity is a
feature, and this is not a precedent for adding more settings elsewhere.

Whenever a screen/view listed here is added, renamed, or removed, update
the "which screen" explainer and dropdown in
[`.github/ISSUE_TEMPLATE/`](../.github/ISSUE_TEMPLATE/) (feature_request,
bug_report, support) to match — those exist so issue reporters can name
the right screen, and go stale silently otherwise.

## 1. Home

The app's entry point.

- A prominent **Start** button/action, always available when idle.
- A list of past sessions (most recent first), each row showing:
  date/time, place name if known (see
  [Data Model](data-model.md#place-name)), the activity type if the session
  has one (see [Calories burned](calories.md#activity-type)), distance,
  duration, average speed, and calories when the session has them (see
  [Calories burned](calories.md#where-it-is-shown)). The type is only
  displayed here — it is chosen on Tracking.
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

## 2. Tracking (active recording)

Entered by tapping Start on Home. Stays active in the background/locked
screen while recording.

- The system/gesture back action asks for confirmation first, in a dialog
  titled **Stop recording?**, with **Stop** and **Keep recording** — but
  only once recording has actually started (Force start tapped, or
  movement confirmed naturally — see
  [Tracking](tracking.md#start-gating)). It never stops a session that has
  started ticking by itself, so a stray back tap can't end a recording
  with real data in it.
  - While still waiting for movement — before Force start, before
    movement is naturally confirmed — back instead stops immediately, the
    same as tapping Stop, with no confirmation step: nothing has been
    recorded yet, so there's nothing to lose by asking first.
  - **Keep recording** (or dismissing the dialog, e.g. with back again)
    changes nothing: the session carries on and Tracking stays open.
  - **Stop** — from the dialog, the Stop control itself, or back while
    still waiting for movement — behaves exactly like tapping Stop (see
    [Navigation](#navigation) below) — same finalize-or-discard logic, same
    destination (that session's Detail screen, or Home if movement was
    never confirmed).
  - The dialog says the activity will be saved — it only ever shows once
    recording has actually started, so there's no separate "will be
    discarded" wording to show here (that's the no-dialog case above).
  - Back never simply returns to Home with the recording left running:
    leaving the session ongoing but no longer reachable from the UI would
    leave it to linger unfinished — see
    [Tracking](tracking.md#what-must-survive-interruption). This still
    holds while waiting for movement: back stops (and, since nothing was
    recorded yet, discards) the session rather than leaving it running
    unreachable.
  - The Stop control itself is unchanged: it stops at once, without a
    confirmation step.
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
- An **activity type** choice — **Walking**, **Running** or **Cycling** (see
  [Calories burned](calories.md#activity-type)) — in the stats panel,
  available in every state (waiting for movement, recording, paused). It
  starts on the type of the user's last activity (see
  [Calories burned](calories.md#activity-type)) and changing it takes
  effect immediately, including on the calories shown. Unlike weight and
  the heart rate monitor setting (below), this stays directly on
  Tracking rather than on Settings — it's changed often enough mid-run
  (a walk that turns into a run) that a sub-screen round trip would be
  the wrong tradeoff.
- A settings control over another corner of the map opens
  [Settings](#2a-settings) — weight (see
  [Calories burned](calories.md#weight)), the heart rate monitor setting
  (see [Heart rate](heart-rate.md#setting)), and voice feedback (see
  [Voice feedback](voice-feedback.md)).
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
  setting (on [Settings](#2a-settings) — see
  [Heart rate](heart-rate.md#setting)) is on (see
  [Heart rate](heart-rate.md#display)), and the calories burned so far
  when the session has them (see
  [Calories burned](calories.md#where-it-is-shown)).
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
  place, so it's always exactly two controls regardless of state. A
  **"Waiting for movement"** line above the controls, with an
  information icon next to it, opens a dialog explaining that recording
  starts automatically once moving, and that Force start begins it
  immediately instead — kept out of a permanently-visible paragraph so
  the stats panel stays about the numbers, not instructions.
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

## 2a. Settings

Opened from the settings control on [Tracking](#2-tracking-active-recording)'s
map, over that screen. The one place weight, the heart rate monitor
setting, and voice feedback are all configured — see
[Calories burned](calories.md#weight), [Heart rate](heart-rate.md#setting)
and [Voice feedback](voice-feedback.md) for their full contracts. None of
these make sense from Home or Detail: they only ever affect a live
recording.

- A title and a back control returning to Tracking (system back does the
  same).
- A **weight** field (see [Calories burned](calories.md#weight)) —
  labelled with its unit (kg, or lb under imperial), reflects and
  immediately persists what is typed — no separate Save action — and
  takes effect on the session being recorded right away. A value that is
  not a positive number is not stored; clearing the field removes the
  weight and turns calories off for this session.
- A **Heart rate monitor** switch, off by default (see
  [Heart rate](heart-rate.md#setting)); disabled when no monitor is
  available.
- A **Vibrate while paused** switch, on by default (see
  [Tracking](tracking.md#paused-session-reminder)) — the reminder pulses
  that tell someone a paused session is still open.
- A **Voice feedback** master switch.
- Five switches choosing what each announcement includes: **Kilometers
  completed**, **Average speed per kilometer**, **Steps per kilometer**,
  **Elapsed time per kilometer**, **Total elapsed time**.
- Every field/switch on this screen reflects and immediately persists
  its current setting — no separate Save action.
- The screen scrolls when the controls don't all fit: every field and
  switch must stay reachable on any screen size — a row added later must
  never push the ones below it off a small screen.

## 3. Detail (a past or just-finished session)

- An **Export** action in the top bar (see [Export](export.md)), offering
  a choice of CSV or Image before the Share/Save to device choice every
  export in Wisp offers — one entry point for both formats, the same
  pattern [Home](#1-home)'s own Export CSV action uses, rather than a
  separate button per format taking up room below.
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
  omitted when the session has none), the activity type (see
  [Calories burned](calories.md#activity-type) — omitted when the session
  has none, and not editable here), and calories burned (see
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
- Below that, one row of two controls: **Back** and **Delete**.
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

Launching or reopening the app while a session is being recorded opens
Tracking directly instead of Home — the same destination tapping the
recording notification goes to (see
[Permissions & Privacy](permissions-and-privacy.md#required-access)) —
regardless of whether the app was opened via that notification, the
launcher icon, or the task switcher. Landing on Home first, with no other
cue a recording exists, would read as the session having stopped on its
own.

```
Home ──(tap Start)──▶ Tracking ──(tap Stop)──▶ Detail ──(tap Km splits)──▶ Km splits
  │                       │  ▲                    ▲
  │                       │  │                     │
  │        (tap settings) ▼  │ (back)               │
  │                    Settings                     │
  │                                                 ▲
  └──────────────────(tap a history row)────────────┘
```
