# Working in this repository

Wisp keeps its specification separate from its implementation on purpose —
see [`specs/README.md`](specs/README.md). This is so Wisp can eventually
have multiple technical implementations (Android today, iOS later, maybe
others) that all behave the same way.

## Rule: specs first

Any new requirement — a new feature, a changed behavior, a new constraint —
must be added or updated in `specs/` as part of the same change, before or
alongside touching `android/` (or any other implementation). This applies
regardless of which platform prompted the requirement: something asked for
"for Android" still belongs in the platform-agnostic spec if it's a
behavior, not an Android-specific implementation detail.

- If a change is purely a technical/implementation detail with no
  user-visible or contract-level behavior difference (e.g. swapping a
  library, refactoring), it does not need a spec change.
- If you're unsure whether something is a spec-level requirement or an
  implementation detail, prefer adding it to `specs/` — it's cheap to
  document and expensive to lose track of.
- Never let `android/` (or any implementation) be the only place a behavior
  is defined. If you find implementation behavior that isn't reflected in
  `specs/`, fix that by updating the spec, not by leaving it undocumented.

## Rule: test behavior, not mocks

Every requirement in `specs/` should have a test that verifies the actual
requirement, not an implementation detail. See
[`android/README.md#testing`](android/README.md#testing) for how this
works in the Android implementation.

- No mocking libraries (Mockito, MockK, etc.). Test against real
  collaborators — a real in-memory database, a pure function fed real
  input — not a mock told what to return. A passing test should mean the
  requirement works, not that a mock was configured correctly.
- If a class is hard to test without mocks, that's usually a sign it's
  doing too much — prefer extracting the pure/testable logic (see
  `TrackRecorder` in the Android implementation) over reaching for a mock.
- When you add or change a requirement in `specs/`, add or update the test
  that covers it in the same change.

## Layout

- `specs/` — what Wisp does, independent of any implementation.
- `android/` — the native Android (Kotlin + Jetpack Compose) implementation.
