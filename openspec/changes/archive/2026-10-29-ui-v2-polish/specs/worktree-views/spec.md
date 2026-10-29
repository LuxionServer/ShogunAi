## MODIFIED Requirements

### Requirement: Project group headers
Each project group SHALL have a header with the project's name, preceded by its multirepo's name when it belongs to one (`product / app`), its number of worktrees, a collapse toggle, and the project's actions: "Project settings" and "New worktree" as icons, and a "More" (⋯) menu with "Clean up" in danger text, disabled with its reason when the project has no candidates. The header SHALL NOT show "Clean up" as a button. The collapsed state of each group SHALL be remembered across restarts.

#### Scenario: Collapse a group
- **WHEN** the user collapses the `api` group and restarts the app
- **THEN** the `api` group is still collapsed in "All worktrees"

#### Scenario: New worktree from a group
- **WHEN** the user activates "New worktree" on the `api` group's header
- **THEN** the "New worktree" dialog opens for `api`

#### Scenario: Project of a multirepo
- **WHEN** "All worktrees" is grouped by project and `app` belongs to the multirepo `product`
- **THEN** its header reads `product / app`

#### Scenario: Clean up from a project group
- **WHEN** `api` has cleanup candidates and the user opens the ⋯ menu of its group header
- **THEN** it offers "Clean up" in danger text, which opens the cleanup confirmation for `api`

#### Scenario: Nothing to clean up in a project group
- **WHEN** `api` has no cleanup candidates
- **THEN** the ⋯ menu's "Clean up" is disabled and says there is nothing to clean up
