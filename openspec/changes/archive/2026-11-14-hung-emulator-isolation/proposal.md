# Proposal

## Why

When one worktree's emulator stops answering `adb` (seen with three emulators booting on a saturated machine), `shogun emulator start` and `shogun emulator status` fail in every worktree of the machine with `The command did not finish within 10s: adb -s emulator-5554 shell getprop sys.boot_completed`, naming another worktree's emulator, and `shogun emulator status` and `shogun pool status` take one to several minutes.
The cause is that reading emulator states asks every online emulator for its boot property one after the other, and the timeout of any one of them escapes as the error of the whole read; the physical Android device listing has the same pattern with each device's properties.

## What Changes

- An emulator whose boot property query doesn't answer within a short probe limit is reported with a new **unresponsive** state instead of failing the read, in `shogun emulator status`, `shogun pool status`, their JSON output and the desktop app's views.
- Boot property queries of different emulators run concurrently, so one emulator that doesn't answer delays a read by at most the probe limit, whatever the number of emulators.
- `shogun emulator status` (and every read of a single worktree's emulator) queries only that worktree's emulator, so another worktree's emulator never delays or fails it.
- `shogun emulator start` treats another worktree's unresponsive emulator as one more holder of the pool: it neither fails nor waits for it, and when choosing whom to release it ranks it like a booting emulator.
- When the worktree's own emulator is the one that doesn't answer, `start` keeps waiting up to its boot timeout and then fails with an error that says the emulator is running but not answering `adb`, and that the agent can wait and start again, or stop it and start it again; `status` reports it as unresponsive with that same advice. When `stop` gets no answer from `adb emu kill`, it fails with an error that says the emulator didn't exit and how to end it, instead of showing the internal `adb` command.
- A physical Android device whose properties don't answer within the probe limit is listed as pending with a new **unresponsive** usability instead of failing the whole listing, and devices are probed concurrently.
- The iOS simulator and iPhone listings were checked: each is a single `simctl` or `devicectl` call for every device, without per-device queries, so they don't have this pattern and don't change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `emulator-provider`: report and start/stop requirements gain the unresponsive state and the actionable errors; a new requirement isolates each emulator's boot property query (concurrent, bounded, only the emulators the read needs).
- `resource-pools`: the order of release places unresponsive emulators with booting ones, and a pool reading never fails or stalls because one device doesn't answer.
- `physical-devices`: discovery of physical Android devices reports a device that doesn't answer as pending and unresponsive, probing devices concurrently.

## Impact

- `core`: `EmulatorState`, `PoolHolderState` and `DeviceUsability` gain a value; `LocalAndroidSdk` (concurrent bounded probes, timeout mapped to the new state), `AndroidSdk` port (states of a set of serials), `SharedDeviceStatesAndroidSdk`, `ReadEmulatorStatesUseCase`, `StartWorktreeEmulatorUseCase`, `StopWorktreeEmulatorUseCase`, `ReadResourcePoolUseCase`, `releaseRank`, `AdbPhysicalDevices`, `CommandTimeouts`, and new `WorktreeError` cases.
- `cli`: status and pool output and JSON labels for the new states, a hint line when the worktree's own emulator is unresponsive.
- `desktopApp`: status chips, labels and error texts in English and Spanish for the new states and errors.
- No new dependencies; JSON gains the `unresponsive` value in existing `state` and `usability` fields.
