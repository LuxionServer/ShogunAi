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
The state badge SHALL be a circle in the color of the worktree's state with an icon inside that is distinct for each state, so the state can be told without seeing color. The "New" state SHALL be an outlined circle in the neutral status color with its icon in that color, not a filled circle in a brand or primary color. Hovering it SHALL show the state's name, and assistive technologies SHALL read the state's name.

#### Scenario: Needs you
- **WHEN** a worktree needs the user
- **THEN** its badge shows the "needs you" icon in the warning color, and its tooltip and accessible name say "Needs you"

#### Scenario: Same color, different icon
- **WHEN** two states share a similar color
- **THEN** their badges still differ by their icon

#### Scenario: New worktree badge
- **WHEN** a worktree has no commits of its own and isn't merged
- **THEN** its badge is an outlined neutral circle with the "new" icon, and its tooltip and accessible name say "New"

### Requirement: Execution cell
The execution cell SHALL show the worktree's agent on its first line and its emulator or simulator on its second line. The agent SHALL be shown with the agent indicator: a dot colored by its state, its name and its state in the shared vocabulary (working, needs you, done, idle or running). With several agents, the most urgent one SHALL be shown with a "+N" count, and the tooltip SHALL list every agent with its state, the source of the state and when it was read. The device line SHALL show the device only while it is booting or running, colored by its state.

#### Scenario: Agent and emulator
- **WHEN** Claude is blocked in a worktree whose emulator is running
- **THEN** the first line shows a warning dot with "Claude · needs you" and the second line shows the emulator as running

#### Scenario: Several agents
- **WHEN** a worktree has a working Codex and a blocked Claude
- **THEN** the cell shows "Claude · needs you" with "+1", and its tooltip lists both agents with their states

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
When the list is too narrow to show the execution cell's labels, the cell SHALL collapse to the agent's dot and the device's icon, with the same colors by state as the full cell, and the labels in their tooltips. Every row of the view SHALL collapse at the same time.

#### Scenario: Narrow list
- **WHEN** the window is narrowed below the width that fits the execution labels
- **THEN** every row shows only the agent's dot and the device icon, Claude's dot still in the warning color, and hovering it says Claude needs you

### Requirement: Name cell
The name cell's first line SHALL show the branch, or that the HEAD is detached, with the origin in its tooltip. Its second line SHALL only identify the row, separated by " · ": the group tag outside the multirepo view, the folder when it differs from the branch's last segment, and the project's name in views spanning several projects not grouped by project, truncated last. It SHALL NOT show the origin.

#### Scenario: Folder matches the branch
- **WHEN** the branch is `feature/TASK-123` and the folder is `TASK-123`
- **THEN** the second line doesn't show the folder

#### Scenario: Folder differs from the branch
- **WHEN** the branch is `feature/TASK-123` and the folder is `task-123-wt`
- **THEN** the second line shows `task-123-wt`

#### Scenario: Origin in the tooltip
- **WHEN** a worktree was created by ShogunAi
- **THEN** its second line doesn't name the origin, and the branch's tooltip does

#### Scenario: Worktree in a group
- **WHEN** the worktree belongs to the group `TASK-12` with `api`, and the view is `app`'s
- **THEN** the second line starts with the tag `TASK-12 · +api`

#### Scenario: Group tag and project
- **WHEN** a worktree of the group `TASK-7` with `app` is listed in "Active" grouped by state
- **THEN** its second line reads `TASK-7 · +app · <project>`, with a separator between the tag and the project

#### Scenario: Grouped by state across projects
- **WHEN** "Active" is grouped by state and lists worktrees of `api` and `main-repo`
- **THEN** each row's second line shows its project's name

#### Scenario: Grouped by project
- **WHEN** "All worktrees" is grouped by project
- **THEN** no row shows its project's name, since its group header does

#### Scenario: Narrow row across projects
- **WHEN** a row in "Active" grouped by state doesn't fit its second line
- **THEN** the group tag and the folder are truncated before the project's name

