# Proposal

## Why

`orca-emulator-attach` shows a worktree's ShogunAi Android emulator in Orca's emulator view, but a worktree whose repository declares the iOS simulator provider still gets nothing there: opening the view by hand starts Orca's default iPhone instead of the worktree's own simulator. `orca emulator attach` in Orca 1.4.243 takes a simulator's UDID as well as an emulator's serial. This is the follow-up the roadmap lists under "What only Orca offers".

Exploring it showed a constraint the Android change didn't meet: Orca's view shows one device per worktree, and when another device is attached to the same worktree, Orca shuts down the iOS simulator it was showing (an Android emulator it replaces keeps running). In a worktree with both providers (Kotlin Multiplatform), starting the emulator while Orca shows the simulator shuts the simulator down.

## What Changes

- When ShogunAi starts a worktree's iOS simulator (`shogun simulator start`, the worktree's detail and the machine view's iOS simulators) and that worktree is open in a running Orca, ShogunAi shows the simulator in the worktree's emulator view in Orca.
- When ShogunAi opens a worktree in Orca (`shogun open --orca`, "Open in Orca", a group opened in Orca) and the worktree's simulator is booted, it shows it there too, before focusing the agent's terminal.
- When a worktree's emulator and simulator are both running, ShogunAi shows neither, after a start or an open, so that Orca never shuts the simulator down. The command line says so.
- Showing the simulator is best effort, as for the emulator: it never starts Orca, never fails the start or the open, and the command line reports it (a line on standard error, `orcaView` in `shogun simulator start --json`, `simulator` in `shogun open --orca --json`).
- `shogun emulator start` reports `orcaView: skipped` when the worktree's simulator is running too.
- Nothing is done to disconnect it: when ShogunAi shuts the simulator down, Orca's stream ends on its own.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `orca-integration`: shows the worktree's iOS simulator in Orca's emulator view when it starts and when the worktree opens in Orca, and shows neither device when both run.

## Impact

- Core: `ShowEmulatorInOrcaUseCase` (a device and a check for the worktree's other device), `OrcaEmulatorView` (`Skipped`), `ReadSimulatorStatesUseCase` (running UDID lookup), `OpenWorktreeInOrcaUseCase` and `OpenGroupInOrcaUseCase` (simulator lookup), `OrcaOpening` (`simulator`, `devicesSkipped`).
- CLI: `shogun simulator start`, `shogun emulator start` and `shogun open --orca` (text and JSON).
- Desktop app: the simulator and emulator start wiring and the Orca open wiring in `AppContainer`; no new strings.
- Docs: Orca and iOS simulator pages of the guide, the CLI reference and the roadmap, in English and Spanish.
