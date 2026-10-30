## MODIFIED Requirements

### Requirement: Fixed cells in two lines
A worktree's row SHALL have two lines and five cells, in this order: state badge, execution, name, Git and time. Every cell except the name SHALL have a fixed width and keep it when it has nothing to show, so the same information sits at the same position in every row of a view. The execution cell SHALL be left out of every row of a view when no row of that view has anything to show in it, and shown again in every row as soon as one has. The name cell SHALL take the remaining width and truncate with an ellipsis, showing the full text in a tooltip.

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

### Requirement: Name cell
The name cell's first line SHALL show the branch, or that the HEAD is detached, with the origin in its tooltip; for the main checkout it SHALL be followed by a neutral "main" chip. When the branch doesn't fit, its type prefix (the part up to its last `/`, such as `feature/`) SHALL be truncated before the rest, so the task id stays visible as long as it fits. Its second line SHALL only identify the row, separated by " · ": the group tag outside the multirepo view, the folder when it differs from the branch's last segment and the row isn't the main checkout, and the project's name in views spanning several projects not grouped by project, truncated last. It SHALL NOT show the origin.

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

### Requirement: Time cell
The time cell's first line SHALL show the worktree's activity date relative to now, saying what it measures. When the list is narrow it SHALL show the compact form of the elapsed time ("12 h", "2 d"), with the full text in a tooltip. Its second line SHALL show a lock icon when the worktree is locked, with "Locked" as its tooltip and accessible name, and nothing otherwise.

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
- **THEN** the time cell's second line shows a lock icon whose tooltip reads "Locked", and the "main" chip is in the name cell

#### Scenario: Main worktree
- **WHEN** the row is the main worktree and it isn't locked
- **THEN** the time cell's second line is empty, since the name cell shows the "main" chip
