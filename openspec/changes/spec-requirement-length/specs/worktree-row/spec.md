## MODIFIED Requirements

### Requirement: Fixed cells in two lines
A worktree's row SHALL have two lines and five cells, in this order: state badge, execution, name, Git and time. Every cell except the name SHALL have a fixed width and keep it when it has nothing to show, so the same information sits at the same position in every row of a view. The name cell SHALL take the remaining width and truncate with an ellipsis, showing the full text in a tooltip.

#### Scenario: Rows with different information
- **WHEN** one row has an agent, an emulator and uncommitted changes, and the next row has none of them
- **THEN** both rows' branch names, Git cells and dates start at the same horizontal positions

#### Scenario: Long branch name
- **WHEN** a branch name doesn't fit its cell
- **THEN** it is cut with an ellipsis and its full name is shown on hover, and the other cells keep their positions

#### Scenario: No row uses the execution cell
- **WHEN** no worktree of the view has an agent, a running device, a desktop turn or a running headless app
- **THEN** no row of the view has an execution cell, and the name cell starts right after the state badge

#### Scenario: An agent starts
- **WHEN** an agent starts in one worktree of a view whose rows had no execution cell
- **THEN** every row of the view shows the execution cell again, at the same position

### Requirement: Execution cell
The execution cell SHALL show the worktree's agent on its first line and its emulator or simulator on its second line. The agent SHALL be shown with the agent indicator: the agent icon colored by its state, its name and its state in the shared vocabulary (working, needs you, done, idle or running). The device line SHALL show the device only while it is booting or running, colored by its state.

#### Scenario: Agent and emulator
- **WHEN** Claude is blocked in a worktree whose emulator is running
- **THEN** the first line shows a warning-colored agent icon with "Claude · needs you" and the second line shows the emulator as running

#### Scenario: Several agents
- **WHEN** a worktree has a working Codex and a blocked Claude
- **THEN** the cell shows "Claude · needs you" with "+1", and its tooltip lists both agents with their states

#### Scenario: Nothing running
- **WHEN** a worktree has no agent and no running device
- **THEN** the execution cell is empty and keeps its width

#### Scenario: Focus the agent from the row
- **WHEN** Claude needs the user in a Herdr pane and the user clicks the row's agent indicator, in the full or the compact cell
- **THEN** the row is selected and Claude's pane is focused in Herdr, as "Open in Herdr" does for that worktree

#### Scenario: Focus an agent in Orca
- **WHEN** Codex works in a terminal Orca reports and the user clicks the row's agent indicator
- **THEN** the row is selected and the worktree's terminal is focused in Orca, as "Open in Orca" does for that worktree

### Requirement: Name cell
The name cell's first line SHALL show the branch, or that the HEAD is detached, with the origin in its tooltip; for the main checkout it SHALL be followed by a neutral "main" chip. When the branch doesn't fit, its type prefix (the part up to its last `/`, such as `feature/`) SHALL be truncated before the rest, so the task id stays visible as long as it fits.

#### Scenario: Folder matches the branch
- **WHEN** the branch is `feature/TASK-123` and the folder is `TASK-123`
- **THEN** the second line doesn't show the folder

#### Scenario: Folder differs from the branch
- **WHEN** the branch is `feature/TASK-123` and the folder is `task-123-wt`
- **THEN** the second line shows `task-123-wt`

#### Scenario: Origin in the tooltip
- **WHEN** a worktree was created by ShogunAi
- **THEN** its second line doesn't name the origin, and the branch's tooltip does

#### Scenario: Main checkout
- **WHEN** the main checkout of `main-repo` is on `develop` and is listed in "Active" grouped by state across projects
- **THEN** its first line shows `develop` with a neutral "main" chip, and its second line shows `main-repo` once, not `main-repo · main-repo`

#### Scenario: Narrow row keeps the task id
- **WHEN** the branch `feature/TASK-123` doesn't fit its cell
- **THEN** `feature/` is cut with an ellipsis first and `TASK-123` stays whole

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
The Git cell's first line SHALL show the number of uncommitted changes. Its second line SHALL show that the branch is merged, naming the branch it is merged into in the tooltip; otherwise the commits ahead of ("↑") and behind ("↓") its upstream, with their meaning in the tooltip; otherwise, for a branch with commits of its own and no upstream, a "not pushed" icon whose tooltip and accessible name say the branch isn't pushed. The second line SHALL NOT compare the branch with its base.

