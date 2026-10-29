## MODIFIED Requirements

### Requirement: What the palette searches
The palette SHALL search the views (Active, All worktrees, each visible machine resource, Preferences), every saved project, the worktrees of every saved project, the actions "Add project", "New worktree in <project>", "Settings of <project>", "Clean up", "Reload" and "Toggle sidebar", and, when a worktree is selected, its available non-destructive actions from the action catalog, with one item per other agent for agent-aware actions when its project has several agents.

#### Scenario: Find a worktree of another project
- **WHEN** the user is in the view filtered to `main-repo` and types `TASK-9`
- **THEN** the palette lists the worktree `feature/TASK-9` of `api`

#### Scenario: Open with another agent
- **WHEN** a worktree of a project with Claude and Codex is selected, its agent is Claude, Orca is installed, and the user types `orca codex`
- **THEN** the palette lists "Open in Orca with Codex", which opens the selected worktree in Orca running Codex

#### Scenario: Tool not installed
- **WHEN** Orca isn't installed
- **THEN** the palette lists no "Open in Orca" item
