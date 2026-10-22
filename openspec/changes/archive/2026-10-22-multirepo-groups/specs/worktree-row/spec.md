## MODIFIED Requirements

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
