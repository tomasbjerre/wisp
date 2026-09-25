# Wisp — Android

Native Android implementation (Kotlin + Jetpack Compose) of the spec in
[`../specs`](../specs/README.md). If you're about to change behavior, check
the spec first — it's the source of truth, not this code.

## Stack

- Kotlin + Jetpack Compose (Material 3), no XML layouts.
- [Room](https://developer.android.com/training/data-storage/room) for local
  storage (`data/`).
- [FusedLocationProviderClient](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient)
  for GPS, in a foreground [`Service`](app/src/main/java/com/github/tomasbjerre/wisp/location/TrackingService.kt)
  so recording survives the screen turning off.
- [osmdroid](https://github.com/osmdroid/osmdroid) (OpenStreetMap) for the
  map view — no API key needed.

## Local development setup

1. Install [Android Studio](https://developer.android.com/studio) (Ladybug
   or newer). It bundles a JDK; no separate Java install needed.
2. Open this `android/` directory in Android Studio (**not** the repo
   root) — `File > Open`.
3. Let Android Studio's first-run Gradle sync finish. It will prompt to
   install the Android SDK platform/build-tools this project needs
   (`compileSdk 35`, `minSdk 26`) — accept that.
4. Run the `app` configuration on an emulator or a physical device (USB
   debugging enabled) via the ▶ button, or:

   ```bash
   ./gradlew installDebug
   ```

No API keys, accounts, or backend setup are required to build and run.

### Command line only (no Android Studio)

```bash
export ANDROID_HOME=/path/to/android/sdk   # needs platform-tools, platforms;android-35, build-tools;35.0.0
./gradlew assembleDebug                     # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest                 # unit tests — see "Testing" below
./gradlew installDebug                      # installs on a connected device/emulator
```

## Code quality

These all run in CI (`.github/workflows/ci_android.yml`) and locally:

```bash
./gradlew ktlintCheck   # Kotlin style (ktlintFormat to auto-fix)
./gradlew detekt        # Kotlin static analysis (config/detekt.yml)
./gradlew lintDebug     # Android Lint
./gradlew spotlessCheck # formatting for non-Kotlin files (spotlessApply to auto-fix)
./gradlew violations    # aggregates the above into one summary; fails on ERROR-level findings
                         # — see https://github.com/tomasbjerre/violations-gradle-plugin
```

### Keeping dependencies current

```bash
./gradlew showUpdateableDependencies   # what's outdated
./gradlew updateDependencies           # bump build.gradle.kts in place
```

From [update-versions-gradle-plugin](https://github.com/tomasbjerre/update-versions-gradle-plugin).
Dependency versions are plain literals in `build.gradle.kts` (no
`libs.versions.toml`) specifically so this plugin can rewrite them — it
doesn't support version catalogs. Renovate (`../renovate.json`) also keeps
this repo's dependencies current via PRs; see that file for the few
dependencies that are intentionally excluded because they can't be bumped
in isolation (e.g. `kotlinx-coroutines-test`, which must move in lockstep
with the pinned Kotlin version).

## Testing

No mocking libraries (Mockito, MockK, etc.) — tests exercise real behavior
against real collaborators instead of mocked ones, so a passing test means
the requirement actually works, not that a mock was told to say so:

- **Pure logic** (`GeoUtils`, `TrackRecorder`, `Formatting`) — plain JUnit
  Jupiter tests, no framework dependencies. `TrackRecorder` in particular
  holds all the GPS-fix filtering and segment/pause bookkeeping from
  `specs/tracking.md`, extracted out of `TrackingService` specifically so
  it's testable without an Android device or emulator.
- **Storage** (`SessionRepositoryTest`) — runs against a real in-memory
  SQLite database via Room + [Robolectric](http://robolectric.org/), not a
  mocked DAO. It verifies every query listed in
  `specs/data-model.md#required-queries` directly.
- **Schema migrations** (`WispDatabaseMigrationTest`) — see
  `specs/data-model.md#data-integrity-on-start`: a schema change must carry
  existing data forward, not silently drop it. Tested by seeding a real,
  file-based database at the *old* schema version (a small `@Database`
  declared just for the test, mirroring what `WispDatabase` actually was at
  that version — not an exported-schema fixture), then reopening the same
  file through the real, current `WispDatabase.build` — the exact
  production path, migration included — and asserting the old data is
  still there. Add one of these alongside every future `Migration`.
- Assertions use [AssertJ](https://assertj.github.io/doc/).

Run them with `./gradlew testDebugUnitTest`.

When you add a requirement to `specs/`, add a test for it here — see
[`../AGENTS.md`](../AGENTS.md).

### Screenshots

`ScreenshotTest` (`app/src/androidTest`) is not a correctness test — it
drives the real app against seeded data (not live GPS) to capture the
screenshots used in the root README and the Play Store listing. Run it
against a connected device/emulator with:

```bash
./gradlew connectedDebugAndroidTest
adb pull /sdcard/wisp-screenshots .
```

Each instrumented test runs through the Android Test Orchestrator with
`clearPackageData`, so it starts from an empty database rather than whatever
the tests before it seeded or recorded. Tests that open "the newest history
row" (`onFirst()`) rely on this to get their own seeded session (see #93).

The `release_android` workflow runs this automatically on every release (see
below), re-encodes each capture as a JPEG (`screencap` only writes PNG,
and an uncompressed PNG of a satellite map capture runs several MB —
JPEG at this content is visually indistinguishable and a fraction of the
size, which matters since these are committed to git history on every
release), and commits the result to `docs/screenshots/` and
`app/src/main/play/listings/en-US/graphics/phone-screenshots/`. Every
screen/state in `specs/ui-flows.md` should have an entry here — if you add
a feature that changes what's on screen, add a capture for it in
`ScreenshotTest` (and `InstructionVideoTest`, below) in the same change.

#### Running it against a local emulator

No physical device needed. One-time setup, using the SDK already pointed
to by `android/local.properties`'s `sdk.dir`:

```bash
SDK=$(sed -n 's/sdk.dir=//p' local.properties)   # run from android/
export ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK"
export PATH="$SDK/platform-tools:$PATH"

# Install the same image CI uses (api-level 35, google_apis, x86_64) if you
# don't have it yet:
"$SDK/cmdline-tools/"*/bin/sdkmanager --install "system-images;android-35;google_apis;x86_64"

echo no | "$SDK/cmdline-tools/"*/bin/avdmanager create avd \
  -n wisp_test -k "system-images;android-35;google_apis;x86_64" -d pixel_6
```

Boot it (headless — no window needed) and wait for it to come up:

```bash
"$SDK/emulator/emulator" -avd wisp_test -no-window -gpu swiftshader_indirect \
  -noaudio -no-boot-anim -camera-back none &
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done
```

Fix a location so Tracking has a real position instead of hanging on
"Finding your location…" (see `specs/tracking.md#start-gating`):

```bash
adb emu geo fix 18.0686 59.3293
```

`ScreenshotTest`'s live-Tracking captures (recording/paused/satellite) need
actual movement, not just a fixed point — Wisp only starts the clock once a
fix implies at least walking pace (`specs/tracking.md#start-gating`). Feed
it a slow walk north while the instrumented test runs, e.g. in a background
loop:

```bash
lat=59.3293
for i in $(seq 1 60); do
  lat=$(echo "$lat + 0.000045" | bc)   # ~5m north per step
  adb emu geo fix 18.0686 "$lat"
  sleep 2
done &
```

Then run the test and pull the results as above. Kill the emulator
(`adb -s emulator-5554 emu kill`) when done — it has no separate "stop"
command.

## Play Store release

Releasing is two manual clicks, in order:

1. [`.github/workflows/release.yml`](../.github/workflows/release.yml)
   (`workflow_dispatch`) — implementation-agnostic, and lives at the repo
   root rather than here because it isn't Android-specific:
   - Determines the next version from [Conventional
     Commits](../AGENTS.md) since the last tag
     ([git-changelog-command-line](https://github.com/tomasbjerre/git-changelog-command-line)).
   - Prepends a section to `../CHANGELOG.md`, commits it, and tags the
     release (`vX.Y.Z`).
   - Publishes a GitHub Release with notes generated from the same commits
     ([git-changelog-github-release](https://github.com/tomasbjerre/git-changelog-github-release)).
2. [`.github/workflows/release_android.yml`](../.github/workflows/release_android.yml)
   (`workflow_dispatch`) — builds and ships the Android app for whatever
   tag `release.yml` just created (it doesn't create a tag or touch the
   changelog itself):
   - Uses the latest tag in the repo as the version.
   - Captures fresh screenshots on an emulator and commits them.
   - Records a short screen capture of the same emulator run walking
     through Home → a past session's Detail → starting and stopping a new
     session ([`InstructionVideoTest`](app/src/androidTest/java/com/github/tomasbjerre/wisp/InstructionVideoTest.kt)),
     and attaches it to the GitHub Release as `instruction-video.mp4`.
   - Builds a signed App Bundle and APK, uploads the bundle to the Play
     Console's **internal** track
     ([Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher)),
     and attaches both (plus the video above) to the GitHub Release for
     that tag.

Promoting a release from internal → production is a manual step in the
[Play Console](https://play.google.com/console) — intentionally not
automated, so a real person always looks at a release before it reaches
real users.

### Store listing's instruction video

Play Console's listing asks for a video ("Instruktionsvideo") that clearly
shows the app in use — but that field only accepts a **YouTube link**,
there's no direct file upload, so this can't be fully automated end to
end. After a release:

1. Download `instruction-video.mp4` from the GitHub Release.
2. If it's worth updating the public listing (first release, or the UI
   changed enough to matter — not necessarily every release), upload it
   to a YouTube video you control and copy its URL.
3. Paste that URL into the store listing's video field in Play Console.

### One-time setup (not automatable — Google account actions)

1. Create a [Play Console](https://play.google.com/console) developer
   account (one-time $25 fee, identity verification).
2. Create the app in the Console (package name `com.github.tomasbjerre.wisp`,
   permanent once chosen) and complete its first store listing, content
   rating, and data safety form — Google requires this once per app before
   the API can publish to it. For the store listing's privacy policy URL,
   use the GitHub-rendered link to [`../PRIVACY.md`](../PRIVACY.md), e.g.
   `https://github.com/tomasbjerre/wisp/blob/main/PRIVACY.md`.
3. Create a Google Cloud project, enable the Android Publisher API, create
   a service account with a JSON key, and grant it publishing access to
   this app in Play Console → Users and permissions. Full steps:
   [GPP's Service Account guide](https://github.com/Triple-T/gradle-play-publisher#service-account).
4. Add these repo secrets (`Settings → Secrets and variables → Actions`):
   - `PLAY_SERVICE_ACCOUNT_JSON` — the full JSON key from step 3.
   - `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
     `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` — the app signing
     (upload) key. Already set for this repo; back up
     `~/.keystores/wisp/upload-keystore.jks` somewhere durable — it's
     the only copy and losing it means losing the ability to publish
     updates under this app's identity.
