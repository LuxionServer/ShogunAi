# Proposal

## Why

A worktree whose repository declares the emulator provider gets its own Android emulator from ShogunAi, often without a window. When that worktree is open in Orca, the agent and the person following it can't see the emulator in Orca's emulator view: Orca lists it among its devices, but opening the view by hand starts its default iOS simulator and the user has to look for the AVD in a long list. Orca 1.4.243 ships `orca emulator attach <device> --worktree <selector> --focus`, which shows a running device in the worktree's emulator view. This is the "emulator" step of the roadmap's "What only Orca offers".

## What Changes

- When ShogunAi starts a worktree's emulator (`shogun emulator start`, the worktree's detail and the machine view's Android emulators) and that worktree is open in a running Orca, ShogunAi shows the emulator in the worktree's emulator view in Orca, with live video.
- When ShogunAi opens a worktree in Orca (`shogun open --orca`, "Open in Orca", a group opened in Orca) and the worktree's emulator is running, it shows the emulator there too, before focusing the agent's terminal.
- Showing the emulator is best effort: it never starts Orca, never fails the start or the open, and the command line says whether it was shown (a line on standard error, `orcaView` in `shogun emulator start --json` and `emulator` in `shogun open --orca --json`).
- Nothing is done to disconnect it: when ShogunAi stops the emulator, Orca's stream ends on its own. Orca 1.4.243 has no command to close the view, which keeps the last frame until the next start shows it again.
- `OrcaClient` gains `attachEmulator(workspace, serial)`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `orca-integration`: adds showing the worktree's ShogunAi emulator in Orca's emulator view when the emulator starts and when the worktree opens in Orca.

## Impact

- Core: `OrcaClient`, `CliOrcaClient`, `FakeOrcaClient`, a new `ShowEmulatorInOrcaUseCase`, `OpenWorktreeInOrcaUseCase` (and through it `OpenGroupInOrcaUseCase`), `OrcaOpening`.
- CLI: `shogun emulator start` and `shogun open --orca` (text and JSON).
- Desktop app: the emulator start wiring in `AppContainer` and the Orca open wiring; no new strings.
- Docs: Orca and emulator pages of the guide, the CLI reference and the roadmap, in English and Spanish.
- iOS simulators, which Orca's view also shows, are left for a follow-up.
