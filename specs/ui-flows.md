# UI Flows

Wisp has three screens. No settings screen, no onboarding wizard, no
account/login — simplicity is a feature.

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

- The only way to leave this screen is Stop (see [Navigation](#navigation)
  below) — the system/gesture back action does nothing while a session is
  active, whether still gating on movement or already recording/paused.
  Otherwise the back action would return to Home while the session (and
  its recording) is still ongoing but no longer reachable from the UI,
  leaving it to linger unfinished — see
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
- A live stats panel below the map (not laid over it — see
  [Accessibility](accessibility.md#text-contrast)): current speed,
  elapsed distance, elapsed time — all zero while waiting for movement.
- A **Pause**/**Continue** control (labeled Continue while paused) and a
  **Stop** control, both visible together at all times once recording has
  actually started — recording or paused, it's always exactly these two
  controls, no intermediate confirmation step. While waiting for
  movement, only **Stop** is shown (there's nothing to pause yet).
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
  [Accessibility](accessibility.md#map-markers-and-route)).
- Summary stats below the map, in their own panel (see
  [Accessibility](accessibility.md#text-contrast)): date/time, nearest
  city if known (see [Data Model](data-model.md#session)), distance,
  duration, average speed, max speed.
- Below the summary stats, a list of km splits (see
  [Tracking](tracking.md#km-splits)) — one row per completed kilometer,
  its split time. Omitted entirely for a session under 1 km.
- **Back**, **Export Image** (see
  [Export](export.md#single-activity-as-an-image)), and **Delete**
  controls, in that order, below the map.
- Delete has a confirmation step before it actually deletes.

## Feedback and support

It must be clear to a user how to report feedback, problems, or feature
requests. Wisp has no in-app support flow of its own — the app surfaces a
visible, reachable link to https://github.com/tomasbjerre/wisp/issues (e.g.
from Home) that opens in the user's browser. This does not need its own
screen; a single action/icon is enough, kept out of the way of the
Start button and history list.

## Navigation

```
Home ──(tap Start)──▶ Tracking ──(tap Stop)──▶ Detail
  │                                                ▲
  └──────────────────(tap a history row)───────────┘
```