### Requirement: Git cell
The Git cell's first line SHALL show the number of uncommitted changes, and its second line the commits ahead of and behind the worktree's base, or that the branch is merged. The base's name, or the name of the branch it is merged into, SHALL be shown in the tooltip. While the status is loading, the cell SHALL show a loading indicator; when the status can't be read, a muted dash with the reason in its tooltip.

#### Scenario: Changes and divergence
- **WHEN** a worktree has 3 uncommitted changes and is 2 ahead of and 1 behind `develop`
- **THEN** the first line shows 3 changes and the second shows 2 ahead and 1 behind, with `develop` on hover

#### Scenario: Merged
- **WHEN** a worktree's branch is merged into `develop`
- **THEN** the second line shows it as merged, with `develop` on hover

#### Scenario: Status unavailable
- **WHEN** a worktree's status can't be read
- **THEN** the Git cell shows a muted dash, and hovering it says the status is unavailable

#### Scenario: Recorded base
- **WHEN** a worktree's branch has recorded base `epic/redesign` and is 2 ahead of it
- **THEN** the second line shows 2 ahead, with `epic/redesign` on hover

#### Scenario: Merged into the recorded base
- **WHEN** a worktree's branch is merged into its recorded base `epic/redesign` but not into `develop`
- **THEN** the second line shows it as merged, with `epic/redesign` on hover

### Requirement: Time cell
The time cell's first line SHALL show the worktree's activity date relative to now, saying what it measures. When the list is narrow it SHALL show the compact form of the elapsed time ("12 h", "2 d"), with the full text in a tooltip. Its second line SHALL show the worktree's fixed marks: main worktree and locked.

#### Scenario: Branch with commits
- **WHEN** a worktree's branch has a commit of its own made 3 hours ago
- **THEN** the first line reads "commit 3 hours ago" (or its translation)

#### Scenario: New worktree
- **WHEN** a worktree was created 2 hours ago from `develop` and has no commits of its own
- **THEN** the first line reads "created 2 hours ago" (or its translation), not the date of `develop`'s last commit

#### Scenario: Narrow list
- **WHEN** the list is narrow and a worktree's last commit was 12 hours ago
- **THEN** the first line reads "12 h" and its tooltip reads "commit 12 hours ago"

#### Scenario: Compact units
- **WHEN** the list is narrow and the activity was 30 seconds, 5 minutes, 2 days, 3 months or 1 year ago
- **THEN** the first line reads "now", "5 min", "2 d", "3 mo" or "1 y" (or their translations)

#### Scenario: Locked main worktree
- **WHEN** the row is the main worktree and it is locked
- **THEN** the second line shows the main and locked marks

### Requirement: Row actions keep their place
The row's hover action (copy the launch command) SHALL keep its fixed slot at the end of the row, and the row's context menu SHALL keep its current actions.

#### Scenario: Hover
- **WHEN** the user hovers a row
- **THEN** the copy action appears at the row's end without moving any cell

### Requirement: Row identifiers and selection colors
A row's repository alias and group tag SHALL be drawn in the secondary text color, not in the primary or brand color, and the selected row SHALL be drawn on the accent container.

#### Scenario: Multirepo row
- **WHEN** a row of a multirepo view shows its repository alias and its group tag
- **THEN** both use the secondary text color

#### Scenario: Selected row
- **WHEN** the user selects a row
- **THEN** its background is the accent container

### Requirement: Monospace only for data in the row
In a worktree row, the branch, the folder, the repository alias, the Git counts and the device's identifiers SHALL use the monospaced data font, and the agent's state, the relative activity date, the marks and the project's name SHALL use the interface font.

#### Scenario: Row fonts
- **WHEN** a row shows `feature/TASK-123`, "Claude · working", "commit 3 h ago" and the project `main-repo`
- **THEN** the branch is in the data font, and the agent's state, the date and the project's name are in the interface font

### Requirement: Rows of every worktree view share the list row
The worktree rows of the Active, All, project and multirepo views, by state and by change, SHALL be drawn with the shared list row, so their hover, selection, keyboard activation and compact behavior are the same.

#### Scenario: Keyboard in the multirepo view by change
- **WHEN** a worktree row of a multirepo view by change has keyboard focus and the user presses Enter
- **THEN** the worktree is selected and its detail is shown
