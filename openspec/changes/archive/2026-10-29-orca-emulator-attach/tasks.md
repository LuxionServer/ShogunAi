# Tasks

## 1. Orca port

- [x] 1.1 Add `OrcaClient.attachEmulator(workspace, serial)` and implement it in `CliOrcaClient` as `emulator attach <serial> --worktree <selector> --focus --json`; record the attached pairs and an optional attach failure in `FakeOrcaClient`. Verify: `CliOrcaClientTest` asserts the argv, success on `{"attached": true}` and `OrcaCommandFailed` with Orca's message on `emulator_device_not_found`.

## 2. Use cases

- [x] 2.1 Add `OrcaEmulatorView` and `ShowEmulatorInOrcaUseCase` (installed → running → listed by `worktree ps` with `sameDirectory` → attach; any error `Failed`; 10 s timeout). Verify: `ShowEmulatorInOrcaUseCaseTest` covers "Worktree open in Orca", "Orca closed" (no start, no attach), "Worktree not in Orca", "Orca rejects it", Orca not installed and `/tmp` vs `/private/tmp`.
- [x] 2.2 Give `OpenWorktreeInOrcaUseCase` and `OpenGroupInOrcaUseCase` the optional running-emulator lookup, attach before focusing or starting the agent, ignore an attach failure, and add `emulator` to `OrcaOpening`. Verify: `OpenWorktreeInOrcaUseCaseTest` covers "Emulator running" (attach happens before the terminal is created or switched to), "Emulator stopped" and an attach failure that still opens; `OpenGroupInOrcaUseCaseTest` checks a member with a running emulator.

## 3. Command line

- [x] 3.1 Show the emulator in Orca after `shogun emulator start`, with the standard error line and `orcaView` in the JSON, and wire the lookup into `shogun open --orca` (text and `emulator` in its JSON). Verify: CLI tests for "Shown", "Failed" (exit 0, stdout unchanged), "JSON" and "Reported by the command line".

## 4. Desktop app

- [x] 4.1 Show the emulator in Orca after a successful start from the worktree's detail and from the machine view, and wire the lookup into "Open in Orca" and group opens, in `AppContainer`. Verify: `./gradlew :desktopApp:test` passes, and the real app check in 6.2.

## 5. Documentation

- [x] 5.1 Document showing the emulator in Orca's emulator view, and closing a tab opened from Orca's menu, in the guide's Orca and emulator pages and the CLI reference (`emulator start` JSON `orcaView`, `open` JSON `emulator`), with their Spanish translations. Verify: `mkdocs build --strict`.

## 6. Validation

- [x] 6.1 Run the whole suite and the spec validation. Verify: `./gradlew test` passes with no warnings; `openspec validate orca-emulator-attach --strict` passes.
- [x] 6.2 Check it with real Orca 1.4.243 on a disposable sample Android repository that declares the emulator provider: `shogun emulator start` in a worktree open in Orca, `shogun open --orca` with the emulator running, a start from the desktop app, a stop and a start again, and a start with Orca closed. Remove the sample from Orca afterwards. Verify: Orca's view of the worktree shows the emulator live after each start and open, the agent's terminal is in front after the open, nothing is started in Orca when it's closed, and Orca's repositories match their state before the check.
  Result: with the built CLI and Orca 1.4.243 on a sample with a headless `Tablet_API_35`, `emulator start` reported `orcaView: not-open` before the sample was in Orca and `shown emulator-5554 in Orca's emulator view` once Orca listed the worktree; `open --orca` attached the running emulator before creating the agent's terminal (the agent then waited on Claude's folder trust prompt, the known limit of a new folder); `emulator stop` ran no Orca command and Orca's `scrcpy` helper ended with the emulator. Orca's sidebar never drew the sample's worktree, so the live video was checked with the same `emulator attach … --focus` command on a ShogunAi worktree during exploration (see design.md), not on the sample. The desktop app's start and Orca closed are covered by tests only. Orca's CLI has no command to remove a repository, so the sample stays registered in Orca.

## Workflow follow-up

- On archiving, remove the emulator step from item 1 of `docs/roadmap.md` and `docs/roadmap.es.md`, and add the iOS simulator in Orca's emulator view as a follow-up there.
