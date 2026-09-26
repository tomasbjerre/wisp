# Voice feedback

Optional spoken announcements during a live [Tracking](ui-flows.md#2-tracking-active-recording)
session, read aloud through the device's normal audio output — so
someone wearing earbuds/headphones can get progress updates without
looking at their phone. See [Overview](overview.md#design-principle) for
why this is configurable at all, unlike almost everything else in Wisp.

## Settings

- **Voice feedback** — a single master switch. Off by default: with it
  off, Wisp never speaks, and nothing else on this page matters.
- Four independent switches choose what's included in each announcement
  (below), all on by default the first time voice feedback is turned on:
  - **Kilometers completed** — the running count of complete kilometers.
  - **Average speed** — over the kilometer that was just completed.
  - **Steps** — taken over the kilometer that was just completed (see
    [Tracking](tracking.md#step-count)). Never included when the session
    has no step count at all, regardless of this switch — there's
    nothing to say.
  - **Elapsed time** — total time since the session started, not just
    the last kilometer's.
- All five switches persist locally across app restarts (the one piece
  of durable user-facing configuration in Wisp — see
  [Overview](overview.md#design-principle)). Like session history, this
  never syncs anywhere.
- Reached via a settings control on [Tracking](ui-flows.md#2-tracking-active-recording)'s
  screen, opening a **Voice feedback settings** view over it. Not offered
  from Home or Detail — voice feedback only ever applies to a live
  recording, so there's nothing for it to configure from either of those.
- Changing a switch takes effect immediately for the current session (if
  one is running), not just the next one — there's no reason to make
  someone stop and restart to pick up a change.

## When an announcement happens

- Exactly once per newly completed kilometer (see
  [Km splits](tracking.md#km-splits)) — never on a separate timer, never
  for the trailing partial kilometer, and never twice for the same
  kilometer.
- Only while a session is actively recording — the same "movement
  confirmed, not paused" condition already gating the live stats panel
  (see [Start gating](tracking.md#start-gating)). No backlog of
  announcements is queued up for kilometers completed while voice
  feedback happened to be off; turning it on mid-session only affects
  kilometers completed from then on.
- If a device has no usable text-to-speech engine (none installed, or it
  fails to initialize), voice feedback is silently unavailable — this
  never blocks, delays, or errors the recording itself. Nothing on
  screen needs to say so; the settings still show as configured, they
  just have nothing to speak through.

## What's said

Whichever of the four switches above are on, said together as one
announcement, in this order: kilometers completed, average speed over
that kilometer, steps over that kilometer, elapsed time. If every switch
happens to be off (master on, nothing to announce with it), nothing is
said. Exact phrasing is an implementation detail; the values themselves
(which kilometer just completed, that kilometer's own average speed and
steps, the session's total elapsed time) are the contract.
