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

#### Scenario: Default agent command runs without Headroom
- **WHEN** a project has not customized `AgentLaunchConfig`
- **THEN** the copied command's resolved agent command is `claude`, without the `headroom wrap` prefix

#### Scenario: Headroom wrapping enabled
- **WHEN** the project's `AgentLaunchConfig.useHeadroom` is `true`
- **THEN** the copied command's resolved agent command is `headroom wrap <AgentLaunchConfig.agentCommand>`

#### Scenario: Default agent command wraps Headroom
- **WHEN** a project saved by an earlier version has not customized `AgentLaunchConfig`
- **THEN** the copied command's resolved agent command is `headroom wrap claude`, as it was before

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

### Requirement: Open a worktree in a terminal
The system SHALL offer "Open in terminal" for a worktree in its detail, its row's context menu and the command palette. It SHALL open a new window or tab of the chosen terminal in the worktree's folder, with the worktree's environment variables set, and run the project's agent command there, leaving the shell open when the agent exits. "Copy launch command" SHALL stay the detail's primary action.

#### Scenario: Open in Terminal on macOS
- **WHEN** the chosen terminal is Terminal and the user activates "Open in terminal" for a worktree whose environment has `PORT=3001`
- **THEN** a new Terminal window opens in the worktree's folder with `PORT` set to `3001` and runs the agent command

#### Scenario: Path or value with shell syntax
- **WHEN** the worktree's folder is named `x$(id)` or a variable's value contains `'`
- **THEN** the terminal changes into that folder and sets that value literally, and runs no other command

#### Scenario: No terminal chosen
- **WHEN** no terminal has been chosen and the user activates "Open in terminal"
- **THEN** the app asks the user to choose one among the detected terminals, and opens it after the choice

### Requirement: Supported terminals
The system SHALL support Terminal and iTerm2 on macOS, Windows Terminal on Windows, and tmux (3.0 or later) and Zellij on macOS and Linux. A terminal SHALL be offered only when it's installed, and tmux and Zellij only while they have a running session, in which the worktree opens as a new window or tab.

#### Scenario: tmux session running
- **WHEN** the chosen terminal is tmux and a tmux session is running
- **THEN** a new window opens in the most recently attached session, in the worktree's folder, with its environment, running the agent command

#### Scenario: Multiplexer without a session
- **WHEN** Zellij is installed but no Zellij session is running
- **THEN** Zellij isn't offered as a terminal

### Requirement: Terminal launch failures
When opening the terminal fails, the app SHALL show an error naming the terminal and its error output, and SHALL NOT change the worktree. When macOS refuses ShogunAi permission to control Terminal or iTerm2, the message SHALL say where to allow it.

#### Scenario: Automation refused
- **WHEN** the user declined macOS's prompt to let ShogunAi control Terminal
- **THEN** the app explains that the permission is in System Settings > Privacy & Security > Automation, and nothing else happens

### Requirement: Choose the agent for a launch
When a project has several agents, "Copy launch command", "Open in terminal", "Open in Herdr" and "Open in Orca" SHALL let the user pick another of the project's agents through their "with agent" submenu in the "Open with…" menu and the row's context menu, and through one command palette item per other agent ("Open in Orca with Codex"). Their plain entries SHALL use the worktree's agent, which the primary action's tooltip names. A project with one agent SHALL show the actions without agent choice.

#### Scenario: Launch with another agent
- **WHEN** the project's agents are Claude (default) and Codex, and the user picks Codex in "Copy launch command › with agent" on a worktree
- **THEN** the copied command changes into the worktree and runs Codex's command

#### Scenario: Single agent
- **WHEN** the project has only one agent
- **THEN** the launch actions offer no agent choice

### Requirement: Set the agent of each worktree
When a project has several agents, the "Open with…" menu SHALL show a "With agent" section listing the project's agents with the worktree's one checked, and picking one SHALL set the worktree's agent without launching. Picking an agent for a launch SHALL also make it the worktree's agent. The worktree's agent SHALL be remembered on this machine and used by default for its launches; choosing the project's default agent SHALL clear it, and an agent that no longer exists in the project SHALL fall back to the default.

#### Scenario: Continue with the agent the task moved to
- **WHEN** the user launched a worktree with Codex and later opens it in the terminal without choosing
- **THEN** the terminal runs Codex

#### Scenario: Set the agent from the detail
- **WHEN** the user picks Codex in the "With agent" section of a worktree's "Open with…" menu
- **THEN** nothing is launched and the section shows Codex checked the next time it opens

#### Scenario: Agent removed
- **WHEN** the remembered agent of a worktree is removed from the project
- **THEN** the worktree's launches use the project's default agent
