## ADDED Requirements

### Requirement: Focus the worktree's agent in Herdr
When Herdr reports an agent in a worktree, the worktree's "Open in Herdr" action SHALL focus that agent's pane instead of opening or focusing the workspace, and SHALL NOT type anything into it.

#### Scenario: Agent pane focused
- **WHEN** Herdr reports a blocked `claude` agent in a worktree and the user activates "Open in Herdr"
- **THEN** Herdr focuses that agent's pane

### Requirement: Reading Herdr's agents doesn't slow the list
Reading agents from Herdr SHALL run at most once per check for all projects, SHALL run nothing when Herdr isn't installed, and after a failed read SHALL wait at least 30 seconds before trying again. A failed or slow read SHALL NOT delay showing the rest of the worktrees' state.

#### Scenario: Server stopped
- **WHEN** Herdr's server isn't running during several consecutive checks
- **THEN** ShogunAi runs `herdr` at most once every 30 seconds and shows agents without states meanwhile