#### Scenario: Changes and divergence
- **WHEN** a worktree has 3 uncommitted changes and is 2 ahead of and 1 behind its upstream
- **THEN** the first line shows 3 changes and the second shows ↑2 ↓1, and hovering it says 2 commits aren't pushed and 1 isn't pulled

#### Scenario: Recorded base
- **WHEN** a worktree's branch has recorded base `epic/redesign`, is 2 ahead of it and even with its upstream
- **THEN** the second line is empty, and the comparison with `epic/redesign` is only in the detail

#### Scenario: Branch not pushed
- **WHEN** a worktree's branch has 2 commits of its own and no upstream
- **THEN** the second line shows the "not pushed" icon, and hovering it says the branch isn't pushed

#### Scenario: New branch without upstream
- **WHEN** a worktree's branch has no commits of its own and no upstream
- **THEN** the second line is empty

#### Scenario: Merged
- **WHEN** a worktree's branch is merged into `develop`
- **THEN** the second line shows it as merged, with `develop` on hover

#### Scenario: Status unavailable
- **WHEN** a worktree's status can't be read
- **THEN** the Git cell shows a muted dash, and hovering it says the status is unavailable

#### Scenario: Merged into the recorded base
- **WHEN** a worktree's branch is merged into its recorded base `epic/redesign` but not into `develop`
- **THEN** the second line shows it as merged, with `epic/redesign` on hover

## ADDED Requirements

### Requirement: The execution cell only when a row uses it
The execution cell SHALL be left out of every row of a view when no row of that view has anything to show in it, and shown again in every row as soon as one has.

#### Scenario: Unused in the view
- **WHEN** no worktree of the view has an agent, a running device, a desktop turn or a running headless app
- **THEN** no row of the view has an execution cell, and the name cell starts right after the state badge

#### Scenario: Used again
- **WHEN** an agent starts in one worktree of a view whose rows had no execution cell
- **THEN** every row of the view shows the execution cell again, at the same position

### Requirement: Several agents in the execution cell
With several agents in a worktree, the execution cell SHALL show the most urgent one with a "+N" count, and its tooltip SHALL list every agent with its state, the source of the state and when it was read.

#### Scenario: Most urgent agent first
- **WHEN** a worktree has a working Codex and a blocked Claude
- **THEN** the cell shows "Claude · needs you" with "+1", and its tooltip lists both agents with their states, where each state came from and when it was read

### Requirement: Second line of the name cell
The name cell's second line SHALL only identify the row, separated by " · ": the group tag outside the multirepo view, the folder when it differs from the branch's last segment and the row isn't the main checkout, and the project's name in views spanning several projects not grouped by project, truncated last. It SHALL NOT show the origin.

#### Scenario: Folder only when it differs
- **WHEN** the branch is `feature/TASK-123` and the folder is `task-123-wt`
- **THEN** the second line shows `task-123-wt`

#### Scenario: No origin
- **WHEN** a worktree was created by ShogunAi
- **THEN** its second line doesn't name the origin

#### Scenario: Tag, then project
- **WHEN** a worktree of the group `TASK-7` with `app` is listed in "Active" grouped by state
- **THEN** its second line reads `TASK-7 · +app · <project>`, with a separator between the tag and the project

#### Scenario: Project truncated last
- **WHEN** a row in "Active" grouped by state doesn't fit its second line
- **THEN** the group tag and the folder are truncated before the project's name

### Requirement: Git cell while loading or unreadable
While a worktree's status is loading, the Git cell SHALL show a loading indicator; when the status can't be read, a muted dash with the reason in its tooltip.

#### Scenario: Loading
- **WHEN** a worktree's status hasn't been read yet
- **THEN** the Git cell shows a loading indicator

#### Scenario: Unreadable
- **WHEN** a worktree's status can't be read
- **THEN** the Git cell shows a muted dash, and hovering it says the status is unavailable
