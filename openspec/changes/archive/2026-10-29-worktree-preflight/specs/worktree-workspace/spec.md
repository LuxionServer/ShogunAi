# Spec Delta

## MODIFIED Requirements

### Requirement: Create a worktree for a task
The system SHALL let the user create a new worktree in the "New worktree" dialog by entering a task id and choosing a branch type (`feature` or `fix`), using `CreateWorktreeUseCase`. Before creating the worktree, the task id SHALL be normalized (trimmed, internal whitespace runs replaced with `-`) and validated as a Git-ref-safe identifier; if the normalized id is still invalid, the use case SHALL fail with `WorktreeError.InvalidTaskId` instead of attempting the Git command. While `CreateWorktreeUseCase` is running, the dialog SHALL show a loading indicator and disable its inputs, the new/existing-branch mode toggle and its "Create worktree" and "Cancel" actions until the call completes.

#### Scenario: Successful creation
- **WHEN** the user enters a non-empty task id in the dialog, selects a branch type, and confirms
- **THEN** the app invokes `CreateWorktreeUseCase(taskId, branchType)` and, on success, closes the dialog and adds the returned `Worktree` to the displayed list

#### Scenario: Task id with spaces is normalized instead of failing
- **WHEN** the user enters a task id containing spaces (e.g. `"TASK 123"`)
- **THEN** `CreateWorktreeUseCase` normalizes it to `"TASK-123"`, creates the worktree and branch using the normalized id, and the displayed `Worktree`/branch reflects the normalized id rather than failing with a Git error

#### Scenario: Task id is invalid even after normalization
- **WHEN** the user enters a task id that, after trimming and replacing spaces with `-`, still contains characters Git disallows in ref names, or that normalizes to an empty string
- **THEN** `CreateWorktreeUseCase` returns a failed `Result` with `WorktreeError.InvalidTaskId` and does not run any Git command

#### Scenario: Creation fails with a domain error
- **WHEN** `CreateWorktreeUseCase` returns a failed `Result` (e.g. `WorktreeError.WorktreeAlreadyExists`, `WorktreeError.LocalFilesUnavailable`, `WorktreeError.BaseRepositoryNotFound`, `WorktreeError.GitLfsNotFound`, `WorktreeError.InvalidTaskId`)
- **THEN** the dialog stays open with the values the user entered, shows an error message describing the failure inside the dialog, and no worktree is added to the list

#### Scenario: Creation fails because Git LFS is required but not installed
- **WHEN** `git worktree add` fails and its stderr indicates the repository's `post-checkout` hook could not find `git-lfs` on the user's `PATH`
- **THEN** `CreateWorktreeUseCase` removes the partially created worktree (`git worktree remove --force`) and the branch Git already created (`git branch -D`), both best-effort, and returns a failed `Result` with `WorktreeError.GitLfsNotFound` instead of `WorktreeError.GitCommandFailed`, so a subsequent retry with the same task id does not hit `WorktreeAlreadyExists` or a "branch already exists" error

#### Scenario: Task id field shows the normalized value while typing
- **WHEN** the user types a task id containing spaces into the dialog's task id field
- **THEN** the field displays the normalized value (spaces replaced with `-`) rather than the raw input with spaces

#### Scenario: Creation is in progress
- **WHEN** the user confirms worktree creation and `CreateWorktreeUseCase` has not yet returned
- **THEN** the dialog shows a loading indicator and its inputs, the mode toggle and its "Create worktree" and "Cancel" actions are disabled, preventing a duplicate submission until the call completes

### Requirement: Local folder errors
When a local files entry is a folder, a folder that doesn't exist in the base repository, or an entry inside it that can't be read, SHALL fail creation or provisioning with `WorktreeError.LocalFilesUnavailable` naming the entry, before any Git command that creates a branch or a worktree. A failure while copying the folder's files SHALL be handled like a failed copy of a file: `WorktreeError.SecretCopyFailed`, and on creation the partially created worktree is removed.

#### Scenario: Folder missing in the base repository
- **WHEN** the local files include `keys` and the base repository has no `keys` folder
- **THEN** creation fails with `WorktreeError.LocalFilesUnavailable` listing `keys` and no worktree or branch is created

#### Scenario: Copy of a folder's file fails
- **WHEN** copying a file of a local folder into the new worktree fails after `git worktree add` succeeded
- **THEN** the partially created worktree is removed, best-effort, and the result is a failed `Result` with `WorktreeError.SecretCopyFailed`

#### Scenario: Folder copy fails on an existing worktree
- **WHEN** copying a file of a local folder fails while provisioning an existing worktree
- **THEN** the worktree is not removed and the operation fails with `WorktreeError.SecretCopyFailed`

### Requirement: Local patterns aren't required to match
A local files entry that is a pattern SHALL NOT produce `WorktreeError.LocalFilesUnavailable` when it matches nothing; exact file and folder entries keep failing with that error when they don't exist in the base repository.

#### Scenario: Pattern and missing file
- **WHEN** the local files include the pattern `*.p12`, which matches nothing, and the file `local.properties`, which doesn't exist in the base repository
- **THEN** creation fails with `WorktreeError.LocalFilesUnavailable` listing only `local.properties`
