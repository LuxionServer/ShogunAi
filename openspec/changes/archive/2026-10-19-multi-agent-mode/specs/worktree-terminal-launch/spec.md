## ADDED Requirements

### Requirement: Choose the agent for a launch
When a project has several agents, "Copy launch command", "Open in terminal" and "Open in Herdr" SHALL name the agent they will use and let the user pick another of the project's agents, from the worktree's detail, its row's context menu and the command palette. A project with one agent SHALL show the actions as before.

#### Scenario: Launch with another agent
- **WHEN** the project's agents are Claude (default) and Codex, and the user picks Codex for "Copy launch command" on a worktree
- **THEN** the copied command changes into the worktree and runs Codex's command

#### Scenario: Single agent
- **WHEN** the project has only one agent
- **THEN** the launch actions don't name an agent or offer a choice

### Requirement: Set the agent of each worktree
When a project has several agents, the worktree's detail SHALL show the worktree's agent and let the user change it without launching. Picking an agent for a launch SHALL also make it the worktree's agent. The worktree's agent SHALL be remembered on this machine and used by default for its launches; choosing the project's default agent SHALL clear it, and an agent that no longer exists in the project SHALL fall back to the default.

#### Scenario: Continue with the agent the task moved to
- **WHEN** the user launched a worktree with Codex and later opens it in the terminal without choosing
- **THEN** the terminal runs Codex and the action reads "Open in terminal · Codex"

#### Scenario: Set the agent from the detail
- **WHEN** the user sets a worktree's agent to Codex in its detail, without launching it
- **THEN** the detail shows Codex as its agent and the launch actions read "· Codex"

#### Scenario: Agent removed
- **WHEN** the remembered agent of a worktree is removed from the project
- **THEN** the worktree's launches use the project's default agent
