## MODIFIED Requirements

### Requirement: Open a worktree in Orca from the desktop app
When Orca is installed, the worktree's detail, its row's context menu, the command palette, the tray and the menu bar SHALL offer an "Open in Orca" action, as the primary action when Orca is the default runner and in "Open with…" otherwise, that opens the worktree in Orca under its repository. The action SHALL open a focused terminal in the worktree whose shell has the worktree's environment, and SHALL run the project's agent launch command in it.

#### Scenario: Worktree with an environment
- **WHEN** the user opens in Orca a worktree whose repository declares `env` or a provider that adds variables
- **THEN** Orca shows the worktree under its repository with a focused terminal in it, the terminal's shell has every variable `shogun env` prints for that worktree, and the agent command runs with them

#### Scenario: Worktree without an environment
- **WHEN** the repository declares no environment
- **THEN** the terminal opens in the worktree and runs the agent command, with no extra variables

#### Scenario: Agent command and permissions
- **WHEN** the project's agent launch configuration resolves to `claude`
- **THEN** that command runs, the same one the copied launch command uses, with the session name ShogunAi gives Claude sessions, and Orca's own agent launch settings, such as its full-access default, aren't applied
