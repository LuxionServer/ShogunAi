# worktree-terminal-launch Specification

## Purpose
TBD - created by archiving change worktree-open-in-terminal. Update Purpose after archive.
## Requirements
### Requirement: Open a terminal at a worktree's path
The system SHALL let the user open a terminal session at a worktree's path from the worktree management screen, via `OpenWorktreeTerminalUseCase`, which runs the project's configured agent launch command (`ProjectConfig.agentLaunchConfig`) in that terminal.

#### Scenario: Successful launch
- **WHEN** the user clicks the "Terminal" action on a worktree row
- **THEN** the app resolves a terminal emulator per the project's `TerminalPreference`, opens it with the worktree's path as working directory, and runs the project's resolved agent launch command in it

#### Scenario: Default agent command wraps Headroom
- **WHEN** a project has not customized `AgentLaunchConfig`
- **THEN** the resolved command run in the opened terminal is `headroom wrap claude`

#### Scenario: Headroom wrapping disabled
- **WHEN** the project's `AgentLaunchConfig.useHeadroom` is `false`
- **THEN** the resolved command run in the opened terminal is just `AgentLaunchConfig.agentCommand`, without the `headroom wrap` prefix

#### Scenario: Launch does not block on the terminal session
- **WHEN** a terminal is successfully opened for a worktree
- **THEN** the use case returns success as soon as the terminal process has started, without waiting for the user to close that terminal window

### Requirement: Resolve which terminal emulator to use
The system SHALL resolve a concrete terminal emulator for a launch based on the project's `TerminalPreference.mode`: `FIXED` uses the configured `emulator` directly, `CUSTOM` uses the configured `customCommandTemplate` argv with `{path}`/`{command}` placeholders substituted, and `AUTO_DETECT` picks the first available emulator (via `TerminalEmulatorDetector`) in a fixed, OS-appropriate priority order. On macOS, launching against `Terminal.app` SHALL NOT rely on OS Accessibility/GUI-scripting permissions, so the launch works without any manual permission setup.

#### Scenario: Fixed emulator configured
- **WHEN** `TerminalPreference.mode` is `FIXED` and `emulator` is set
- **THEN** the system builds the launch command for that exact emulator, without running detection

#### Scenario: Custom command template configured
- **WHEN** `TerminalPreference.mode` is `CUSTOM` and `customCommandTemplate` is set
- **THEN** the system substitutes `{path}` and `{command}` into each argv element of the template and launches that argv directly, without invoking a shell to interpret a command string

#### Scenario: Auto-detect finds an available emulator
- **WHEN** `TerminalPreference.mode` is `AUTO_DETECT`
- **THEN** the system queries `TerminalEmulatorDetector` and uses the first available emulator in priority order for the current OS

#### Scenario: Launching Terminal.app on macOS always opens a new window
- **WHEN** the resolved emulator is `MACOS_TERMINAL`
- **THEN** the system opens a new `Terminal.app` window and runs the resolved command in it, regardless of how many `Terminal.app` windows are already open, without requesting or depending on Accessibility permission for GUI scripting

### Requirement: Surface a typed error when no terminal can be launched
The system SHALL return `WorktreeError.NoTerminalAvailable` when auto-detection finds no supported emulator, or when a `FIXED` preference points at an emulator that is not installed, instead of silently doing nothing or throwing an untyped exception.

#### Scenario: Auto-detect finds nothing installed
- **WHEN** `TerminalPreference.mode` is `AUTO_DETECT` and `TerminalEmulatorDetector` reports no available emulators for the current OS
- **THEN** `OpenWorktreeTerminalUseCase` returns a failed `Result` wrapping `WorktreeError.NoTerminalAvailable`

#### Scenario: Fixed emulator not installed
- **WHEN** `TerminalPreference.mode` is `FIXED` and the configured `emulator` is not available on the current machine
- **THEN** `OpenWorktreeTerminalUseCase` returns a failed `Result` wrapping `WorktreeError.NoTerminalAvailable`

### Requirement: Surface a typed error when the terminal process fails to start
The system SHALL return `WorktreeError.TerminalLaunchFailed` when the operating system fails to start the resolved terminal process (e.g. the target binary/app is missing despite being detected, or the OS rejects the launch).

#### Scenario: Process fails to start
- **WHEN** `TerminalLauncher.launch` fails to start the OS process for the resolved emulator's command
- **THEN** `OpenWorktreeTerminalUseCase` returns a failed `Result` wrapping `WorktreeError.TerminalLaunchFailed` with the underlying cause

