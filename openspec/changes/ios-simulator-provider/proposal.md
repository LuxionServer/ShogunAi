# Proposal

## Why

Parallel agents working on a KMP or iOS project share the same iOS simulators: KMP's iOS simulator tests pick a default device from `xcrun simctl list devices available` and `xcodebuild` uses whatever destination it's given, so two worktrees end up booting, installing on and testing against the same device. The Android emulator provider (#72) solved this for Android with one emulator per worktree; iOS needs the same, with Apple's tools. Measured on a development machine: a new simulator created with `xcrun simctl create` takes 17 MB, 364 MB after its first boot (18 s), and boots again in 3 s, while cloning a used one copies all its data (2.6 GB).

## What Changes

- `.shogunai.json` accepts `"providers": { "ios-simulator": {} }`, optionally with a preferred device type and runtime (`{ "device": "iPhone 16", "runtime": "iOS 18.5" }`). Without it nothing iOS-related runs or appears, and on Linux and Windows the provider does nothing.
- Each worktree gets its own simulator, created with `simctl create` (never cloned) the first time its environment is resolved, named `ShogunAi · <repository> · <worktree>`, and kept with the worktree's reservations. Its UDID is exported as `IOS_SIMULATOR_UDID` through `shogun env`, the `SessionStart` hook and `envFile`. A simulator deleted outside ShogunAi is recreated.
- Machine settings per project (device type, runtime) and CLI flags, with the same three levels as the emulator's AVD: this run, the machine setting, the repository's preference if available here, then a default (the newest iOS runtime and an iPhone it supports).
- `shogun simulator start|stop|status [<path>]` boots (and waits for), shuts down or reports the worktree's simulator.
- Cleanup deletes the simulators of the worktrees it removes, and lists simulators ShogunAi created whose worktree no longer exists (removed by another tool) so they can be deleted.
- KMP tests: ShogunAi detects whether the repository's Gradle build scripts pass `IOS_SIMULATOR_UDID` to the simulator test tasks. If not, the project configuration and `shogun simulator status` show the snippet to add to the repository, and offer, only after explicit confirmation, a machine-wide Gradle init script (`shogun simulator gradle-init install|uninstall`) that does it for every project.
- The `SessionStart` context tells Claude its simulator's UDID and how to use it with `xcodebuild` and to start it.
- Desktop app: the worktree row shows its simulator with Start and Stop; the cleanup dialog offers, checked by default, to delete the simulators of removed worktrees; the project configuration has an "iOS simulator" section.

## Capabilities

### New Capabilities
- `ios-simulator-provider`: declaring the provider, choosing device type and runtime, creating, recreating and deleting a simulator per worktree, exporting its UDID, booting and shutting it down, finding orphaned simulators, and wiring KMP's simulator tests.

### Modified Capabilities
- `worktree-environment`: providers can keep per-worktree resources (not only ports) with the worktree's reservations.
- `repository-provisioning`: the `ios-simulator` provider declaration and its validation.
- `project-configuration`: the iOS simulator machine settings and the KMP tests setup, shown only for repositories that declare the provider.
- `command-line-tool`: `shogun simulator start|stop|status`, `shogun simulator gradle-init install|uninstall`, `shogun clean --delete-simulators`, and the simulator hint in `SessionStart`.
- `worktree-overview`: the simulator state and actions in the worktree row.
- `worktree-cleanup`: deleting the simulators of removed worktrees and orphaned ones.

## Impact

- `core`: `IosSimulatorProvider` declaration, `SimulatorSettings` in `ProjectConfig`, an `AppleSimulators` port and its `simctl` implementation, resource reservations in the environment state, simulator use cases, KMP test wiring detection and the init script.
- `cli`, `desktopApp`: commands, row line, cleanup option, configuration section, strings in both languages.
- Docs and roadmap in both languages.
