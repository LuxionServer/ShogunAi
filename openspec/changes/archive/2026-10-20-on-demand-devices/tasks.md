# Tasks

## 1. Pool model and limits

- [x] 1.1 Add `ResourceKind`, `CapacityProfile`, `ResourceLimits`, `PoolHolder`, `PoolUsage` and `PoolRelease` to `domain/model`, and `WorktreeError.ResourcePoolFull(kind, limit, holders)`; verified by compiling `core` and a test that the effective emulator limit is capped at 16
- [x] 1.2 Add the `MachineCapacity` port and its JVM implementation (`OperatingSystemMXBean.totalMemorySize`, `availableProcessors`) with a fake in `testFixtures`; verified by a test that the JVM implementation returns positive values
- [x] 1.3 Add `ResolveResourceLimitsUseCase` with the profile table of design decision 6; verified by tests for 8, 16, 32 and 64 GB, low core counts, unknown memory (Light), and each fixed profile
- [x] 1.4 Add `ResourceSettings` as `resources` in `JsonSettingsFile.Settings` and `JsonResourceLimitsRepository` (read/write, missing or unreadable → Automatic); verified by repository tests with a temporary settings file

## 2. Reservations: on demand and last use

- [x] 2.1 Add `lastUsed` read/write and `reservationsOnMachine` to `EnvironmentStateStore`, `NioEnvironmentStateStore` and `FakeEnvironmentStateStore`, plus releasing a single port or resource key; verified by store tests (write, read, release keeps other keys, index updated)
- [x] 2.2 Add `onDemand` to `PortRequest` and `ResourceRequest` and make `ResolveWorktreeEnvironmentUseCase` only read on-demand reservations, emitting provider variables only when their key is present and still delivering the other variables; verified by tests for the `worktree-environment` scenarios (on-demand port not acquired, read without creating, provider variable after acquisition)
- [x] 2.3 Mark the emulator and iOS simulator contributions as on demand; verified by tests that creating a worktree and `shogun env` reserve no console port and create no simulator

## 3. Acquire and release devices

- [x] 3.1 Add `ReadResourcePoolUseCase` that builds `PoolUsage` for emulators and simulators from machine reservations and device states, mapping admin directories to worktree paths; verified by tests with holders in two repositories, a removed worktree, and devices reserved before `lastUsed` existed
- [x] 3.2 Add `AcquireWorktreeResourceUseCase` (under the reservation lock: already held → touch last use; room → reserve; full → release the least-recently-used idle holder; nothing idle → `ResourcePoolFull`), releasing an emulator by removing its port and a simulator by deleting it and its UDID, and rewriting the environment file; verified by tests for every `resource-pools` acquisition scenario and the emulator and simulator release scenarios
- [x] 3.3 Make `StartWorktreeEmulatorUseCase` and `StartWorktreeSimulatorUseCase` acquire first and return the releases made, and report "not acquired" in `ReadEmulatorStatesUseCase`, `ReadSimulatorStatesUseCase` and the stop use cases; verified by the updated `emulator-provider` and `ios-simulator-provider` scenario tests
- [x] 3.4 Map `ResourcePoolFull` in error presentation to the limit, each holder as `project · worktree` and its state, and the hint to stop one or change Preferences; verified by an error presentation test

## 4. Gradle daemon pool

- [x] 4.1 Add `TrimGradleDaemonsUseCase` over `ListGradleDaemonsUseCase` and `StopGradleDaemonsUseCase` (idle only, longest idle first, until within the limit); verified by tests for over the limit, only busy daemons, became busy before the stop, and no limit
- [x] 4.2 Add Gradle daemons to `ReadResourcePoolUseCase`'s usage; verified by a test with three Gradle and two Kotlin daemons counting three

## 5. Command line

- [x] 5.1 Build `JsonResourceLimitsRepository` and `MachineCapacity` in the CLI and wire them into the acquisition use cases; verified by a CLI test where the settings file sets Custom limits and `emulator start` applies them
- [x] 5.2 Update `shogun emulator start|status` and `shogun simulator start|status`: `not acquired`, releases on standard error, `export …` after a first acquisition, pool-full error with exit code 1; verified by CLI tests for the `command-line-tool` scenarios
- [x] 5.3 Update `shogun hook session-start` context for worktrees with and without a device; verified by hook tests for both cases and that it reserves nothing
- [x] 5.4 Add `shogun pool status` and `shogun pool trim` with `--json` and help text; verified by CLI tests for the status, trim and JSON scenarios and the usage output

## 6. Desktop app

- [x] 6.1 Add the "Resources" section to Preferences (profile choice, limits per kind on this machine, Custom fields with "no limit" and validation, iOS line only on macOS) with English and Spanish strings; verified by a UI test choosing Custom and an invalid value, and by running the app
- [x] 6.2 Show pool usage, the profile, the over-limit mark and dismissable release/trim notices in the emulator, simulator and Gradle machine views, and usage in the sidebar summaries; verified by UI tests for usage and a reported release
- [x] 6.3 Show "Not acquired" with Start in the worktree detail and the machine device rows, and surface releases and `ResourcePoolFull` from Start; verified by UI tests and by starting two emulators with a limit of 1 in the running app
- [x] 6.4 Run `TrimGradleDaemonsUseCase` in the 30-second machine summary refresh when a saved project declares the Gradle provider; verified by a view model test with a fake clock and daemon source

## 7. Documentation and validation

- [x] 7.1 Update `docs/architecture/domain.md`, `cli.md`, `ui.md` and their `.es.md` translations, and the README's provider section, to describe pools, profiles and on-demand devices; verified by `mkdocs build --strict`
- [x] 7.2 Run `./gradlew test` and check in the running app: a new worktree has no `ANDROID_SERIAL`, starting acquires it, a full pool releases the idle device or fails naming the holders, and Preferences changes the limits used by `shogun emulator start`

## Workflow follow-up

- Archive the change with `/opsx:archive` before opening the pull request, removing roadmap item 2 from `docs/roadmap.md` and `docs/roadmap.es.md`, renumbering the rest, and adding the DerivedData disk quota as a follow-up.
- Open the pull request against `develop`.
