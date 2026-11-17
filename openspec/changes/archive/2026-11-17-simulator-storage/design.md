# Design

## Context

ShogunAi talks to the simulators through the `AppleSimulators` port, implemented by `SimctlAppleSimulators` with `xcrun simctl list -j runtimes|devices`, `create`, `boot`, `shutdown` and `delete`. It never downloads runtimes. A worktree's simulator UDID is recorded with its reservations (`ResolveWorktreeEnvironmentUseCase.recordedOnMachine`), its runtime is chosen by `chooseSimulator` from the request, the project's machine settings (`ProjectConfig`'s `SimulatorSettings`), the repository's `IosSimulatorProvider` preference and, by default, the newest iOS runtime. `DeleteSimulatorsUseCase` already finds and deletes orphaned `ShogunAi · ` simulators.

What the tooling reports, checked on macOS 27.0.1 with Xcode's current simulators:

- `xcrun simctl runtime list -j` gives, per disk image: `identifier` (UUID), `runtimeIdentifier` (`com.apple.CoreSimulator.SimRuntime.iOS-26-5`), `version`, `build`, `platformIdentifier`, `kind`, `state`, `deletable`, `sizeBytes` and `lastUsedAt`. `sizeBytes` is the real cost; `diskutil apfs list` shows each runtime as two volumes whose logical sizes add up to far more.
- `xcrun simctl list -j devices` groups devices by `runtimeIdentifier` and gives each `isAvailable`, `dataPathSize` and `lastUsedAt`.
- `xcrun simctl runtime delete <identifier>` deletes one image, shutting down the simulators booted on it; it has `--dry-run`. `xcrun simctl delete unavailable` deletes the devices whose runtime is gone.
- `/Library/Developer/CoreSimulator/Caches/dyld/<macOS build>/<runtime>` belongs to root, readable by everyone. `sw_vers -buildVersion` gives the running build. The premise that prompted this change, a cache of an old build, turned out wrong on that machine: `26A434` was the running build. Caches are regenerated on demand, so only a different build's folder is safe to call stale.

`MachineResources.kt` is at 995 of its 1006-line cap and `ShogunCli.kt` at 3363 of 3493.

## Goals / Non-Goals

**Goals:**

- One core listing (`SimulatorStorage`) feeding the CLI and the desktop view, with each runtime's users and refusal reason computed once in the domain.
- Deleting a runtime is always a single, named, confirmed, re-checked action.

**Non-Goals:**

- Deleting dyld caches or anything else that needs administrator rights; the user chose to see their size only.
- Automatic or policy-based deletion (`--notUsedSinceDays`, `--outdated`, a quota for runtimes), and runtimes in `shogun clean` or the cleanup dialog.
- Downloading or installing runtimes.
- A `shogun doctor` warning or a status bar item for simulator storage; left as roadmap follow-ups.
- Measuring `~/Library/Developer/CoreSimulator/Devices` as a whole: the available devices belong to Xcode or to ShogunAi's own worktree simulators, already handled.

## Decisions

### Extend `AppleSimulators` instead of a new simctl port

`AppleSimulators` gains `runtimeImages()` (parsing `simctl runtime list -j` into `SimulatorRuntimeImage`), `deleteRuntime(identifier)` and `deleteUnavailable()`. `SimulatorDevice` gains `runtimeIdentifier`, `isAvailable` and `dataBytes`, with defaults so existing callers and fakes compile unchanged; `parseDevices` keeps the unavailable devices but existing callers that create or boot only see the ones they already saw, because they match by UDID or name prefix.

Alternative: a separate `SimulatorStoragePort`. Rejected: the same `simctl` executor, error type (`WorktreeError.AppleToolFailed`) and macOS guard, and the users computation needs both devices and runtimes from the same source.

`runtimes()` (from `simctl list runtimes`, used by `chooseSimulator`) stays as it is: it gives names and supported device types, which `runtime list` doesn't. The two are joined on `runtimeIdentifier`.

### A `DyldCacheSource` port for the caches and the macOS build

`DyldCacheSource` (domain/io) has `folders(): List<DyldCacheFolder>` (build, path, size or null) and `currentBuild(): String?`; `MacDyldCacheSource` reads the folder with the existing `SizeVisitor` (skipping unreadable entries, returning null for a folder it can't open) and runs `sw_vers -buildVersion` as argv. It never deletes. The removal command is built in the domain as text for display only (`sudo rm -rf <path>`), never passed to the executor; a test asserts the fake executor never receives a `sudo` command.

### Users and refusals in the domain

`ListSimulatorStorageUseCase` takes the saved projects (`ProjectConfig` list) and returns `SimulatorStorage(runtimes: List<StoredRuntime>, unavailableDevices, dyldCaches, currentBuild)`. For each `StoredRuntime` it computes `users: List<RuntimeUser>`:

- `RuntimeUser.Worktree(simulatorName)`: a device whose UDID is in some declaring project's `recordedOnMachine(IOS_SIMULATOR_KEY)` and whose `runtimeIdentifier` matches. The label is the device name without `ShogunAi · `, which already is `<repository> · <worktree>`, so no worktree lookup is needed.
- `RuntimeUser.Project(name)`: a project that declares `ios-simulator` whose machine setting or repository preference matches the runtime's name the way `chooseSimulator` matches it (case-insensitive name, e.g. `iOS 26.5`).
- `RuntimeUser.Default`: the runtime `chooseSimulator` would pick with no request, settings nor preference (newest iOS), computed by calling it, so the two can't drift.

`refusal` is the first of: not deletable (`BUNDLED`), booted device on it (`BOOTED`), has users (`IN_USE`). Several images can share a `runtimeIdentifier` (an outdated image next to a newer one); users attach to every image with that identifier, but `Default` attaches only to the newest image, so the older duplicate is deletable.

Projects that don't declare `ios-simulator` contribute no users: their simulators aren't ShogunAi's.

### Deletion re-lists first

`DeleteSimulatorRuntimeUseCase(identifier, projects)` lists the storage again, refuses when the image is gone or has a refusal, then calls `deleteRuntime` and returns `RuntimeDeletion(runtime, bytesFreed = sizeBytes, unavailableLeft)`, re-reading devices afterwards for the offer. `DeleteUnavailableDevicesUseCase` lists, deletes with `simctl delete unavailable` and reports the sum of their `dataBytes`. Both are exposed in `ShogunEngine`.

No `sudo` and no retry: if `simctl runtime delete` fails for lack of rights, the tooling's message is reported. Deleting an image as a normal user is expected to work, as Xcode's Components settings does it without a password; the implementation checks it on a runtime the user agrees to delete, with their permission, not by exploring.

### Runtime argument in the CLI

`shogun simulator runtime delete <runtime>` matches, in order, the image identifier, the `runtimeIdentifier`, the runtime name (`iOS 26.5`) and the bare version (`26.5`); a bare version matching images of several platforms is ambiguous and lists them. Previews without `--yes` follow `shogun clean`, since a runtime costs a long download to get back; `xcode clean` deletes without `--yes`, but DerivedData is rebuilt locally.

The commands work from any folder, reading the saved projects from the project registry, because the storage is the machine's. They live in a new `SimulatorStorageCommands.kt` with their text and JSON, so `ShogunCli.kt` only gains the dispatch.

### Desktop: a separate state holder and the view's visibility

The storage state and actions go into a new `MachineSimulatorStorage` class, created in `AppShell` from the engine's actions (`simulatorStorageActions`) and passed to `MachineResources`, which only exposes it and adds the iOS simulators entry for Xcode projects. The section loads the storage each time it is shown, not on the view's refresh ticks, so the dyld caches aren't walked every few seconds, and reloads it after each deletion. To stay within the length caps without raising them, `MachineSimulator` moved out of `MachineResources.kt`, `XcodeUseCases` out of `AppContainer.kt` and `RemoveProjectDialog` out of `AppShell.kt`, and the caps of those three files were lowered to their new lengths. `IosSimulatorsView` renders a storage section below the worktree simulators; the section uses the shared list row and ghost actions with disabled reasons, like the DerivedData view. The dyld command is offered as "Copy command", never run.

`IOS_SIMULATORS` is offered when a saved project declares `ios-simulator` or `xcode` on macOS (`MachineProject.declares` gains it for `xcode`); with only `xcode` the worktree simulator part is omitted.

Alternative: put the storage in the Xcode DerivedData view. Rejected: runtimes and devices are the simulators' storage, and the iOS simulators view already deletes orphaned simulators.

## Risks / Trade-offs

- [`simctl runtime list -j` fields change between Xcode versions] → lenient parsing; a missing `sizeBytes` or `lastUsedAt` is shown as unknown, never as zero, and an image without `identifier` is skipped.
- [A runtime deletion needs rights on some setups] → reported with the tooling's message; no escalation. It wasn't confirmed on a real runtime: the only deletable one on the machine used for the check was named by a saved project, and ShogunAi refused it, as specified.
- [Deleting unavailable devices also deletes an unavailable ShogunAi simulator a worktree recorded] → acceptable: it can't boot anyway, and the provider recreates a lost simulator on the next start.
- [Measuring the dyld caches walks tens of GB of files] → done off the interface thread, cancellable like DerivedData measurement, and only when the view or the CLI asks.
- [A runtime used by a project not saved in ShogunAi looks unused] → its users only cover ShogunAi's projects; the confirmation states the runtime and its size, and deletion is always explicit.
