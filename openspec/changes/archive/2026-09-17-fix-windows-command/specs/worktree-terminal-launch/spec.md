## MODIFIED Requirements

### Requirement: Copy the worktree launch command to the clipboard
The system SHALL let the user copy, to the system clipboard, the shell command needed to start working on a worktree from the worktree management screen, via `CopyWorktreeLaunchCommandUseCase`. The copied command SHALL change into the worktree's directory and then run the project's resolved agent launch command (`ProjectConfig.agentLaunchConfig`), without launching any terminal process or terminal emulator on the user's behalf. The worktree path SHALL be wrapped in double quotes so the copied command is valid when pasted into `cmd.exe`, PowerShell, bash, or zsh alike.

#### Scenario: Successful copy
- **WHEN** the user clicks the worktree row's action to copy the launch command
- **THEN** the app writes `cd "<worktree path>" && <resolved agent command>` to the system clipboard

#### Scenario: Default agent command wraps Headroom
- **WHEN** a project has not customized `AgentLaunchConfig`
- **THEN** the copied command's resolved agent command is `headroom wrap claude`

#### Scenario: Headroom wrapping disabled
- **WHEN** the project's `AgentLaunchConfig.useHeadroom` is `false`
- **THEN** the copied command's resolved agent command is just `AgentLaunchConfig.agentCommand`, without the `headroom wrap` prefix

#### Scenario: Copy does not depend on any terminal emulator being installed
- **WHEN** the user copies the launch command
- **THEN** the app succeeds regardless of which, if any, terminal emulators are installed on the machine, since no emulator is detected or launched
