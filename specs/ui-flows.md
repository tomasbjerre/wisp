# UI Flows

Wisp has three screens. No settings screen, no onboarding wizard, no
account/login — simplicity is a feature.

## 1. Home

The app's entry point.

- A prominent **Start** button/action, always available when idle.
- Below it, a list of past sessions (most recent first), each row showing:
  date/time, distance, duration, average speed.
- Tapping a row opens that session's **Detail** screen.
- An empty state ("No activities yet — tap Start to record your first
  route.") when there is no history.
- If there's an interrupted session recovered on launch (see
  [Tracking](tracking.md)), it simply appears in the list like any other
  finished session — no special dialog or interruption.

## 2. Tracking (active recording)

Entered by tapping Start on Home. Stays active in the background/locked
screen while recording.

- A map filling most of the screen, centered on the current location,
  drawing the route as it's recorded.
- Live stats overlay: current speed, elapsed distance, elapsed time.
- **Pause/Resume** control and a **Stop** control.
- Stopping navigates to that session's Detail screen (the session is now
  finished).
- If location permission is missing or denied, this screen must explain
  what's needed and offer a way to grant it, rather than silently
  recording nothing (see [Permissions & Privacy](permissions-and-privacy.md)).

## 3. Detail (a past or just-finished session)

- A map showing the full recorded route.
- Summary stats: date/time, distance, duration, average speed, max speed.
- A **Delete** action, with a confirmation step before it actually
  deletes.

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
