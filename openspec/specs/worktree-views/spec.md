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

### Requirement: Active worktrees view
The app SHALL offer an "Active" view that lists the active worktrees of every saved project, using the same rows and detail as the other worktree views, with each row showing its project's name.

#### Scenario: Active across projects
- **WHEN** `api` has an agent running in `TASK-1`, and `main-repo` has uncommitted changes in `TASK-7` and a merged `TASK-3`
- **THEN** "Active" lists `TASK-1` (with `api`) and `TASK-7` (with `main-repo`), and not `TASK-3`

### Requirement: What counts as an active worktree
A worktree SHALL be active when an agent is running in it, when it has uncommitted changes, or when it isn't the main worktree, its status is read, and it is neither merged, nor inactive, nor missing its folder. A worktree whose status isn't read yet SHALL only be active when an agent is running in it.

#### Scenario: Freshly created worktree
- **WHEN** a worktree was just created from the main branch's tip, with no changes and no agent
- **THEN** it is active, because it isn't merged, inactive or missing

#### Scenario: Main worktree with changes
- **WHEN** the main worktree has uncommitted changes and no agent
- **THEN** it is active

#### Scenario: Clean main worktree
- **WHEN** the main worktree is clean and has no agent
- **THEN** it isn't active

#### Scenario: Inactive worktree
- **WHEN** a worktree is flagged as inactive
- **THEN** it isn't active

#### Scenario: Status still loading
- **WHEN** a non-main worktree's status hasn't been read and no agent runs in it
- **THEN** it isn't shown in "Active" until its status is read

### Requirement: Keep the selected worktree visible
A worktree that is selected in "Active" SHALL stay listed while it remains selected, even if it stops being active, and SHALL leave the list once it is deselected.

#### Scenario: Agent finishes while selected
- **WHEN** the selected worktree's agent ends and the worktree is clean and merged
- **THEN** it stays in "Active", in the group for its new state, until the user selects something else

### Requirement: Group by state
"Active" SHALL group its worktrees by state, in this order: "Agent working" (an agent is running), "Needs you" (uncommitted changes or commits not pushed to its upstream, and no agent), and "In progress" (any other active worktree). When "All worktrees" is grouped by state, it SHALL also show "Merged", "Inactive" and "Folder missing" groups after those, and a trailing "Other" group for the clean main worktrees and the worktrees whose status isn't read yet, so that no worktree disappears. Empty groups SHALL be hidden.

#### Scenario: Active groups
- **WHEN** one active worktree has an agent, one has uncommitted changes and one is clean and unmerged
- **THEN** "Active" shows them under "Agent working", "Needs you" and "In progress", in that order

#### Scenario: All worktrees by state
- **WHEN** "All worktrees" is grouped by state and two worktrees are merged
- **THEN** a "Merged" group lists them after the active groups

### Requirement: Remember each view's grouping
"Active" and "All worktrees" SHALL each offer grouping by state or by project, defaulting to state for "Active" and to project for "All worktrees", and SHALL remember each view's choice across restarts.

#### Scenario: Change and restart
- **WHEN** the user groups "Active" by project and restarts the app
- **THEN** "Active" is grouped by project and "All worktrees" keeps its own grouping

### Requirement: Nothing active
When no worktree is active, "Active" SHALL say so and offer "New worktree" and "See all worktrees".

#### Scenario: Everything finished
- **WHEN** every worktree of every project is clean and merged, inactive or the main one, with no agent
- **THEN** "Active" shows the empty state with both actions
