# Design

## Context

See proposal.md for the motivation. Today the `adb` load comes from three independent callers of `AndroidSdk.deviceStates`, all wired in `AppContainer` to the same `LocalAndroidSdk` instance:

- `WorktreeBoard.checkProjects` runs every 3 seconds and calls `ProjectWorktrees.refreshDevices()` for every unchanged project, which runs `ReadEmulatorStatesUseCase` once per project.
- `MachineResources` reads the running emulators every 30 seconds for the sidebar summary, and every 5 seconds (owned states plus running emulators, so twice) while the emulators view is shown.
- `StartWorktreeEmulatorUseCase` polls every 2 seconds while an emulator boots.

Each `deviceStates` call runs `adb devices` and then `adb -s <serial> shell getprop sys.boot_completed` for every serial listed as `device`. Nothing is shared between calls, so the number of `adb` processes grows with projects × online emulators × cycles.

`refreshDevices()` also refreshes iOS simulator states (`xcrun simctl`), so a throttle placed there covers both kinds of device.

## Goals / Non-Goals

**Goals:**
- One `adb devices` per burst of reads, regardless of how many projects or views ask.
- No boot-property query for an emulator already seen booted while it stays online.
- Device states in the worktree view refreshed on their own, slower clock.

**Non-Goals:**
- Replacing polling with `adb track-devices` or any long-lived `adb` connection.
- Changing the machine view's 5-second and 30-second intervals, or the 2-second boot wait.
- Changing the CLI: each `shogun` invocation is short-lived and runs `adb` a handful of times.

## Decisions

### 1. A shared device-list reader in front of `LocalAndroidSdk`

Add `SharedDeviceStatesAndroidSdk` in `core/infrastructure`, an `AndroidSdk` decorator that delegates everything to the wrapped SDK except `deviceStates`. For `deviceStates(sdk)` it keeps, per `sdk.adb` path, the last result and when it was read, and holds a `Mutex` around the read: a caller that arrives while a read is running waits for it and gets its result, and a caller that arrives less than 2 seconds after the last read gets the cached result. `stopEmulator` drops the cached result for that `adb`, so the next read after a stop is fresh. The clock is injected (`() -> Instant`, as `WorktreeBoard` does) so tests drive the window.

`AppContainer` wraps its single `LocalAndroidSdk` in this decorator, so the board, the machine view, the per-project use cases and the start loop all share it. The CLI keeps using `LocalAndroidSdk` directly.

- The 2-second window is shorter than every caller's own interval (2 s boot wait measured from the end of the previous read, 3 s board check, 5 s machine view), so no caller ever sees a result older than one of its own cycles; it only collapses reads that happen in the same moment.
- *Alternative: group projects by SDK inside `WorktreeBoard` and read once per cycle.* Rejected: it only covers the board, not the machine view, and pushes `adb` knowledge into a view model.
- *Alternative: `adb track-devices` stream.* Rejected for now: it needs a long-lived process with restart and parsing logic, and boot completion still needs `getprop`. The shared reader gets most of the benefit with a small change.

### 2. Boot-completed cache inside `LocalAndroidSdk`

`LocalAndroidSdk` remembers the serials whose `sys.boot_completed` returned `1` (a thread-safe set). On every `deviceStates` call it first drops the serials that aren't listed as `device` in the new `adb devices` output, then queries `getprop` only for `device` serials not in the set. `stopEmulator` removes the serial. This lives in the adapter, not the decorator, because it's about how a state is derived from `adb` output, and it's harmless in the CLI.

- *Alternative: cache in the decorator.* Rejected: the decorator would have to re-implement the state mapping that already lives in `LocalAndroidSdk`.

### 3. Device refresh throttle in `WorktreeBoard`

`WorktreeBoard` keeps, per project entry, when its devices were last refreshed by a check (using its injected `clock`). In `checkProjects` it still detects agents for every unchanged project, but calls `holder.refreshDevices()` only when that project's devices were never refreshed or were refreshed 10 or more seconds ago. The interval is a constructor parameter (`deviceRefreshInterval`, default 10 seconds), like `autoRefreshInterval`.

The immediate refreshes stay where they are today and don't go through the throttle: `ProjectWorktrees.startEmulator`/`stopEmulator` (and the simulator equivalents) reload device states when the action ends, a list reload runs `loadEmulatorSection`, and the machine view's `onDevicesChanged` calls `refreshDevices()` directly.

- *Alternative: a separate 10-second loop for devices.* Rejected: it would duplicate the board's watching rules (only shown projects, skip busy projects, stop when leaving the screen) that `checkProjects` already applies.

## Risks / Trade-offs

- [An emulator closed and restarted on the same port between two reads is never seen absent, so it's reported running while it reboots] → Restarts take far longer than the reads that see it offline or missing; a stop through ShogunAi always clears the cache; the next boot-property query happens as soon as it's seen absent once.
- [An emulator closed from its own window shows as running for up to 10 seconds in the worktree view] → Accepted; the spec bounds it, and actions on it fail gracefully (stopping a stopped emulator succeeds).
- [Two SDKs on the same machine] → The shared reader caches per `adb` path, so projects pointing at different SDKs never share a result.
- [A slow `adb devices` holds the mutex] → Callers wait for the same read they'd otherwise duplicate; no extra delay compared with running their own.

## Migration Plan

No data or configuration changes. Verify by running the desktop app with a project that declares the emulator provider and two emulators running, and counting `adb` executions over a minute (`sudo eslogger exec` filtered on `platform-tools/adb`) before and after the change.
