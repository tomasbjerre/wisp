# Wisp — Specifications

Wisp is a simple GPS tracker for any activity — running, cycling, walking,
or anything else. It doesn't care what you're doing, only where you went.
It records your route, distance, and speed, and keeps a local history of
past activities.

These specs describe **what** Wisp does and the **contracts** an
implementation must satisfy. They intentionally say nothing about
frameworks, languages, or libraries, so that multiple technical
implementations (e.g. native Android, native iOS, a future desktop
companion) can all be built against the same behavior.

Implementations live in their own top-level directories (e.g. `android/`)
and must conform to these documents. If an implementation needs to deviate
from a spec, the spec should be updated first — code should never be the
source of truth for behavior.

## Documents

- [Overview](overview.md) — product scope, goals, and non-goals
- [Tracking](tracking.md) — how a session is recorded: sampling, distance,
  and speed calculations
- [Data Model](data-model.md) — the entities every implementation must
  persist, independent of storage technology
- [UI Flows](ui-flows.md) — the screens and interactions a user goes through
- [Permissions & Privacy](permissions-and-privacy.md) — what access is
  needed and how data is handled
- [Export](export.md) — exporting history as CSV, and a single activity as an image
- [Accessibility](accessibility.md) — map clarity and text contrast, everywhere
- [Voice feedback](voice-feedback.md) — optional spoken announcements
  during a live recording
- [Units](units.md) — the metric/imperial display setting
- [Heart rate](heart-rate.md) — optional recording from a BLE heart rate monitor
