# Tasks

## 1. Use cases

- [ ] 1.1 Rename `OrcaClient.attachEmulator`'s parameter to `device`, add `OrcaEmulatorView.Skipped`, and give `ShowEmulatorInOrcaUseCase` the `otherDeviceRunning` lookup, checked after Orca is running and lists the worktree (a throwing lookup counts as not running). Verify: `CliOrcaClientTest` asserts the argv with a UDID; `ShowEmulatorInOrcaUseCaseTest` covers a simulator UDID shown, "Emulator started while the simulator runs" (no attach, `Skipped`), a skip not reported when the worktree isn't in Orca, and a throwing lookup.
- [ ] 1.2 Add `ReadSimulatorStatesUseCase.runningUdid(config, worktreePath)`, returning the UDID unless the simulator is shut down. Verify: `WorktreeSimulatorUseCasesTest` covers booted, booting, shut down, not acquired and provider not declared.
- [ ] 1.3 Give `OpenWorktreeInOrcaUseCase` and `OpenGroupInOrcaUseCase` the optional `runningSimulator` lookup, attach the simulator when it is the only running device, attach nothing when both run, and add `simulator` and `devicesSkipped` to `OrcaOpening`. Verify: `OpenWorktreeInOrcaUseCaseTest` covers "Simulator booted" (attach before the terminal), "Opened with both running" (no attach, `devicesSkipped`) and only the emulator running; `OpenGroupInOrcaUseCaseTest` checks a member with a booted simulator.

## 2. Command line

- [ ] 2.1 Show the simulator in Orca after `shogun simulator start` (standard error lines and `orcaView` in the JSON), pass the simulator lookup to `shogun emulator start` and the emulator lookup to `shogun simulator start`, report the skip on both, and wire `runningSimulator` into `shogun open --orca` and group opens (text, skip line, `simulator` and `devicesSkipped` in the JSON). Verify: CLI tests for the simulator's "Shown", "Failed" (exit 0, stdout unchanged) and "JSON", both "started while the other runs" scenarios and "Opened with both running".

## 3. Desktop app

- [ ] 3.1 In `AppContainer`, show the simulator in Orca after a successful start from the worktree's detail and the machine view, pass the other-device lookups to both starts, and wire `runningSimulator` into "Open in Orca" and group opens. Verify: `./gradlew :desktopApp:test` passes, and the real app check in 5.2.

## 4. Documentation

- [ ] 4.1 Document showing the simulator in Orca's emulator view, the one-device-per-worktree rule (neither shown when both run; picking another device by hand in Orca shuts the simulator down) in the guide's Orca and iOS simulator sections and the CLI reference (`simulator start` JSON `orcaView`, `skipped` for both starts, `open` JSON `simulator` and `devicesSkipped`), with their Spanish translations. Verify: `mkdocs build --strict`.

## 5. Validation

- [ ] 5.1 Run the whole suite and the spec validation. Verify: `./gradlew test` passes with no warnings; `openspec validate orca-ios-simulator-attach --strict` passes.
- [ ] 5.2 Check it with real Orca 1.4.243 on a disposable sample repository that declares the iOS simulator provider (and the emulator provider, for the skip), with a worktree open in Orca: `shogun simulator start`, `shogun open --orca` with the simulator booted, a start from the desktop app, `shogun emulator start` while the simulator is booted, a stop and a start again. Delete the sample's simulators afterwards. Verify: Orca's view shows the simulator live after each start and open, the agent's terminal is in front after the open, the simulator stays booted after the emulator start, and no ShogunAi simulator is left behind.

## Workflow follow-up

- On archiving, remove the iOS simulator follow-up from item 1 of `docs/roadmap.md` and `docs/roadmap.es.md`.
