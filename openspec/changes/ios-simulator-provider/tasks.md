# Tasks

## 1. Declaration, settings and resource reservations

- [ ] 1.1 Parse `providers.ios-simulator` (`device`, `runtime`) into `IosSimulatorProvider`, add `SimulatorSettings` to `ProjectConfig` and the pure `chooseSimulator`; verify with provisioning-file, project-repository and choice tests (every order case, missing runtime)
- [ ] 1.2 Move `EnvironmentStateStore` to `WorktreeReservations(ports, resources)` with `recordedResourcesOnMachine`, keeping old files readable; verify with infrastructure tests (round trip, index while only resources exist, old files)
- [ ] 1.3 Add `ResourceRequest`s and the injected `ProviderContributions` factory to `ResolveWorktreeEnvironmentUseCase` (create outside the lock, recreate when gone, loser deletes its own, read-only mode creates nothing, declared `env` wins); move the emulator contribution into it; verify with use case tests
- [ ] 1.4 Document declaration, settings, resources and the factory in the architecture docs (both languages); verify `mkdocs build --strict` passes

## 2. Apple simulators and use cases

- [ ] 2.1 Add the `AppleSimulators` port, models and errors (`NoIosRuntime`, `SimulatorBootTimeout`, `IosSimulatorProviderNotDeclared`, `IosSimulatorsNotSupported`) with a fake; implement `SimctlAppleSimulators` on `simctl list -j`, `create`, `boot`, `bootstatus`, `shutdown`, `delete`; verify with tests on real `simctl` JSON output and the commands built
- [ ] 2.2 Add the iOS simulator contribution (macOS only, `IOS_SIMULATOR_UDID`, name `ShogunAi · <repository> · <worktree>`) and the start, stop, states and delete (with orphans) use cases; verify with use case tests for every scenario of the provider spec
- [ ] 2.3 Add the `KmpTestWiring` port and its file-based implementation (scan, init script install/uninstall with the documented content); verify with tests on temporary repositories and a temporary Gradle user home
- [ ] 2.4 Document the port, implementation, use cases and wiring (both languages); verify `mkdocs build --strict` passes

## 3. Command line

- [ ] 3.1 Parse `shogun simulator start|stop|status [<path>] [--device] [--runtime]`, `shogun simulator gradle-init install|uninstall [--yes]` and `shogun clean --delete-simulators`, update help; verify with parsing tests
- [ ] 3.2 Implement the commands (confirmation read from standard input), the clean option (UDIDs read before removal, orphans) and the `SessionStart` hint, wiring the real implementations in `Main.kt`; verify with CLI tests for each scenario
- [ ] 3.3 Document the commands, the iOS section of the README and the agents recipes (both languages); verify `mkdocs build --strict` passes

## 4. Desktop app

- [ ] 4.1 Add the "iOS simulator" configuration section (device type and runtime dropdowns, KMP wiring panel with copy and confirmed install), only for repositories with the provider on macOS; verify with view model tests and in the running app
- [ ] 4.2 Load simulator states and add Start/Stop to the worktree row; verify with view model tests (no `xcrun` without the provider, start, stop) and in the running app
- [ ] 4.3 Add the checked "also delete their iOS simulators" cleanup option with orphans (enabling "Clean up" for orphans only); verify with view model tests and in the running app
- [ ] 4.4 Strings in both languages, `StringsTest` entries and `docs/architecture/ui.md` (both languages); verify tests and `mkdocs build --strict` pass

## 5. Roadmap and end-to-end validation

- [ ] 5.1 Update the roadmap in both languages (iOS simulators delivered; DerivedData next); verify both languages match
- [ ] 5.2 Validate end to end on a toy KMP project with an iOS simulator test and two worktrees: each worktree gets its own simulator, the repository snippet makes `iosSimulatorArm64Test` run on the worktree's simulator in both worktrees at once, the init script does the same for an unwired copy, cleanup deletes the removed worktree's simulator and an orphan, and a repository without the provider runs no Apple tool
- [ ] 5.3 Run `./gradlew test`, `mkdocs build --strict` and `openspec validate ios-simulator-provider --strict`; verify all pass

## Workflow follow-up

- Archive the change with `/opsx:archive` in the same branch once validated, before opening the pull request to `epic/pivot`.
- Add the pull request to the GitHub Project (layer C, step 9) and update the epic's handoff section.
