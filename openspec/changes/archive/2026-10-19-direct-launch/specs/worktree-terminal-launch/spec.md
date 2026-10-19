## ADDED Requirements

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
