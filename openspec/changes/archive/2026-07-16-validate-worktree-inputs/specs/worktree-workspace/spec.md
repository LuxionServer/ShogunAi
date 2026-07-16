## MODIFIED Requirements

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
