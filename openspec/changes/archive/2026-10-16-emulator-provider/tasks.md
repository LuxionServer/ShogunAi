# Tasks

## 1. Declaration, settings and machine-wide reservations

- [x] 1.1 Add `EmulatorProvider` to `RepositoryProviders` and parse `providers.emulator` (object, optional string `avd`); verify with `RepositoryProvisioningFilesTest` cases for declared, preferred AVD and invalid values
- [x] 1.2 Add `EmulatorSettings` to `ProjectConfig` with defaults and the pure AVD choice; verify `JsonProjectRepositoryTest` loads old files and round-trips the settings, and unit tests cover every choice order case
- [x] 1.3 Add the machine reservation index and machine lock to `EnvironmentStateStore`/`NioEnvironmentStateStore`, pruning stale entries; verify with infrastructure tests across two temporary repositories, including a removed worktree's ports becoming free
- [x] 1.4 Turn variable ports into `PortRequest`s and add provider `EnvironmentContribution`s to `ResolveWorktreeEnvironmentUseCase` (emulator: even ports 5554–5584 with the next port free, `ANDROID_SERIAL`, declared `env` wins); verify with use case tests for uniqueness across repositories, the serial, two worktrees, exhaustion, override, and no Android work without the provider
- [x] 1.5 Document the provider declaration, machine settings, contributions and the machine index in the architecture docs (both languages); verify `mkdocs build --strict` passes

## 2. Android SDK and emulator use cases

- [x] 2.1 Add the `AndroidSdk` port, `SdkLocation`, `EmulatorState` and errors (`AndroidSdkNotFound`, `NoAvdAvailable`, `EmulatorBootTimeout`, `NoEmulatorPort`), with a fake in `testFixtures`; verify compilation of test fixtures
- [x] 2.2 Implement `LocalAndroidSdk` (SDK lookup order, `-list-avds`, `adb devices` plus boot property, detached start with the documented flags and log file, `emu kill`, `.exe` on Windows); verify with tests on parsed `adb` output, the lookup order with temporary directories, and the built start command
- [x] 2.3 Implement `StartWorktreeEmulatorUseCase`, `StopWorktreeEmulatorUseCase` and `ReadEmulatorStatesUseCase` with injected polling; verify with use case tests for start and wait, already running, no window, timeout, stop, stop when stopped, and states without reserving
- [x] 2.4 Document the SDK port, its implementation and the use cases (both languages); verify `mkdocs build --strict` passes

## 3. Command line

- [x] 3.1 Parse `shogun emulator start|stop|status [<path>] [--avd <name>] [--no-window]` and `shogun clean --stop-emulators`, update help; verify with parsing tests including usage errors
- [x] 3.2 Implement the emulator commands (error with the declaration hint without the provider), the clean option (serials read before removal) and the `SessionStart` context hint; verify with CLI tests for every scenario
- [x] 3.3 Document the commands in `docs/architecture/cli.md`, the Android section and recipes in `docs/agents.md` and the README (both languages); verify `mkdocs build --strict` passes

## 4. Desktop app

- [x] 4.1 Add the emulator section to the project configuration (AVD list or automatic, no window, SDK not found message), only for repositories with the provider; verify with `ProjectConfigViewModelTest` cases and in the running app
- [x] 4.2 Load emulator states for repositories with the provider and add Start/Stop to the worktree row with a starting state; verify with view model tests (no `adb` without the provider, start, stop, refresh) and in the running app
- [x] 4.3 Add the checked "also stop their emulators" option to the cleanup dialog and report stopped emulators; verify with view model tests
- [x] 4.4 Strings in English and Spanish and `docs/architecture/ui.md` (both languages); verify `StringsTest` and `mkdocs build --strict` pass

## 5. Roadmap and end-to-end validation

- [x] 5.1 Update the roadmap in both languages (emulator delivered; Gradle per-worktree settings and iOS simulators remain); verify both languages match
- [x] 5.2 Validate end to end with the local SDK: a minimal Android app in a toy repository with two worktrees, `shogun emulator start` in both (same AVD, read-only), `./gradlew installDebug` in each installs only on its own emulator, the app rows and configuration section show the right state, cleanup stops the removed worktree's emulator, and a repository without the provider shows nothing Android-related
- [x] 5.3 Run `./gradlew test`, `mkdocs build --strict` and `openspec validate emulator-provider --strict`; verify all pass

## Workflow follow-up

- Archive the change with `/opsx:archive` in the same branch once validated, before opening the pull request to `epic/pivot`.
- Add the pull request to the GitHub Project (layer C, step 9) and update the epic's handoff section.
