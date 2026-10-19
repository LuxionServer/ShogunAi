# Spec Delta

## ADDED Requirements

### Requirement: A failed creation leaves nothing behind
When a creation fails after Git created the worktree or its new branch, and before post-creation commands run, the system SHALL remove the worktree and delete the branch it created, best-effort, so retrying with the same task id works. A branch that existed before the creation SHALL never be deleted.

#### Scenario: No free port
- **WHEN** creating `TASK-123` fails while reserving its ports
- **THEN** the worktree folder and `feature/TASK-123` are gone, and creating `TASK-123` again succeeds once a port is free

#### Scenario: Existing branch
- **WHEN** creating a worktree from the existing branch `feature/TASK-9` fails while copying its files
- **THEN** the worktree is removed and `feature/TASK-9` is kept

### Requirement: Recognize the main worktree by its location
The system SHALL treat a worktree as the main one when its location is the base repository's, comparing resolved paths, so a trailing separator, a symbolic link, a case difference on macOS or a different separator on Windows doesn't change the result.

#### Scenario: Project saved with a trailing slash
- **WHEN** the project's base repository is saved as `~/projects/main-repo/`
- **THEN** the main worktree is listed as main and is never offered for cleanup

### Requirement: Task ids with a slash map to one folder
A task id containing `/` SHALL produce a worktree folder whose name replaces each `/` with `-`, as worktrees created from existing branches already do, while the branch keeps the `/`.

#### Scenario: Team prefix
- **WHEN** the user creates a worktree for `team/TASK-1`
- **THEN** the branch is `feature/team/TASK-1` and the folder is `<worktrees root>/team-TASK-1`
