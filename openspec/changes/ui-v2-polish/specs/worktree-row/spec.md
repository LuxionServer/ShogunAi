## MODIFIED Requirements

### Requirement: Name cell
The name cell's first line SHALL show the branch, or that the HEAD is detached, with the branch and the worktree's origin in its tooltip. Its second line SHALL only identify the row and show, in this order and separated by " · ", the worktree's group tag when it belongs to a group and the view isn't the multirepo view, the worktree's folder name only when it differs from the last segment of the branch, and the project's name in views that span several projects and aren't grouped by project. The second line SHALL NOT show the origin. The project's name SHALL be truncated last.

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
