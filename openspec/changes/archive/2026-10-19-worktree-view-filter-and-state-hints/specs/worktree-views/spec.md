# Spec Delta

## MODIFIED Requirements

### Requirement: Filter the view by project
Selecting a project in the sidebar SHALL show that project's worktree view, which SHALL NOT offer a project filter. "Active" and "All worktrees" SHALL offer a project filter in their header with "All projects" and each project, which narrows the current view to the picked project without changing the sidebar's selection. Each of the two views SHALL keep its own filter while the app runs.

#### Scenario: Filter from the header
- **WHEN** the user is in "All worktrees" and picks `api` in the header's project filter
- **THEN** only the `api` group is shown, the view is still "All worktrees" and "All worktrees" stays selected in the sidebar

#### Scenario: Back to all projects
- **WHEN** "All worktrees" is filtered to `api` and the user picks "All projects"
- **THEN** every project's group is shown

#### Scenario: Filter Active
- **WHEN** the user is in "Active" and picks `api` in the header's project filter
- **THEN** only `api`'s active worktrees are shown, grouped as before, and "Active" stays selected in the sidebar

#### Scenario: No filter in a project's view
- **WHEN** the user selects `api` in the sidebar
- **THEN** the view shows `api`'s worktrees and its header has no project filter

## ADDED Requirements

### Requirement: State groups explain themselves
Each state group header SHALL show, next to its title, a hint of a few words saying what puts a worktree in that group, and SHALL show the full criterion in a tooltip on hover. Hints and tooltips SHALL be translated like the rest of the interface.

#### Scenario: Hint in the header
- **WHEN** "Active" shows a "Needs you" group
- **THEN** its header reads "Needs you" with the hint "uncommitted or unpushed"

#### Scenario: Full criterion on hover
- **WHEN** the user hovers the "Inactive" group's title
- **THEN** a tooltip says the worktrees are clean, have no agent running and no commit in the last 14 days
