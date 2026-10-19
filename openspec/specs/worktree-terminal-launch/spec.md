# worktree-terminal-launch Specification

## Purpose
Lets the user start working on a worktree by copying the command that opens it and runs the configured AI agent, ready to paste in any terminal.

## Requirements

### Requirement: Copy the worktree launch command to the clipboard
The system SHALL let the user copy, to the system clipboard, the shell command needed to start working on a worktree, via `CopyWorktreeLaunchCommandUseCase`, from the worktree's detail, its row's hover action, its row's context menu or the copy shortcut. The copied command SHALL change into the worktree's directory and then run the project's resolved agent launch command (`ProjectConfig.agentLaunchConfig`), without launching any terminal process or terminal emulator on the user's behalf. The worktree path SHALL be quoted so that the pasting shell takes it literally: single quotes on macOS and Linux, double quotes on Windows, where paths can't contain them.

#### Scenario: Successful copy
- **WHEN** the user activates "Copy launch command" for a worktree from any of the places that offer it
- **THEN** the app writes `cd '<worktree path>' && <resolved agent command>` to the system clipboard on macOS and Linux, and `cd "<worktree path>" && <resolved agent command>` on Windows

#### Scenario: Path with shell syntax
- **WHEN** the worktree's folder is named `x$(id)` on macOS
- **THEN** pasting the copied command into zsh or bash changes into that folder and runs no other command

#### Scenario: Path with a single quote
- **WHEN** the worktree's path contains `'` on macOS or Linux
- **THEN** the quote is escaped and the copied command still changes into that folder

#### Scenario: Default agent command wraps Headroom
- **WHEN** a project has not customized `AgentLaunchConfig`
- **THEN** the copied command's resolved agent command is `headroom wrap claude`

#### Scenario: Headroom wrapping disabled
- **WHEN** the project's `AgentLaunchConfig.useHeadroom` is `false`
- **THEN** the copied command's resolved agent command is just `AgentLaunchConfig.agentCommand`, without the `headroom wrap` prefix

#### Scenario: Copy does not depend on any terminal emulator being installed
- **WHEN** the user copies the launch command
- **THEN** the app succeeds regardless of which, if any, terminal emulators are installed on the machine, since no emulator is detected or launched

### Requirement: Surface a typed error when the clipboard write fails
The system SHALL return `WorktreeError.ClipboardWriteFailed` when the operating system fails to write the launch command to the system clipboard, instead of silently doing nothing or throwing an untyped exception.

#### Scenario: Clipboard write fails
- **WHEN** `ClipboardWriter.write` fails to place the command on the system clipboard (e.g. no clipboard owner is available in the current environment)
- **THEN** `CopyWorktreeLaunchCommandUseCase` returns a failed `Result` wrapping `WorktreeError.ClipboardWriteFailed` with the underlying cause
