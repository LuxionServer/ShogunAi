## Why

Launching a terminal automatically for a worktree is unreliable: it depends on detecting an installed emulator, on OS-specific scripting (`osascript`/AppleScript for macOS, shell wrapping for Linux terminals), and on macOS automation permissions the user has to grant per app. A recent fix (`fix-macos-terminal-launch`) already had to work around Accessibility permission failures for `Terminal.app`, and the underlying approach (driving external GUI apps via scripting) remains fragile across emulators and OS versions. Replacing the launch with a "copy the command to the clipboard" button removes this whole class of failure: there is nothing to detect, no external process to drive, and no permission to request — the user pastes the command into whatever terminal they already have open.

## What Changes

- **BREAKING**: Remove automatic terminal launching entirely. The worktree row's terminal action no longer spawns a terminal emulator process.
- Add a new action that builds the shell command needed to start working on a worktree (`cd '<worktree path>' && <resolved agent launch command>`) and copies it to the system clipboard.
- **BREAKING**: Remove the project's terminal emulator preference (`TerminalPreference`, `TerminalSelectionMode`, `TerminalEmulator`) and its configuration UI, since there is no emulator to detect, select, or launch anymore.
- Remove `OpenWorktreeTerminalUseCase`, `TerminalCommandBuilder`, the `TerminalLauncher`/`TerminalEmulatorDetector` ports, and their infrastructure implementations (`ProcessTerminalLauncher`, `SystemTerminalEmulatorDetector`).
- Remove the `WorktreeError.NoTerminalAvailable` and `WorktreeError.TerminalLaunchFailed` error cases, since they only existed to report launch failures that can no longer occur.
- Keep `AgentLaunchConfig` (agent command + Headroom wrapping) unchanged — it is still the source of the command being copied.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `worktree-terminal-launch`: replace "resolve and launch a terminal emulator" requirements with a single "build the launch command and copy it to the clipboard" requirement. Drop all requirements about emulator resolution, `TerminalPreference` modes, and launch-failure errors.
- `project-configuration`: remove the "Configure the terminal emulator preference" requirement and its scenarios; no other requirement in this capability changes.

## Impact

- Affected code: `domain/usecase/OpenWorktreeTerminalUseCase.kt`, `domain/usecase/TerminalCommandBuilder.kt`, `domain/executor/TerminalLauncher.kt`, `domain/executor/TerminalEmulatorDetector.kt`, `infrastructure/ProcessTerminalLauncher.kt`, `infrastructure/SystemTerminalEmulatorDetector.kt`, `domain/model/TerminalPreference.kt`, `domain/model/WorktreeError.kt`, `ui/worktree/WorktreeScreen.kt`, `ui/worktree/WorktreeViewModel.kt`, `ui/projectconfig/ProjectConfigScreen.kt`, `ui/projectconfig/ProjectConfigViewModel.kt`, `AppContainer.kt`.
- Affected persisted data: `ProjectConfig.terminalPreference` becomes unused; existing stored projects that have it keep deserializing fine (field just stops being read/written) but the field itself should be dropped from the model.
- No new external dependencies; clipboard access uses the JVM/Compose Desktop clipboard API already available to the desktop app.
