# worktree-row Specification

## Purpose
Defines the layout of a worktree's row in every worktree view, so every piece of information has a fixed place and two worktrees can be compared at a glance.

## Requirements

### Requirement: Fixed cells in two lines
A worktree's row SHALL have two lines and five cells, in this order: state badge, execution, name, Git and time. Every cell except the name SHALL have a fixed width and keep it when it has nothing to show, so the same information sits at the same position in every row of a view. The name cell SHALL take the remaining width and truncate with an ellipsis, showing the full text in a tooltip.

#### Scenario: Rows with different information
- **WHEN** one row has an agent, an emulator and uncommitted changes, and the next row has none of them
- **THEN** both rows' branch names, Git cells and dates start at the same horizontal positions

#### Scenario: Long branch name
- **WHEN** a branch name doesn't fit its cell
- **THEN** it is cut with an ellipsis and its full name is shown on hover, and the other cells keep their positions

### Requirement: State badge
The state badge SHALL be a circle in the color of the worktree's state with an icon inside that is distinct for each state, so the state can be told without seeing color. Hovering it SHALL show the state's name, and assistive technologies SHALL read the state's name.

#### Scenario: Needs you
- **WHEN** a worktree needs the user
- **THEN** its badge shows the "needs you" icon in the warning color, and its tooltip and accessible name say "Needs you"

#### Scenario: Same color, different icon
- **WHEN** two states share a similar color
- **THEN** their badges still differ by their icon

### Requirement: Execution cell
The execution cell SHALL show the worktree's agent on its first line and its emulator or simulator on its second line. The agent SHALL be shown with its name and its state (working, blocked, done, idle or unknown), its icon colored by that state. With several agents, the most urgent one SHALL be shown with a "+N" count, and the tooltip SHALL list every agent with its state. The device line SHALL show the device only while it is booting or running, colored by its state.

#### Scenario: Agent and emulator
- **WHEN** Claude is blocked in a worktree whose emulator is running
- **THEN** the first line shows Claude as blocked in the warning color and the second line shows the emulator as running

#### Scenario: Several agents
- **WHEN** a worktree has a working Codex and a blocked Claude
- **THEN** the cell shows Claude as blocked with "+1", and its tooltip lists both agents with their states

#### Scenario: Nothing running
- **WHEN** a worktree has no agent and no running device
- **THEN** the execution cell is empty and keeps its width

### Requirement: Second line of the execution cell
The execution cell's second line SHALL show, in this order, that the worktree holds the desktop turn or waits for it with its place in line, its emulator or simulator while booting or running, and its running headless app. When more than one applies, it SHALL show the first with a "+N" count, and its tooltip SHALL list them all.

#### Scenario: Holding the desktop
- **WHEN** a worktree holds the desktop turn and has no running device
- **THEN** the second line shows that it has the desktop, and its tooltip says until when

#### Scenario: Waiting for the desktop with a running app
- **WHEN** a worktree is second in line for the desktop and its headless app is running
- **THEN** the second line shows that it waits for the desktop, second in line, with "+1", and its tooltip lists the wait and the running app

#### Scenario: Headless app only
- **WHEN** a worktree's headless app is running and nothing else applies
- **THEN** the second line shows the app as running in the background

### Requirement: Execution cell in a narrow list
When the list is too narrow to show the execution cell's labels, the cell SHALL collapse to icons only, with the same colors by state as the full cell, and the labels in their tooltips. Every row of the view SHALL collapse at the same time.

#### Scenario: Narrow list
- **WHEN** the window is narrowed below the width that fits the execution labels
- **THEN** every row shows only the agent and device icons, the Claude icon still colored as blocked, and hovering it says "Claude: blocked"

### Requirement: Name cell
The name cell's first line SHALL show the branch, or that the HEAD is detached. Its second line SHALL show, in this order, the worktree's group tag when it belongs to a group and the view isn't the multirepo view, the worktree's folder name only when it differs from the last segment of the branch, the worktree's origin, and the project's name in views that span several projects and aren't grouped by project. The project's name SHALL be truncated last.

#### Scenario: Folder matches the branch
- **WHEN** the branch is `feature/TASK-123` and the folder is `TASK-123`
- **THEN** the second line doesn't show the folder

#### Scenario: Folder differs from the branch
- **WHEN** the branch is `feature/TASK-123` and the folder is `task-123-wt`
- **THEN** the second line shows `task-123-wt` before the origin

#### Scenario: Worktree in a group
- **WHEN** the worktree belongs to the group `TASK-12` with `api`, and the view is `app`'s
- **THEN** the second line starts with the tag `TASK-12 · +api`

#### Scenario: Grouped by state across projects
- **WHEN** "Active" is grouped by state and lists worktrees of `api` and `main-repo`
- **THEN** each row's second line shows its project's name

#### Scenario: Grouped by project
- **WHEN** "All worktrees" is grouped by project
- **THEN** no row shows its project's name, since its group header does

#### Scenario: Narrow row across projects
- **WHEN** a row in "Active" grouped by state doesn't fit its second line
- **THEN** the folder and the origin are truncated before the project's name

### Requirement: Git cell
The Git cell's first line SHALL show the number of uncommitted changes, and its second line the commits ahead of and behind the main branch, or that the branch is merged into it. The main branch's name SHALL be shown in the tooltip. While the status is loading, the cell SHALL show a loading indicator; when the status can't be read, a muted dash with the reason in its tooltip.

#### Scenario: Changes and divergence
- **WHEN** a worktree has 3 uncommitted changes and is 2 ahead of and 1 behind `develop`
- **THEN** the first line shows 3 changes and the second shows 2 ahead and 1 behind, with `develop` on hover

#### Scenario: Merged
- **WHEN** a worktree's branch is merged into `develop`
- **THEN** the second line shows it as merged, with `develop` on hover

#### Scenario: Status unavailable
- **WHEN** a worktree's status can't be read
- **THEN** the Git cell shows a muted dash, and hovering it says the status is unavailable

### Requirement: Time cell
The time cell's first line SHALL show the worktree's activity date relative to now, saying what it measures. Its second line SHALL show the worktree's fixed marks: main worktree and locked.

#### Scenario: Branch with commits
- **WHEN** a worktree's branch has a commit of its own made 3 hours ago
- **THEN** the first line reads "commit 3 h ago" (or its translation)

#### Scenario: New worktree
- **WHEN** a worktree was created 2 hours ago from `develop` and has no commits of its own
- **THEN** the first line reads "created 2 h ago" (or its translation), not the date of `develop`'s last commit

#### Scenario: Locked main worktree
- **WHEN** the row is the main worktree and it is locked
- **THEN** the second line shows the main and locked marks

### Requirement: Row actions keep their place
The row's hover action (copy the launch command) SHALL keep its fixed slot at the end of the row, and the row's context menu SHALL keep its current actions.

#### Scenario: Hover
- **WHEN** the user hovers a row
- **THEN** the copy action appears at the row's end without moving any cell
