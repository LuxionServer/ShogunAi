# worktree-views Specification

## Purpose
Defines the views that show worktrees across projects, so the user can watch every worktree in one place and narrow the view to one project or to a text when needed.

## Requirements

### Requirement: All worktrees view
The app SHALL offer an "All worktrees" view that lists the worktrees of every saved project, grouped by project in the sidebar's order, using the same rows and detail as the project view.

#### Scenario: Two projects
- **WHEN** the projects `api` (2 worktrees) and `main-repo` (3 worktrees) are saved and the user opens "All worktrees"
- **THEN** the view shows an `api` group with 2 rows and a `main-repo` group with 3 rows

### Requirement: Filter the view by project
Selecting a project in the sidebar SHALL show the worktree view filtered to that project. The view's header SHALL offer a project filter with "All projects" and each project, kept in sync with the sidebar's selection.

#### Scenario: Filter from the header
- **WHEN** the user is in "All worktrees" and picks `api` in the header's project filter
- **THEN** only the `api` group is shown and `api` is marked as selected in the sidebar

#### Scenario: Back to all projects
- **WHEN** the view is filtered to `api` and the user picks "All projects"
- **THEN** every project's group is shown and "All worktrees" is marked as selected in the sidebar

### Requirement: Filter the view by text
The view SHALL offer a text filter, focused with Cmd/Ctrl+F, that keeps only the worktrees whose branch or folder name contains the text, ignoring case. Groups without matching worktrees SHALL be hidden while the filter is active, and clearing it SHALL show every worktree again.

#### Scenario: Filter by task id
- **WHEN** the user types `task-12` in the text filter
- **THEN** only worktrees whose branch or folder contains `TASK-12`, in any case, remain, and groups with none are hidden

#### Scenario: No match
- **WHEN** no worktree matches the text
- **THEN** the view says that no worktree matches the filter, with an action to clear it

### Requirement: Project group headers
Each project group SHALL have a header with the project's name, its number of worktrees, a collapse toggle, and the project's actions: "New worktree", "Clean up" (enabled when the project has candidates) and "Project settings". The collapsed state of each group SHALL be remembered across restarts.

#### Scenario: Collapse a group
- **WHEN** the user collapses the `api` group and restarts the app
- **THEN** the `api` group is still collapsed in "All worktrees"

#### Scenario: New worktree from a group
- **WHEN** the user activates "New worktree" on the `api` group's header
- **THEN** the "New worktree" dialog opens for `api`

### Requirement: Projects load independently
The view SHALL load each project's worktrees independently, showing each group as soon as its list is read. A project whose list can't be read SHALL show its error inside its group, and the other groups SHALL load and work normally.

#### Scenario: One repository missing
- **WHEN** `api`'s base repository no longer exists and the user opens "All worktrees"
- **THEN** the `api` group shows an error that its repository wasn't found, and `main-repo`'s worktrees are listed normally

### Requirement: New worktree from a view spanning several projects
When "New worktree" is started from the unfiltered view, the dialog SHALL let the user pick the project, defaulting to the project used for the last worktree created, or the first project when there is none.

#### Scenario: Pick the project
- **WHEN** the user presses Cmd/Ctrl+N in "All worktrees" after last creating a worktree in `api`
- **THEN** the dialog opens with `api` selected as the project, and the user can change it before creating

#### Scenario: Filtered view
- **WHEN** the user presses Cmd/Ctrl+N with the view filtered to `main-repo`
- **THEN** the dialog creates the worktree in `main-repo` without asking for the project
