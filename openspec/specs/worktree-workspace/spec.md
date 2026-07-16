# worktree-workspace Specification

## Purpose
TBD - created by archiving change project-worktree-ui. Update Purpose after archive.
## Requirements
### Requirement: List worktrees for the active project
The system SHALL show the active project's worktrees on the worktree management screen using `ListWorktreesUseCase`, including path, branch, and whether each is the main worktree.

#### Scenario: Worktrees loaded successfully
- **WHEN** the worktree management screen opens for a project
- **THEN** it invokes `ListWorktreesUseCase` and displays the returned worktrees, marking the main worktree distinctly

#### Scenario: Listing fails
- **WHEN** `ListWorktreesUseCase` returns a failed `Result` (e.g. `WorktreeError.GitCommandFailed`)
- **THEN** the screen shows an error message derived from the `WorktreeError` instead of a worktree list

### Requirement: Create a worktree for a task
The system SHALL let the user create a new worktree by entering a task id and choosing a branch type (`feature` or `fix`), using `CreateWorktreeUseCase`. Before creating the worktree, the task id SHALL be normalized (trimmed, internal whitespace runs replaced with `-`) and validated as a Git-ref-safe identifier; if the normalized id is still invalid, the use case SHALL fail with `WorktreeError.InvalidTaskId` instead of attempting the Git command.

#### Scenario: Successful creation
- **WHEN** the user enters a non-empty task id, selects a branch type, and confirms
- **THEN** the app invokes `CreateWorktreeUseCase(taskId, branchType)` and, on success, adds the returned `Worktree` to the displayed list

#### Scenario: Task id with spaces is normalized instead of failing
- **WHEN** the user enters a task id containing spaces (e.g. `"TASK 123"`)
- **THEN** `CreateWorktreeUseCase` normalizes it to `"TASK-123"`, creates the worktree and branch using the normalized id, and the displayed `Worktree`/branch reflects the normalized id rather than failing with a Git error

#### Scenario: Task id is invalid even after normalization
- **WHEN** the user enters a task id that, after trimming and replacing spaces with `-`, still contains characters Git disallows in ref names, or that normalizes to an empty string
- **THEN** `CreateWorktreeUseCase` returns a failed `Result` with `WorktreeError.InvalidTaskId` and does not run any Git command

#### Scenario: Creation fails with a domain error
- **WHEN** `CreateWorktreeUseCase` returns a failed `Result` (e.g. `WorktreeError.WorktreeAlreadyExists`, `WorktreeError.SecretFileNotFound`, `WorktreeError.BaseRepositoryNotFound`, `WorktreeError.GitLfsNotFound`, `WorktreeError.InvalidTaskId`)
- **THEN** the screen shows an error message describing the failure and does not add a worktree to the list

#### Scenario: Creation fails because Git LFS is required but not installed
- **WHEN** `git worktree add` fails and its stderr indicates the repository's `post-checkout` hook could not find `git-lfs` on the user's `PATH`
- **THEN** `CreateWorktreeUseCase` removes the partially created worktree (`git worktree remove --force`) and the branch Git already created (`git branch -D`), both best-effort, and returns a failed `Result` with `WorktreeError.GitLfsNotFound` instead of `WorktreeError.GitCommandFailed`, so a subsequent retry with the same task id does not hit `WorktreeAlreadyExists` or a "branch already exists" error

#### Scenario: Task id field shows the normalized value while typing
- **WHEN** the user types a task id containing spaces into the worktree creation field
- **THEN** the field displays the normalized value (spaces replaced with `-`) rather than the raw input with spaces

### Requirement: Remove a worktree
The system SHALL let the user remove a non-main worktree from the list, using `RemoveWorktreeUseCase`, with an option to also delete its local branch. When the removal fails because the worktree has uncommitted or untracked changes, the system SHALL offer the user a confirmation dialog to retry the removal with `--force` instead of leaving the user with no recourse.

#### Scenario: Successful removal
- **WHEN** the user chooses to remove a worktree from the list and confirms
- **THEN** the app invokes `RemoveWorktreeUseCase(worktreePath, branchToDelete, force)` and, on success, removes it from the displayed list

#### Scenario: Removal fails for a reason unrelated to uncommitted changes
- **WHEN** `RemoveWorktreeUseCase` returns a failed `Result` whose error does not indicate that `--force` would resolve it
- **THEN** the screen shows an error message and keeps the worktree in the displayed list, without offering a force-retry dialog

#### Scenario: Removal fails because the worktree has uncommitted or untracked changes
- **WHEN** `RemoveWorktreeUseCase` returns a failed `Result` whose `WorktreeError.GitCommandFailed` indicates the worktree needs `--force` to be removed
- **THEN** the screen shows a confirmation dialog asking whether to force-delete the worktree, instead of only showing the raw error message

#### Scenario: User confirms force removal
- **WHEN** the user confirms the force-delete dialog shown after a failed removal
- **THEN** the app invokes `RemoveWorktreeUseCase(worktreePath, branchToDelete, force = true)` and, on success, removes the worktree from the displayed list and dismisses the dialog

#### Scenario: User cancels force removal
- **WHEN** the user dismisses or cancels the force-delete dialog
- **THEN** the worktree remains in the displayed list and no further removal command is executed

#### Scenario: Main worktree cannot be removed
- **WHEN** the user views the main worktree entry in the list
- **THEN** the screen does not offer a remove action for it

