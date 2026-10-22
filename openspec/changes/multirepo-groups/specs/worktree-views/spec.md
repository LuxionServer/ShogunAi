## MODIFIED Requirements

### Requirement: Project group headers
Each project group SHALL have a header with the project's name, preceded by its multirepo's name when it belongs to one (`product / app`), its number of worktrees, a collapse toggle, and the project's actions: "New worktree", "Clean up" (enabled when the project has candidates) and "Project settings". The collapsed state of each group SHALL be remembered across restarts.

#### Scenario: Collapse a group
- **WHEN** the user collapses the `api` group and restarts the app
- **THEN** the `api` group is still collapsed in "All worktrees"

#### Scenario: New worktree from a group
- **WHEN** the user activates "New worktree" on the `api` group's header
- **THEN** the "New worktree" dialog opens for `api`

#### Scenario: Project of a multirepo
- **WHEN** "All worktrees" is grouped by project and `app` belongs to the multirepo `product`
- **THEN** its header reads `product / app`
