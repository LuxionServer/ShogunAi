# Tasks

## 1. Core

- [ ] 1.1 Add `SimulatorRuntimeImage`, `StoredRuntime`, `RuntimeUser`, `RuntimeRefusal`, `DyldCacheFolder`, `SimulatorStorage`, `RuntimeDeletion` and `UnavailableDeletion` to the model, and `runtimeIdentifier`, `isAvailable` and `dataBytes` (with defaults) to `SimulatorDevice`; verified by the use case tests below.
- [ ] 1.2 Extend `AppleSimulators` and `SimctlAppleSimulators` with `runtimeImages()`, `deleteRuntime(identifier)` and `deleteUnavailable()`, keep unavailable devices in `parseDevices` with their runtime and data size, and update the fakes; verified by parser tests on fixtures copied from `simctl runtime list -j` and `simctl list -j devices` (with placeholder paths), missing fields and argv assertions for the deletions.
- [ ] 1.3 Add the `DyldCacheSource` port and `MacDyldCacheSource` (folders with sizes, unreadable folder as unknown, missing root, `sw_vers -buildVersion`, `osName` parameter), wired into `EnginePorts`, `HostEnginePorts` and the fixtures; verified by tests on a temporary folder and a fake executor that never receives `sudo`.
- [ ] 1.4 Add `ListSimulatorStorageUseCase` with users (recorded worktree simulators, project settings and preferences, the default from `chooseSimulator`), refusals (bundled, booted, in use), duplicate images, stale dyld folders and the display-only removal command; verified by tests for every scenario of `simulator-storage`, including non-macOS and projects without the provider.
- [ ] 1.5 Add `DeleteSimulatorRuntimeUseCase` (re-list, refuse, delete, space freed, unavailable devices left) and `DeleteUnavailableDevicesUseCase`, exposed in `ShogunEngine`; verified by tests for unused, used in the meantime, gone, failing and refused runtimes, and for none or several unavailable devices.

## 2. CLI

- [ ] 2.1 `shogun simulator storage [--json]` in a new `SimulatorStorageCommands.kt`, working from any folder with the saved projects; verified by text and JSON tests, and the non-macOS message.
- [ ] 2.2 `shogun simulator runtime delete <runtime> [--yes] [--json]` with matching by identifier, runtime identifier, name and version, preview without `--yes`, refusals and ambiguity on standard error with exit code 1; verified by parser and command tests.
- [ ] 2.3 `shogun simulator clean-unavailable [--yes] [--json]`; verified by preview and deletion tests.
- [ ] 2.4 Help for the new subcommands, and a test that `shogun clean --yes` with every option never calls the runtime or unavailable deletions.

## 3. Desktop app

- [ ] 3.1 Offer the iOS simulators view for projects that declare `xcode` on macOS, omitting the worktree simulators when none declares `ios-simulator`; verified by Machine entry tests.
- [ ] 3.2 Add `MachineSimulatorStorage` (background load, delete runtime, delete unavailable, reports) driven by `MachineResources` without raising its length cap; verified by state tests with fakes.
- [ ] 3.3 Storage section in `IosSimulatorsView` (runtime rows with users or disabled reasons, confirmations, unavailable devices action, dyld folders with "Copy command") with English and Spanish strings; verified by UI tests and checked in the running app with `shogun headless start`.

## 4. Docs and validation

- [ ] 4.1 Document simulator storage in `docs/guide/providers.md`/`.es.md` and the new commands in `docs/guide/cli.md`/`.es.md`.
- [ ] 4.2 Check `simctl runtime delete` as a normal user on a runtime the user agrees to delete, asking first with the exact command.
- [ ] 4.3 `./gradlew test koverVerify` passes and `openspec validate simulator-storage --strict` passes.

## Workflow follow-up

- Archive with `/opsx:archive`, adding to the roadmap (`docs/roadmap.md` and `.es.md`) the follow-ups left out: a `shogun doctor` warning and a status bar item for simulator storage.
- Merge `origin/develop`, run `./gradlew test koverVerify` again, open the pull request against `develop` assigned to CiroFeliu.
