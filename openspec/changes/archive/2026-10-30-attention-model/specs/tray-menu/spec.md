## MODIFIED Requirements

### Requirement: Actions on a recent worktree
Each recent worktree SHALL have a submenu with "Show in ShogunAi", "Open in <default runner>", an "Open with" submenu with the worktree's other available open actions from the action catalog, and "Copy launch command", behaving as the same actions in the worktree's detail and using the worktree's agent. Its label SHALL name the branch and the project and mark when an agent is working in it or it is waiting for the user. Unfinished work SHALL NOT mark the label.

#### Scenario: Open in the default runner from the menu
- **WHEN** the default runner is Herdr and the user chooses "Open in Herdr" for a recent worktree
- **THEN** the worktree opens in Herdr with its environment and agent, as from the detail, without bringing the main window forward

#### Scenario: Open in the terminal from the menu
- **WHEN** the chosen terminal is iTerm2 and the user chooses "Open with › Open in iTerm2" for a recent worktree
- **THEN** iTerm2 opens the worktree with its environment and agent, as from the detail, without bringing the main window forward

#### Scenario: Agent waiting
- **WHEN** Herdr reports a recent worktree's agent as blocked
- **THEN** the worktree's label in the menu marks it as waiting for the user

#### Scenario: Unfinished worktree
- **WHEN** a recent worktree has uncommitted changes and no agent
- **THEN** its label in the menu has no mark

#### Scenario: Launch fails
- **WHEN** a launch action from the menu fails
- **THEN** the main window comes forward and shows the error
