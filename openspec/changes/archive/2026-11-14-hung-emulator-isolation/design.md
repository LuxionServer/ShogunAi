# Design

## Context

See proposal.md for the failure.
Emulator states come from `LocalAndroidSdk.emulatorStates`, which takes `adb devices -l` output and, for each online emulator it hasn't yet seen booted, runs `adb -s <serial> shell getprop sys.boot_completed` with the 10-second `CommandTimeouts.LISTING` limit, one after the other.
`ProcessBuilderShellCommandExecutor` throws `CommandExecutionException.TimedOut` when a command passes its limit, and nothing between the probe and the caller catches it:

- `ReadEmulatorStatesUseCase` (behind `shogun emulator status`, the cleanup and the desktop views) fails with it, even when asked about one worktree, because it reads every emulator's state.
- `StartWorktreeEmulatorUseCase` fails during the acquisition, where `ReadResourcePoolUseCase.listing(kind, path)` rethrows device read failures, and again in its own polling loop.
- `ReadResourcePoolUseCase.emulators` (behind `shogun pool status`) swallows the failure and reports every holder as stopped, after waiting 10 seconds per hung emulator.

The booted-serial cache only lives within one process, so every CLI call probes every online emulator again.
`AdbPhysicalDevices.list` probes each physical device's `getprop` the same way, sequentially, and the timeout of one device fails the whole listing.
`SimctlAppleSimulators.devices` and `DevicectlPhysicalDevices.list` each make one call for every device, so a single device can't make them fail on its own; they are left as they are.

## Goals / Non-Goals

**Goals:**

- A per-device probe that times out becomes a state of that device, never an error of the read.
- The cost of hung devices in one read is bounded by one probe limit, not multiplied by their number.
- Reads about one worktree don't probe other worktrees' emulators.

**Non-Goals:**

- A hung `adb` server (`adb devices` itself not answering) keeps failing the read: it affects every device equally and there is no per-device state to report.
- Remembering unresponsive emulators across CLI processes, or killing a hung emulator automatically.
- Any change to iOS simulator or iPhone reads.

## Decisions

### Unresponsive is a state, not an error

`EmulatorState`, `PoolHolderState` and `DeviceUsability` each gain `UNRESPONSIVE`.
The probe catches only `CommandExecutionException.TimedOut`; a probe that runs and fails (non-zero exit) keeps meaning booting, and a command that can't start keeps failing the read, as today.
Alternative considered: treat a timed-out probe as booting.
Rejected because the agent couldn't tell a slow boot from a hung emulator, and the observed case needs exactly that distinction to decide whether to wait or restart.

### Concurrent probes with a shorter limit

`emulatorStates` runs the probes of the emulators it has to ask inside `coroutineScope { async }`, and `AdbPhysicalDevices` does the same for the devices whose identity it doesn't know yet.
Probes use a new `CommandTimeouts.DEVICE_PROBE` of 5 seconds; `adb devices`, `emu kill` and the other commands keep `LISTING`.
A healthy `getprop` answers in well under a second, even on a loaded machine; 5 seconds keeps a margin while halving the worst-case wait of a read.
`bootedSerials` is already a concurrent set, and `AdbPhysicalDevices` collects the probe results first and updates its identity map afterwards, inside its mutex.

### Reads scoped to the serials they need

The `AndroidSdk` port gains `deviceStates(sdk, serials: Set<String>)`, whose default filters `attachedDevices` to those serials before calling `emulatorStates`.
`SharedDeviceStatesAndroidSdk` overrides it: when the cached reading already has states it filters them; otherwise it probes only the requested serials on the shared device list, without storing the partial states as the reading's states.
`ReadEmulatorStatesUseCase` asks for the serials of the worktrees it was given, so `shogun emulator status` probes one emulator; the desktop app, which passes every worktree, still probes them all.
`StartWorktreeEmulatorUseCase` uses the scoped read for its own serial before launching and while polling.
The acquisition's pool listing still reads every emulator, because choosing what to release needs each holder's state; with concurrent probes it costs at most one probe limit.
Alternative considered: skip probing in the pool listing and treat every online emulator as running.
Rejected because the release order depends on telling booting holders apart, and the cost is now bounded.

### Unresponsive holders aren't released

`ReadResourcePoolUseCase` maps the new emulator state to `PoolHolderState.UNRESPONSIVE`, and `holder()` passes it to `releaseRank` as busy, like booting, so it is never chosen as a victim.
Stopping a hung emulator would run `adb emu kill`, which can hang too and would fail the requesting start with another worktree's problem.

### Actionable errors for the worktree's own emulator

- Start: while polling, the loop remembers the last state; when the boot timeout passes and that state is unresponsive it throws the new `WorktreeError.EmulatorUnresponsive(serial, waitedSeconds)` instead of `EmulatorBootTimeout`. Its message names the serial, says it is running but not answering `adb`, and suggests waiting and running `shogun emulator start` again, or `shogun emulator stop` then `shogun emulator start`. A start that finds its emulator already unresponsive launches nothing and waits like for a booting one.
- Status: the CLI prints `emulator-5560 unresponsive` on standard output, as for any state, and a hint line on standard error with the same advice. JSON reports `"state": "unresponsive"`.
- Stop: `LocalAndroidSdk.stopEmulator` turns a timed-out `emu kill` into the new `WorktreeError.EmulatorDidNotExit(serial, consolePort)`, whose message says the emulator didn't answer the request to exit and that its emulator process, started on that port, has to be ended before starting it again.

Both errors get English and Spanish texts in the desktop app.

### Desktop app

`EmulatorState.UNRESPONSIVE` maps to a warning status chip with an "unresponsive" label and offers Stop, like other non-stopped states; the worktree row counts it as an active emulator.
`DeviceUsability.UNRESPONSIVE` gets its own label, and `PoolHolderState.UNRESPONSIVE` its English and Spanish holder texts.

## Risks / Trade-offs

- [A slow but healthy emulator on a very loaded machine is reported as unresponsive for a while] → it's reported as booting or running as soon as a probe answers, and the start keeps waiting until its boot timeout.
- [`SharedDeviceStatesAndroidSdk` holds its mutex while probing, so concurrent desktop reads wait for a hung probe] → the wait is bounded by one probe limit instead of 10 seconds per emulator.
- [A pool full of unresponsive emulators can't make room] → the pool error lists them as unresponsive, so the user knows which emulators to end; releasing them automatically could hang the start.
- [Coverage] → no new `@Serializable` classes, so core's maximum of missed branches doesn't change; every new line and branch is covered by unit tests with fake executors.
