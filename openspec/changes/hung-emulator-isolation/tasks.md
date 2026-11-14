# Tasks

## 1. Core: probes that don't fail the read

- [ ] 1.1 Add `EmulatorState.UNRESPONSIVE`, `PoolHolderState.UNRESPONSIVE`, `DeviceUsability.UNRESPONSIVE` and `CommandTimeouts.DEVICE_PROBE` (5 s); verify the project compiles without warnings (`./gradlew compileKotlin compileTestKotlin`) after updating every exhaustive `when`
- [ ] 1.2 Write the failing regression tests first in `LocalAndroidSdkTest`: a fake executor whose `getprop sys.boot_completed` for one serial throws `CommandExecutionException.TimedOut` next to a booted one (read succeeds, unresponsive + running), several timed-out serials probed concurrently (virtual time ≈ one probe limit), a later answer clearing the state, and a non-timeout start failure still failing; then make `emulatorStates` probe concurrently with `DEVICE_PROBE` and map timeouts to `UNRESPONSIVE`; verify the tests pass
- [ ] 1.3 Add `AndroidSdk.deviceStates(sdk, serials)` and its `SharedDeviceStatesAndroidSdk` override (filters cached states, otherwise probes only those serials without caching partial states); verify with `SharedDeviceStatesAndroidSdkTest` cases for both paths and that only the requested serial is probed
- [ ] 1.4 Make `LocalAndroidSdk.stopEmulator` turn a timed-out `emu kill` into `WorktreeError.EmulatorDidNotExit(serial, consolePort)`; verify with a `LocalAndroidSdkTest` case
- [ ] 1.5 Write failing tests in `AdbPhysicalDevicesTest` for one device whose `getprop` times out next to a healthy one (listing succeeds, pending `UNRESPONSIVE`, re-probed on the next listing) and for concurrent probing; then probe concurrently with `DEVICE_PROBE` and map timeouts; verify the tests pass

## 2. Core: use cases

- [ ] 2.1 Make `ReadEmulatorStatesUseCase` read only the serials of the worktrees it gets; verify with a use case test where another worktree's emulator times out and only the asked serial is probed
- [ ] 2.2 Make `StartWorktreeEmulatorUseCase` use the scoped read, wait on an unresponsive own emulator without launching, and fail with the new `WorktreeError.EmulatorUnresponsive(serial, waitedSeconds)` when the boot timeout passes while unresponsive; verify with use case tests: another worktree's emulator timing out during acquisition and polling (start succeeds), own emulator unresponsive until timeout (new error, message without the `adb` command line), own emulator unresponsive then booted (succeeds)
- [ ] 2.3 Map the new state to `PoolHolderState.UNRESPONSIVE` in `ReadResourcePoolUseCase` and pass it as busy to `releaseRank`; verify with `ResourcePoolUseCasesTest` cases: pool reading with a timed-out holder next to a running one, and a full pool whose only expired holder is unresponsive failing with the pool error without stopping it
- [ ] 2.4 Update `docs/architecture/domain.md`, `docs/architecture/infrastructure.md` and their `.es.md` translations (states, `releaseRank`, new errors, concurrent bounded probes, scoped reads); verify with `mkdocs build --strict`

## 3. CLI

- [ ] 3.1 Print the unresponsive state in `emulator status|start` text and JSON (`unresponsive`), with the hint line on standard error when the worktree's own emulator is unresponsive, and the new state in `pool status` and device listings; verify with CLI tests for status (own emulator unresponsive, another worktree's emulator timing out), start next to a timed-out emulator, and pool status with a timed-out holder, using the CLI's fake ports
- [ ] 3.2 Update `docs/guide/cli.md` and `docs/guide/cli.es.md` (status states, JSON `state` values, the hint and the stop error); verify with `mkdocs build --strict`

## 4. Desktop app

- [ ] 4.1 Map `EmulatorState.UNRESPONSIVE` to a warning chip and label, count it as active in the worktree row, and add labels for `DeviceUsability.UNRESPONSIVE`, `PoolHolderState.UNRESPONSIVE` and texts for `EmulatorUnresponsive` and `EmulatorDidNotExit` in English and Spanish; verify with `StatusMapping`, `WorktreeRowModel` and `StringsTest` unit tests

## 5. Validation

- [ ] 5.1 Run `./gradlew test koverVerify` and `./gradlew :cli:e2eTest` after merging `origin/develop`; verify both pass

## Workflow follow-up

- Archive with `/opsx:archive`, checking whether the roadmap (`docs/roadmap.md`, `docs/roadmap.es.md`) needs a line, before opening the pull request against `develop`.
