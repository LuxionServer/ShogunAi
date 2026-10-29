# Tasks

## 1. Orca port

- [ ] 1.1 Add `OrcaClient.attachEmulator(workspace, serial)` and implement it in `CliOrcaClient` as `emulator attach <serial> --worktree <selector> --focus --json`; record the attached pairs and an optional attach failure in `FakeOrcaClient`. Verify: `CliOrcaClientTest` asserts the argv, success on `{"attached": true}` and `OrcaCommandFailed` with Orca's message on `emulator_device_not_found`.

## 2. Use cases

- [ ] 2.1 Add `OrcaEmulatorView` and `ShowEmulatorInOrcaUseCase` (installed → running → listed by `worktree ps` with `sameDirectory` → attach; any error `Failed`; 10 s timeout). Verify: `ShowEmulatorInOrcaUseCaseTest` covers "Worktree open in Orca", "Orca closed" (no start, no attach), "Worktree not in Orca", "Orca rejects it", Orca not installed and `/tmp` vs `/private/tmp`.
- [ ] 2.2 Give `OpenWorktreeInOrcaUseCase` and `OpenGroupInOrcaUseCase` the optional running-emulator lookup, attach before focusing or starting the agent, ignore an attach failure, and add `emulator` to `OrcaOpening`. Verify: `OpenWorktreeInOrcaUseCaseTest` covers "Emulator running" (attach happens before the terminal is created or switched to), "Emulator stopped" and an attach failure that still opens; `OpenGroupInOrcaUseCaseTest` checks a member with a running emulator.

## 3. Command line

- [ ] 3.1 Show the emulator in Orca after `shogun emulator start`, with the standard error line and `orcaView` in the JSON, and wire the lookup into `shogun open --orca` (text and `emulator` in its JSON). Verify: CLI tests for "Shown", "Failed" (exit 0, stdout unchanged), "JSON" and "Reported by the command line".

## 4. Desktop app

- [ ] 4.1 Show the emulator in Orca after a successful start from the worktree's detail and from the machine view, and wire the lookup into "Open in Orca" and group opens, in `AppContainer`. Verify: `./gradlew :desktopApp:test` passes, and the real app check in 6.2.

## 5. Documentation

- [ ] 5.1 Document showing the emulator in Orca's emulator view, and closing a tab opened from Orca's menu, in the guide's Orca and emulator pages and the CLI reference (`emulator start` JSON `orcaView`, `open` JSON `emulator`), with their Spanish translations. Verify: `mkdocs build --strict`.

## 6. Validation

- [ ] 6.1 Run the whole suite and the spec validation. Verify: `./gradlew test` passes with no warnings; `openspec validate orca-emulator-attach --strict` passes.
- [ ] 6.2 Check it with real Orca 1.4.243 on a disposable sample Android repository that declares the emulator provider: `shogun emulator start` in a worktree open in Orca, `shogun open --orca` with the emulator running, a start from the desktop app, a stop and a start again, and a start with Orca closed. Remove the sample from Orca afterwards. Verify: Orca's view of the worktree shows the emulator live after each start and open, the agent's terminal is in front after the open, nothing is started in Orca when it's closed, and Orca's repositories match their state before the check.

## Workflow follow-up

- On archiving, remove the emulator step from item 1 of `docs/roadmap.md` and `docs/roadmap.es.md`, and add the iOS simulator in Orca's emulator view as a follow-up there.
