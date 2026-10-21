# Spec Delta

## MODIFIED Requirements

### Requirement: Active worktrees view
The app SHALL offer an "Active" view that lists the active worktrees of every saved project, using the same rows and detail as the other worktree views, with each row showing its project's name when the view isn't grouped by project.

#### Scenario: Active across projects
- **WHEN** `api` has an agent running in `TASK-1`, and `main-repo` has uncommitted changes in `TASK-7` and a merged `TASK-3`
- **THEN** "Active" lists `TASK-1` (with `api`) and `TASK-7` (with `main-repo`), and not `TASK-3`

### Requirement: What counts as an active worktree
A worktree SHALL be active when its state is "Needs you", "Agent working", "New" or "Stopped". A worktree whose status isn't read yet SHALL only be active when an agent is running in it.

#### Scenario: Freshly created worktree
- **WHEN** a worktree was just created from the main branch's tip, with no changes and no agent
- **THEN** it is active, under "New"

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

### Requirement: Group by state
"Active" SHALL group its worktrees by state, in this order: "Needs you", "Agent working", "New" and "Stopped". When "All worktrees" or a project's view is grouped by state, it SHALL also show "Merged", "Inactive" and "Folder missing" groups after those, and a trailing "Other" group for the clean main worktrees and the worktrees whose status isn't read yet, so that no worktree disappears. Empty groups SHALL be hidden.

#### Scenario: Active groups
- **WHEN** one active worktree has uncommitted changes and no agent, one has a working agent, one was just created and one has pushed commits of its own
- **THEN** "Active" shows them under "Needs you", "Agent working", "New" and "Stopped", in that order

#### Scenario: All worktrees by state
- **WHEN** "All worktrees" is grouped by state and two worktrees are merged
- **THEN** a "Merged" group lists them after the active groups

#### Scenario: Agent waiting for the user
- **WHEN** Herdr reports a worktree's agent as blocked
- **THEN** the worktree is listed under "Needs you" instead of "Agent working"

### Requirement: State groups explain themselves
Each state group header SHALL show, next to its title, a hint of a few words saying what puts a worktree in that group, and SHALL show the full criterion in a tooltip on hover. The "Inactive" criterion SHALL name the configured number of days. Hints and tooltips SHALL be translated like the rest of the interface.

#### Scenario: Hint in the header
- **WHEN** "Active" shows a "New" group
- **THEN** its header reads "New" with the hint "no commits yet"

#### Scenario: Full criterion on hover
- **WHEN** the inactivity threshold is 3 days and the user hovers the "Inactive" group's title
- **THEN** a tooltip says the worktrees are clean, have no agent running and no activity in the last 3 days

## ADDED Requirements

### Requirement: Each worktree has one state
Each worktree SHALL have the first state that applies: "Folder missing" when its folder is gone; "Needs you" when an agent is blocked or done; "Agent working" when an agent is working or runs without a known state; "Needs you" when there are uncommitted changes or unpushed commits; "Other" for the main worktree or an unread status; then "New" with no commits of its own, "Merged", "Inactive", and "Stopped" otherwise. An idle agent SHALL NOT decide the state.

#### Scenario: Idle agent with pending work
- **WHEN** Herdr reports a worktree's agent as idle and the worktree has 2 uncommitted changes
- **THEN** the worktree is under "Needs you"

#### Scenario: Agent working with uncommitted changes
- **WHEN** a worktree's agent is working and the worktree has uncommitted changes
- **THEN** the worktree is under "Agent working"

#### Scenario: Blocked agent
- **WHEN** a worktree's agent is blocked
- **THEN** the worktree is under "Needs you", whatever its Git status

#### Scenario: Idle agent without pending work
- **WHEN** Herdr reports a worktree's agent as idle, the worktree is clean and its pushed branch has commits of its own
- **THEN** the worktree is under "Stopped", and its row shows the idle agent

#### Scenario: Agent without a known state
- **WHEN** a `claude` process runs in a clean worktree and no tool reports its state
- **THEN** the worktree is under "Agent working"

#### Scenario: New worktree
- **WHEN** a worktree's branch has no commits the main branch lacks, isn't merged, has no changes and no agent
- **THEN** the worktree is under "New"

#### Scenario: Pushed work waiting
- **WHEN** a worktree's branch has commits of its own, all pushed to its upstream, no changes and no agent, and its activity date is recent
- **THEN** the worktree is under "Stopped"

### Requirement: Sort worktrees within groups
Every worktree view SHALL offer in its header a sort selector with "Name" and "Last activity", defaulting to "Name", and SHALL remember each view's choice across restarts. "Name" SHALL order by branch in natural order, ignoring case and comparing numbers by value. "Last activity" SHALL order by activity date, most recent first. In both, the main worktree SHALL come first within its group.

#### Scenario: Natural order
- **WHEN** a group has `feature/TASK-10`, `feature/TASK-9` and `feature/task-11`, sorted by name
- **THEN** they are listed as `feature/TASK-9`, `feature/TASK-10`, `feature/task-11`

#### Scenario: By last activity
- **WHEN** the user picks "Last activity" in "Active", where `TASK-1` had a commit an hour ago and `TASK-2` was created 10 minutes ago without commits
- **THEN** `TASK-2` is listed before `TASK-1` within their groups, and the choice is kept after a restart

#### Scenario: Each view keeps its own order
- **WHEN** the user sorts "Active" by last activity
- **THEN** "All worktrees" and the projects' views keep their own order

## REMOVED Requirements

### Requirement: Row context keeps the project in cross-project views
**Reason**: The row's layout is now specified as a whole in `worktree-row`, including when the project's name is shown and that it is truncated last.
**Migration**: See the `worktree-row` requirement "Name cell".
