## MODIFIED Requirements

### Requirement: Active worktrees view
The app SHALL offer an "Active" view that lists the active worktrees of every saved project, using the same rows and detail as the other worktree views, with each row showing its project's name when the view isn't grouped by project.

#### Scenario: Active across projects
- **WHEN** `api` has an agent running in `TASK-1`, and `main-repo` has uncommitted changes in `TASK-7`, whose last commit of its own is from today, and a merged `TASK-3`
- **THEN** "Active" lists `TASK-1` (with `api`) and `TASK-7` (with `main-repo`), and not `TASK-3`

### Requirement: What counts as an active worktree
A worktree SHALL be active when its state is "Waiting for you", "Agent working", "Unfinished", "New" or "Stopped". A worktree whose status isn't read yet SHALL only be active when an agent is running in it.

#### Scenario: Freshly created worktree
- **WHEN** a worktree was just created from the main branch's tip, with no changes and no agent
- **THEN** it is active, under "New"

#### Scenario: Main worktree with changes
- **WHEN** the main worktree has uncommitted changes and no agent
- **THEN** it isn't active, and in a view grouped by state it is under "Other" with its changes still shown in its row

#### Scenario: Clean main worktree
- **WHEN** the main worktree is clean and has no agent
- **THEN** it isn't active

#### Scenario: Main worktree with an agent waiting
- **WHEN** an agent in the main worktree is blocked
- **THEN** it is active, under "Waiting for you"

#### Scenario: Inactive worktree
- **WHEN** a worktree is flagged as inactive
- **THEN** it isn't active

#### Scenario: Status still loading
- **WHEN** a non-main worktree's status hasn't been read and no agent runs in it
- **THEN** it isn't shown in "Active" until its status is read

### Requirement: Group by state
"Active" SHALL group its worktrees by state, in this order: "Waiting for you", "Agent working", "Unfinished", "New" and "Stopped". When "All worktrees" or a project's view is grouped by state, it SHALL also show "Merged", "Inactive" and "Folder missing" groups after those, and a trailing "Other" group for the main worktrees without an agent and the worktrees whose status isn't read yet, so that no worktree disappears. Empty groups SHALL be hidden.

#### Scenario: Active groups
- **WHEN** one active worktree has a blocked agent, one has a working agent, one has uncommitted changes and no agent, one was just created and one has pushed commits of its own
- **THEN** "Active" shows them under "Waiting for you", "Agent working", "Unfinished", "New" and "Stopped", in that order

#### Scenario: All worktrees by state
- **WHEN** "All worktrees" is grouped by state and two worktrees are merged
- **THEN** a "Merged" group lists them after the active groups

#### Scenario: Agent waiting for the user
- **WHEN** Herdr reports a worktree's agent as blocked
- **THEN** the worktree is listed under "Waiting for you" instead of "Agent working"

### Requirement: Nothing active
When no worktree is "Waiting for you", and the statuses have been read and no text filter is active, "Active" SHALL say "Nothing needs you" with the number of agents working in its worktrees, or that none is working, and offer "See all worktrees". The notice SHALL sit above the other groups, or alone when the view lists nothing. It SHALL use neutral colors, not a warning or an empty-inbox illustration.

#### Scenario: Agents working, nothing waiting
- **WHEN** two worktrees have a working agent, one is "Unfinished" and none is "Waiting for you"
- **THEN** "Active" shows "Nothing needs you" with "2 agents working" and "See all worktrees" above the "Agent working" and "Unfinished" groups

#### Scenario: Everything finished
- **WHEN** every worktree of every project is clean and merged, inactive or the main one, with no agent
- **THEN** "Active" shows only "Nothing needs you", says no agent is working, and offers "See all worktrees"

#### Scenario: Something waits
- **WHEN** one worktree's agent is done
- **THEN** "Active" doesn't show "Nothing needs you"

#### Scenario: See all worktrees
- **WHEN** the user activates "See all worktrees" in the notice
- **THEN** the "All worktrees" view opens

### Requirement: Each worktree has one state
Each worktree SHALL have the first state that applies: "Folder missing" when its folder is gone; "Waiting for you" when an agent is blocked or done; "Agent working" when an agent is working or runs without a known state; "Other" for an unread status or the main worktree; "Inactive"; "Unfinished" when there are uncommitted changes, commits not pushed to its upstream, or commits of its own and no upstream; then "New" with no commits of its own, "Merged", and "Stopped" otherwise. An idle agent SHALL NOT decide the state.

