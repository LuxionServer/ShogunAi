# Spec Delta

## ADDED Requirements

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
