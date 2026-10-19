# Tasks

## 1. Boot-completed cache in LocalAndroidSdk

- [ ] 1.1 Make `LocalAndroidSdk.deviceStates` remember serials whose boot completed, skip `getprop` for them while they stay listed as `device`, and forget a serial when it leaves the list or isn't `device`; verify with new `LocalAndroidSdkTest` cases (second read of a booted emulator runs only `adb devices`; a serial that disappears and comes back is queried again and reported booting until `getprop` returns `1`)
- [ ] 1.2 Make `LocalAndroidSdk.stopEmulator` forget the stopped serial; verify with a `LocalAndroidSdkTest` case where stop then a later `device` listing queries `getprop` again

## 2. Shared device-list reader

- [ ] 2.1 Add `SharedDeviceStatesAndroidSdk` in `core/.../infrastructure`, an `AndroidSdk` decorator with an injected clock that serves `deviceStates` from one read per `adb` path for concurrent callers and for 2 seconds after a read, delegates every other call, and drops the cached result on `stopEmulator`; verify with a new `SharedDeviceStatesAndroidSdkTest` (concurrent callers → one delegate read; read within 2 s reuses it; read at 2 s or later reads again; different `adb` paths don't share; stop invalidates)
- [ ] 2.2 Wrap the desktop app's `LocalAndroidSdk` in `SharedDeviceStatesAndroidSdk` in `AppContainer` so every use case and `readRunningEmulators` share it; verify `./gradlew :desktopApp:compileKotlin` and `./gradlew :core:test` pass

## 3. Device refresh throttle in the worktree board

- [ ] 3.1 Add a `deviceRefreshInterval` parameter (default 10 seconds) to `WorktreeBoard` and, in `checkProjects`, call `refreshDevices()` for an unchanged project only when it was never refreshed by a check or its last check refresh is at least that old, using the board's clock; verify with new `WorktreeBoardTest` cases (checks within 10 s refresh devices once; a check 10 s later refreshes again; agents are still detected on every check)
- [ ] 3.2 Confirm the immediate refreshes are untouched (start/stop in `ProjectWorktrees`, list reload, machine view `onDevicesChanged`); verify with the existing `ProjectWorktreesTest` emulator and simulator tests passing unchanged

## 4. Integration

- [ ] 4.1 Run `./gradlew test` and verify every module passes
- [ ] 4.2 Run the desktop app with a project that declares the emulator provider and two emulators running, count `adb` executions over one minute with `sudo eslogger exec` filtered on `platform-tools/adb`, compare with `develop`, and verify the worktree rows still show the emulators as running and show a closed one as stopped within about 10 seconds

## Workflow follow-up

- Archive the change with `/opsx:archive` once implementation is validated, in this branch and before opening the pull request (base `develop`).