#### Scenario: Idle agent with pending work
- **WHEN** Herdr reports a worktree's agent as idle and the worktree has 2 uncommitted changes
- **THEN** the worktree is under "Unfinished"

#### Scenario: Agent working with uncommitted changes
- **WHEN** a worktree's agent is working and the worktree has uncommitted changes
- **THEN** the worktree is under "Agent working"

#### Scenario: Blocked agent
- **WHEN** a worktree's agent is blocked
- **THEN** the worktree is under "Waiting for you", whatever its Git status

#### Scenario: Done agent
- **WHEN** a worktree's agent is done and the worktree is clean
- **THEN** the worktree is under "Waiting for you"

#### Scenario: Idle agent without pending work
- **WHEN** Herdr reports a worktree's agent as idle, the worktree is clean and its pushed branch has commits of its own
- **THEN** the worktree is under "Stopped", and its row shows the idle agent

#### Scenario: Agent without a known state
- **WHEN** a `claude` process runs in a clean worktree and no tool reports its state
- **THEN** the worktree is under "Agent working"

#### Scenario: Commits not pushed
- **WHEN** a worktree's branch is 2 commits ahead of its upstream, has no changes and no agent, and its activity date is recent
- **THEN** the worktree is under "Unfinished"

#### Scenario: Branch never pushed
- **WHEN** a worktree's branch has commits of its own, no upstream, no changes and no agent, and its activity date is recent
- **THEN** the worktree is under "Unfinished"

#### Scenario: Old unfinished work
- **WHEN** a worktree has 3 uncommitted changes, no agent, and its last commit of its own is 5 days old, with the default threshold
- **THEN** the worktree is under "Inactive", and its row still shows its 3 changes

#### Scenario: New worktree
- **WHEN** a worktree's branch has no commits the main branch lacks, isn't merged, has no changes and no agent
- **THEN** the worktree is under "New"

#### Scenario: Pushed work waiting
- **WHEN** a worktree's branch has commits of its own, all pushed to its upstream, no changes and no agent, and its activity date is recent
- **THEN** the worktree is under "Stopped"

## ADDED Requirements

### Requirement: Waiting and unfinished headers differ
The "Waiting for you" group header SHALL show its title in the warning color with the "waiting for you" icon. The "Unfinished" group header, like the other groups, SHALL show its title in the regular header color without an icon.

#### Scenario: Both groups shown
- **WHEN** "Active" has a worktree waiting for the user and an unfinished one
- **THEN** the "Waiting for you" header is in the warning color with its icon, and the "Unfinished" header is neutral

### Requirement: Collapse state groups
Each state group header SHALL offer a collapse toggle; a collapsed group SHALL keep its header and count and hide its rows. In "Active", "Stopped" SHALL start collapsed and the other groups expanded; in the other views every group SHALL start expanded. Each view SHALL keep its collapsed groups while the app runs. Collapsing the group of the selected worktree SHALL clear the selection, and selecting a worktree of a collapsed group SHALL expand it.

#### Scenario: Stopped starts collapsed
- **WHEN** the app starts and "Active" has two "Stopped" worktrees
- **THEN** the "Stopped" header shows 2 and its rows are hidden

#### Scenario: Expand Stopped
- **WHEN** the user activates the "Stopped" header's toggle
- **THEN** its rows are shown, and they stay shown when the user switches to another view and back

#### Scenario: Other views start expanded
- **WHEN** the user groups "All worktrees" by state
- **THEN** its "Stopped" group shows its rows

#### Scenario: Select a hidden worktree
- **WHEN** the user picks a "Stopped" worktree in the command palette while that group is collapsed in "Active"
- **THEN** the group expands and the worktree is selected

#### Scenario: Keyboard skips hidden rows
- **WHEN** the user moves the selection with the arrows across a collapsed group
- **THEN** the selection skips that group's rows

### Requirement: New groups at the top stay in view
While the user hasn't scrolled the list away from its top, a group or row that appears above the first one, such as "Waiting for you" once an agent blocks, SHALL be shown at the top instead of being scrolled out of view or covered by the sticky header. A list the user scrolled down SHALL keep its place.

#### Scenario: Agent blocks while the list is at its top
- **WHEN** "Active" shows its groups from the top and an agent becomes blocked
- **THEN** the "Waiting for you" header and its row are visible at the top of the list

#### Scenario: List scrolled down
- **WHEN** the user scrolled "Active" down and an agent becomes blocked
- **THEN** the list keeps showing the rows the user scrolled to
