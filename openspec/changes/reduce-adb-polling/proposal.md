# Proposal

## Why

While the desktop app is open, every 3-second worktree check runs `adb devices` once per project that declares the emulator provider, plus `adb -s <serial> shell getprop sys.boot_completed` for every online emulator, again per project. With a few Android projects and a couple of emulators running that's several `adb` processes per second for as long as the app stays open, which shows up as a steady `adb` load on the machine even when nothing is changing.

## What Changes

- Concurrent or back-to-back reads of the emulator device list are served by a single `adb devices` call: all the projects checked in the same cycle, and the machine view's summary and list, share it instead of each running their own.
- Once an emulator's boot has been seen as completed, its boot property isn't queried again while it stays online; it's queried again only after the emulator leaves the device list or is stopped through ShogunAi.
- The worktree view refreshes device states (Android emulators and iOS simulators) at most every 10 seconds instead of on every 3-second check, while still refreshing them right after a Start or Stop and when a project's worktree list reloads.
- Starting an emulator keeps polling every 2 seconds until it's running, as today.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `emulator-provider`: adds a requirement that bounds how often `adb` is queried to report emulator states (shared device list, boot property only until boot is observed).
- `worktree-auto-refresh`: adds a requirement that device states refresh at a slower pace than the Git metadata and agent checks, and immediately after device actions.

## Impact

- `core`: `LocalAndroidSdk` (boot cache, invalidation on stop) and a new shared device-list reader used by the desktop app's `AndroidSdk` wiring.
- `desktopApp`: `AppContainer` (one shared `AndroidSdk` instance wrapped by the shared reader), `WorktreeBoard` / `ProjectWorktrees` (device refresh throttle).
- No change to the CLI's observable behavior, `.shogunai.json`, settings or the machine view's 5-second and 30-second intervals.
