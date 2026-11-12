## MODIFIED Requirements

### Requirement: Copy the worktree launch command to the clipboard
The system SHALL let the user copy, to the system clipboard, the shell command needed to start working on a worktree, via `CopyWorktreeLaunchCommandUseCase`, from the worktree's detail, its row's hover action, its row's context menu or the copy shortcut. The copied command SHALL change into the worktree's directory and then run the project's resolved agent launch command (`ProjectConfig.agentLaunchConfig`), without launching any terminal process or terminal emulator on the user's behalf.

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

### Requirement: Choose the agent for a launch
When a project has several agents, "Copy launch command", "Open in terminal", "Open in Herdr" and "Open in Orca" SHALL let the user pick another of the project's agents through their "with agent" submenu in the "Open with…" menu and the row's context menu, and through one command palette item per other agent. Their plain entries SHALL use the worktree's agent, which the primary action's tooltip names. A project with one agent SHALL show the actions without agent choice.

#### Scenario: Launch with another agent
- **WHEN** the project's agents are Claude (default) and Codex, and the user picks Codex in "Copy launch command › with agent" on a worktree
- **THEN** the copied command changes into the worktree and runs Codex's command

#### Scenario: Command palette item per agent
- **WHEN** the project's agents are Claude (default) and Codex, and the user opens the command palette
- **THEN** it offers "Open in Orca with Codex" next to "Open in Orca"

#### Scenario: Single agent
- **WHEN** the project has only one agent
- **THEN** the launch actions offer no agent choice

### Requirement: Set the agent of each worktree
When a project has several agents, the "Open with…" menu SHALL show a "With agent" section listing the project's agents with the worktree's one checked, and picking one SHALL set the worktree's agent without launching. Picking an agent for a launch SHALL also make it the worktree's agent.

#### Scenario: Continue with the agent the task moved to
- **WHEN** the user launched a worktree with Codex and later opens it in the terminal without choosing
- **THEN** the terminal runs Codex

#### Scenario: Set the agent from the detail
- **WHEN** the user picks Codex in the "With agent" section of a worktree's "Open with…" menu
- **THEN** nothing is launched and the section shows Codex checked the next time it opens

#### Scenario: Agent removed
- **WHEN** the remembered agent of a worktree is removed from the project
- **THEN** the worktree's launches use the project's default agent

## ADDED Requirements

### Requirement: Quote the worktree path in the launch command
The worktree path in the copied launch command SHALL be quoted so that the pasting shell takes it literally: single quotes on macOS and Linux, double quotes on Windows, where paths can't contain them.

#### Scenario: Quotes per platform
- **WHEN** the user copies the launch command of a worktree
- **THEN** the path is in single quotes on macOS and Linux, and in double quotes on Windows

#### Scenario: Shell syntax in the path
- **WHEN** the worktree's folder is named `x$(id)` on macOS
- **THEN** pasting the copied command into zsh or bash changes into that folder and runs no other command

#### Scenario: Single quote in the path
- **WHEN** the worktree's path contains `'` on macOS or Linux
- **THEN** the quote is escaped and the copied command still changes into that folder

### Requirement: Remember each worktree's agent
A worktree's agent SHALL be remembered on this machine and used by default for its launches. Choosing the project's default agent SHALL clear it, and an agent that no longer exists in the project SHALL fall back to the default.

#### Scenario: Remembered agent used
- **WHEN** the user launched a worktree with Codex and later opens it in the terminal without choosing
- **THEN** the terminal runs Codex

#### Scenario: Default agent chosen
- **WHEN** the worktree's agent is Codex and the user picks Claude, the project's default agent, in its "With agent" section
- **THEN** the worktree no longer has an agent of its own and its launches use the project's default agent

#### Scenario: Remembered agent removed
- **WHEN** the remembered agent of a worktree is removed from the project
- **THEN** the worktree's launches use the project's default agent
