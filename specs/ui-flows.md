# UI Flows

Wisp has three main screens — Home, Tracking, Detail — plus a Km splits
view reached from Detail. No settings screen, no onboarding wizard, no
account/login — simplicity is a feature.

Whenever a screen/view listed here is added, renamed, or removed, update
the "which screen" explainer and dropdown in
[`.github/ISSUE_TEMPLATE/`](../.github/ISSUE_TEMPLATE/) (feature_request,
bug_report, support) to match — those exist so issue reporters can name
the right screen, and go stale silently otherwise.

## 1. Home

The app's entry point.

- A prominent **Start** button/action, always available when idle.
- Below it, a list of past sessions (most recent first), each row showing:
  date/time, nearest city if known (see
  [Data Model](data-model.md#session)), distance, duration, average speed.
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
- A live stats panel below the map (not laid over it — see
  [Accessibility](accessibility.md#text-contrast)): current speed,
  elapsed distance, elapsed time — all zero while waiting for movement.
  Also steps per minute (see [Tracking](tracking.md#step-count)) and the
  most recently completed kilometer split (see
  [Tracking](tracking.md#km-splits)) — each omitted until there's one to
  show, same as Detail. Unlike Detail, only the latest split is shown,
  not the full list — there's no room for a growing list on this screen,
  and "how was that last km" is what's actually useful mid-run.
- A **Pause**/**Continue** control (labeled Continue while paused) and a
  **Stop** control, both visible together at all times once recording has
  actually started — recording or paused, it's always exactly these two
  controls, no intermediate confirmation step. While waiting for
  movement, only **Stop** is shown (there's nothing to pause yet). Also
  flips to paused on its own after a sustained stop (see
  [Tracking](tracking.md#auto-pause)) — same controls either way.
- Tapping Stop finalizes the session immediately and navigates to that
  session's Detail screen — unless movement was never confirmed (see
  [Tracking](tracking.md#start-gating)), in which case the session is
  discarded and Stop returns to Home instead, since there's no Detail
  screen worth showing for it.
- If location permission is missing or denied, this screen must explain
  what's needed and offer a way to grant it, rather than silently
  recording nothing (see [Permissions & Privacy](permissions-and-privacy.md)).
- If Wisp isn't exempt from battery optimization, this screen says so and
  offers a way to fix it (see
  [Permissions & Privacy](permissions-and-privacy.md#required-access)) —
  advisory, not blocking: recording still works either way.

## 3. Detail (a past or just-finished session)

- A map showing the full recorded route, with start and end markers (see
  [Accessibility](accessibility.md#map-markers-and-route)), and the same
  standard/satellite toggle described under [Tracking](#2-tracking-active-recording)
  above, over a corner of the map.
- Summary stats below the map, in their own panel (see
  [Accessibility](accessibility.md#text-contrast)): date/time, nearest
  city if known (see [Data Model](data-model.md#session)), distance,
  duration, average speed, max speed, steps per minute (see
  [Tracking](tracking.md#step-count) — omitted entirely when the session
  has no step count).
- Below the summary stats, a **Km splits (N)** link — N being the number
  of complete kilometers — opening the [Km splits](#4-km-splits) view.
  The splits themselves aren't listed here: this panel shares the screen
  with the map, and a list long enough to be useful would take the map's
  room. Omitted entirely for a session under 1 km.
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
- A table, one row per complete kilometer in order, numbered from 1:
  - the split time,
  - the average speed over that kilometer,
  - the number of steps taken over that kilometer (see
    [Tracking](tracking.md#km-splits)) — the whole column is omitted
    when the session has no steps per split,
  - a bar whose length is that speed relative to the fastest row's — so
    faster and slower stretches stand out at a glance, by length rather
    than by color (see [Accessibility](accessibility.md)).
- After those, the partial km (if any), labeled with its distance (e.g.
  `+0.40`) and visually set apart from the full kilometers, since its
  time (and steps) cover a shorter distance than theirs; its speed and
  bar are directly comparable.
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
  │                                                ▲
  └──────────────────(tap a history row)───────────┘
```
