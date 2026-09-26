# Heart rate

Wisp can, optionally, record the user's heart rate during a session from
a Bluetooth Low Energy (BLE) heart rate monitor — a chest strap, an arm
band, a watch broadcasting heart rate — so it can be seen live on
[Tracking](ui-flows.md#2-tracking-active-recording) and its maximum on
[Detail](ui-flows.md#3-detail-a-past-or-just-finished-session).

## Design principle exception

See [Overview](overview.md#design-principle). Whether someone owns a
heart rate monitor, and whether they want Wisp reaching out over Bluetooth
for it, has no correct default for everyone — and connecting to a
Bluetooth device is something a user should opt into, not have happen
silently. So, like [Voice feedback](voice-feedback.md) and
[Units](units.md), this is a deliberate, narrow exception. It is **off by
default**.

## Setting

A single on/off switch, **Heart rate monitor**, on
[Home](ui-flows.md#1-home), persisted across launches like the other
settings (see [Data Model](data-model.md#heart-rate-setting)). Turning it
on asks for the Bluetooth access it needs (see
[Permissions & Privacy](permissions-and-privacy.md#required-access));
if that's declined the switch stays off.

## Connecting

- Uses the standard BLE **Heart Rate Service** (assigned number `0x180D`)
  and its **Heart Rate Measurement** characteristic (`0x2A37`), so any
  monitor implementing the standard works without pairing to a specific
  brand.
- While a recording is active (see
  [Start gating](tracking.md#start-gating) — connecting starts together
  with the timer and track) and the setting is on, Wisp scans for a
  device advertising the Heart Rate Service, connects to it, and
  subscribes to its measurements. If more than one is in range, the one
  with the strongest signal is used.
- If the connection is lost mid-session, Wisp keeps trying to find and
  reconnect to a monitor until the session ends.
- Best-effort, like [Step count](tracking.md#step-count): with the setting
  off, no monitor in range, no Bluetooth, or no permission, recording
  works exactly as it does without this feature and that session simply
  has no heart rate. Nothing in the UI treats that as an error.
- Nothing is read or recorded while a session is paused, and nothing after
  it ends.

## Reading a measurement

The Heart Rate Measurement characteristic starts with a flags byte; bit 0
selects whether the heart rate value that follows is an unsigned 8-bit
(`0`) or unsigned 16-bit little-endian (`1`) integer, in beats per
minute. A reading of 0 is not a valid heart rate and is ignored, as is a
value that doesn't fit the format.

## Recording

- The **current heart rate** is the most recent valid reading. If no
  reading has arrived for 10 seconds it's considered lost — nothing is
  shown for it, and points recorded meanwhile have no heart rate — until
  the next reading arrives.
- Each recorded [TrackPoint](data-model.md#trackpoint) stores the current
  heart rate at that moment (null when there is none).
- The **maximum heart rate** is the highest valid reading during the
  session, excluding paused time. It's stored on the
  [Session](data-model.md#session) when it finishes; null when no reading
  was ever received.
- A session recovered after an interruption (see
  [What must survive interruption](tracking.md#what-must-survive-interruption))
  has its maximum recomputed from its points' heart rates, since there's
  no live monitor to read it from.

## Display

- [Tracking](ui-flows.md#2-tracking-active-recording): the current heart
  rate and the maximum so far, in bpm. Omitted entirely when the setting
  is off; while on but with no current reading, shown as a placeholder
  rather than hidden, so the user can tell Wisp is still looking for a
  monitor.
- [Detail](ui-flows.md#3-detail-a-past-or-just-finished-session): the
  session's maximum heart rate, in bpm. Omitted when the session has none.
- Not affected by [Units](units.md): beats per minute is the same
  everywhere.
