# Voice feedback

Optional spoken announcements during a live [Tracking](ui-flows.md#2-tracking-active-recording)
session, read aloud through the device's normal audio output — so
someone wearing earbuds/headphones can get progress updates without
looking at their phone. See [Overview](overview.md#design-principle) for
why this is configurable at all, unlike almost everything else in Wisp.

## Settings

- **Voice feedback** — a single master switch. Off by default: with it
  off, Wisp never speaks, and nothing else on this page matters.
- Five independent switches choose what's included in each announcement
  (below). Four are on by default the first time voice feedback is turned
  on (the fifth, **Elapsed time per kilometer**, is off by default — the
  average speed switch already says how long the kilometer took, as a
  pace, so turning this on too by default would repeat it):
  - **Kilometers completed** — the running count of complete kilometers.
  - **Average speed per kilometer** — labeled to make clear this is the
    kilometer that was just completed, not an overall average. Said as a
    pace (time per unit distance, e.g. "5 minutes 13 seconds per
    kilometer"), matching how Wisp expresses this everywhere else it
    appears (the live "Last kilometer"/"Fastest kilometer" stats and the
    [Km splits](ui-flows.md#4-km-splits) view), not a km/h or mph figure.
  - **Steps per kilometer** — labeled to make clear this is the
    kilometer that was just completed, not a running total (see
    [Tracking](tracking.md#step-count)). Never included when the session
    has no step count at all, regardless of this switch — there's
    nothing to say.
  - **Elapsed time per kilometer** — how long the kilometer that was just
    completed took, as a plain duration (its split time, see
    [Km splits](tracking.md#km-splits)) rather than the pace the average
    speed switch says. Off by default.
  - **Total elapsed time** — total time since the session started, not
    just the last kilometer's.
- All six switches persist locally across app restarts (the one piece
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

Whichever of the five switches above are on, said together as one
announcement, in this order: kilometers completed, average speed over
that kilometer, steps over that kilometer, that kilometer's elapsed time,
total elapsed time. The two elapsed times are worded so they can't be
mistaken for each other when both are on (e.g. "kilometer time 5 minutes
0 seconds. Total time 27 minutes 10 seconds"). If every switch
happens to be off (master on, nothing to announce with it), nothing is
said. Exact phrasing is an implementation detail; the values themselves
(which kilometer just completed, that kilometer's own average speed,
steps and elapsed time, the session's total elapsed time) are the contract.
