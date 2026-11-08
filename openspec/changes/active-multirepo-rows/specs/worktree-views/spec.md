# Spec Delta

## ADDED Requirements

### Requirement: One row per multirepo change in Active
When "Active" is grouped by state, the worktrees of a group of a multirepo (the same branch in two or more of its repositories) SHALL be listed as one change row instead of one row per worktree. The change row SHALL sit in the state group of the group's combined state, as its header shows it in the multirepo view, and SHALL count as one item in that group's count.

#### Scenario: Change waiting for the user
- **WHEN** `product` has the group `feature/TASK-1` with `app`'s worktree "Agent working" and `api`'s "Waiting for you"
- **THEN** "Active" shows one `TASK-1` row under "Waiting for you", no `TASK-1` row under "Agent working", and the "Waiting for you" header counts it once

#### Scenario: Partly merged change
- **WHEN** `app`'s worktree on `feature/TASK-1` is "Merged" and `api`'s is "Stopped"
- **THEN** the `TASK-1` row is under "Stopped"

#### Scenario: Grouped by project
- **WHEN** the user groups "Active" by project
- **THEN** `app`'s and `api`'s worktrees on `feature/TASK-1` are listed as separate rows in their projects, each with its group tag

### Requirement: Which changes Active lists
"Active" SHALL list a change row while its combined state is active or while one of its worktrees is selected, and SHALL sort change rows with the other rows of their group, by the change's branch for "Name" and by the most recent activity among its worktrees for "Last activity".

#### Scenario: Every worktree merged
- **WHEN** every worktree of `feature/TASK-1` is merged and none is selected
- **THEN** "Active" doesn't list `TASK-1`

#### Scenario: One active worktree is enough
- **WHEN** `app`'s worktree on `feature/TASK-1` is "Inactive" and `api`'s is "Unfinished"
- **THEN** the `TASK-1` row is under "Unfinished"

#### Scenario: Sorted by name with the other rows
- **WHEN** "Unfinished" holds the change `feature/TASK-2` and the loose worktrees `feature/TASK-1` and `feature/TASK-3`, sorted by name
- **THEN** the rows are `TASK-1`, the `TASK-2` change, then `TASK-3`

### Requirement: The change row
A change row SHALL show the combined state's badge, the agents of all the change's worktrees, the change's title (its branch without the prefix up to its last `/`), and as detail its full branch and its repositories' aliases. Its "⋯" menu, also opened with a secondary click, SHALL offer the actions of the group's header in the multirepo view and "Show in multirepo", which opens the multirepo view with the group shown and selected.

#### Scenario: Change row content
- **WHEN** `feature/TASK-1` has worktrees in `app` and `api` and an agent runs in `api`'s
- **THEN** its row shows `TASK-1`, `feature/TASK-1 · api, app` and the agent

#### Scenario: Open the change in the multirepo view
- **WHEN** the user picks "Show in multirepo" in the `TASK-1` row's menu
- **THEN** the multirepo view opens with `TASK-1` expanded and scrolled into view

#### Scenario: Open all from Active
- **WHEN** the user picks "Open all in Herdr" in the `TASK-1` row's menu
- **THEN** every worktree of the group opens in Herdr, as from the group's header

### Requirement: Change rows expand into their repositories
A change row SHALL start collapsed, and activating it SHALL expand or collapse it, keeping that choice while the app runs. Expanded, it SHALL be followed by one child row per worktree of the group, sorted by alias, each with its alias, its own state and the usual row details. Child rows SHALL NOT repeat the branch: their name SHALL show the repository's project instead, and they SHALL carry no group tag.

#### Scenario: Expand a change
- **WHEN** the user activates the collapsed `TASK-1` row
- **THEN** an `api` row and an `app` row follow it, each with its own state badge and its project as name, without `feature/TASK-1`

#### Scenario: Merged repository in an active change
- **WHEN** `TASK-1` is expanded and `app`'s worktree is merged while `api`'s is "Stopped"
- **THEN** the `app` child row is shown with its "Merged" state

#### Scenario: Select a repository
- **WHEN** the user activates the `api` child row
- **THEN** that worktree is selected and its detail is shown

### Requirement: Selection and filtering inside changes
Selecting a worktree of a collapsed change row, from the command palette, the menu bar or a notice, SHALL expand it. Collapsing a change row whose worktree is selected SHALL clear the selection. Keyboard navigation SHALL move through the child rows of expanded changes and skip collapsed ones. While a text filter is active, change rows with a matching worktree SHALL be shown expanded with only their matching child rows.

#### Scenario: Pick a hidden repository
- **WHEN** the user picks `api`'s worktree on `feature/TASK-1` in the command palette while `TASK-1` is collapsed in "Active"
- **THEN** `TASK-1` expands and the `api` row is selected

#### Scenario: Collapse the selected repository's change
- **WHEN** the `api` row of `TASK-1` is selected and the user collapses `TASK-1`
- **THEN** nothing is selected

#### Scenario: Filter by text
- **WHEN** the user types `api-TASK-1` in the filter and only `api`'s worktree path matches
- **THEN** the `TASK-1` row is shown expanded with only its `api` child row
