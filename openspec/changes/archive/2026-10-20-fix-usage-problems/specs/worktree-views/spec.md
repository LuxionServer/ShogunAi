# Spec Delta

## MODIFIED Requirements

### Requirement: Remember each view's grouping
"Active" and "All worktrees" SHALL each offer grouping by state or by project, defaulting to state for "Active" and to project for "All worktrees", and SHALL remember each view's choice across restarts. A single project's view SHALL NOT offer the grouping choice and SHALL always group by state, since grouping by project would show one group.

#### Scenario: Change and restart
- **WHEN** the user groups "Active" by project and restarts the app
- **THEN** "Active" is grouped by project and "All worktrees" keeps its own grouping

#### Scenario: Single project's view
- **WHEN** the user opens a project from the sidebar
- **THEN** the view shows no "by project" option and groups its worktrees by state

#### Scenario: Remembered choice is kept
- **WHEN** the user groups "All worktrees" by project, opens a project and returns to "All worktrees"
- **THEN** "All worktrees" is still grouped by project
