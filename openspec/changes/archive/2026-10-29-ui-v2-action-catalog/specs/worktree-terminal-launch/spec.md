## MODIFIED Requirements

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
