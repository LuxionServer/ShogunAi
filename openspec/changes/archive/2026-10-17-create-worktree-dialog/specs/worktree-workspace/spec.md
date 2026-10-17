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
- **WHEN** `CreateWorktreeUseCase` returns a failed `Result` (e.g. `WorktreeError.WorktreeAlreadyExists`, `WorktreeError.SecretFileNotFound`, `WorktreeError.BaseRepositoryNotFound`, `WorktreeError.GitLfsNotFound`, `WorktreeError.InvalidTaskId`)
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

### Requirement: Create a worktree from an existing branch
The system SHALL let the user create a new worktree for a branch that already exists locally, from the "Existing branch" mode of the "New worktree" dialog, using `CreateWorktreeFromBranchUseCase`, instead of only being able to create worktrees for brand-new branches. The worktree's directory is derived from the branch name: if the branch starts with a known `BranchType` prefix (`feature/` or `fix/`), that prefix is stripped first; the remaining string then has every `/` replaced with `-`. While `CreateWorktreeFromBranchUseCase` is running, the dialog SHALL show a loading indicator and disable its inputs, the new/existing-branch mode toggle and its actions until the call completes.

#### Scenario: Successful creation
- **WHEN** the user selects "Existing branch" mode in the dialog, picks a branch not already checked out from the list, and confirms
- **THEN** the app invokes `CreateWorktreeFromBranchUseCase(branch)`, which runs `git worktree add <path> <branch>` (without creating a new branch), copies the project's secret files into the new worktree, and on success closes the dialog and adds the returned `Worktree` to the displayed list

#### Scenario: Directory matches the new-branch flow for a known branch type prefix
- **WHEN** the requested branch starts with a known `BranchType` prefix, e.g. `feature/TASK-123`
- **THEN** the worktree directory is `<worktreesRoot>/TASK-123` (the prefix stripped), the same directory `CreateWorktreeUseCase` would have produced for that task id and branch type, instead of `<worktreesRoot>/feature-TASK-123`

#### Scenario: Creation fails because the branch doesn't exist
- **WHEN** the requested branch does not resolve to an existing local ref
- **THEN** `CreateWorktreeFromBranchUseCase` returns a failed `Result` with `WorktreeError.BranchNotFound` and no worktree is created

#### Scenario: Creation fails because the branch is already checked out elsewhere
- **WHEN** `git worktree add` fails because the requested branch is already checked out in another worktree or in the base repository
- **THEN** `CreateWorktreeFromBranchUseCase` returns a failed `Result` with `WorktreeError.BranchAlreadyCheckedOut` instead of `WorktreeError.GitCommandFailed`, and the dialog stays open showing an error message describing the conflict

#### Scenario: Creation fails because the destination directory already exists
- **WHEN** the derived branch name maps to a worktree directory that already exists
- **THEN** `CreateWorktreeFromBranchUseCase` returns a failed `Result` with `WorktreeError.WorktreeAlreadyExists` without touching Git

#### Scenario: Creation fails because Git LFS is required but not installed
- **WHEN** `git worktree add` fails and its stderr indicates the repository's `post-checkout` hook could not find `git-lfs` on the user's `PATH`
- **THEN** `CreateWorktreeFromBranchUseCase` removes the partially created worktree (`git worktree remove --force`), best-effort, and returns a failed `Result` with `WorktreeError.GitLfsNotFound` instead of `WorktreeError.GitCommandFailed`

#### Scenario: Creation fails while copying secrets
- **WHEN** copying a secret file into the new worktree fails after `git worktree add` succeeded
- **THEN** `CreateWorktreeFromBranchUseCase` removes the partially created worktree (`git worktree remove --force`), best-effort, and returns a failed `Result` with `WorktreeError.SecretCopyFailed`

#### Scenario: Creation from an existing branch is in progress
- **WHEN** the user confirms creation from an existing branch and `CreateWorktreeFromBranchUseCase` has not yet returned
- **THEN** the dialog shows a loading indicator and its inputs, the mode toggle and its actions are disabled, preventing a duplicate submission until the call completes

### Requirement: Show feedback when a worktree is created successfully
The system SHALL show a visible, transient confirmation when a worktree is created successfully, in addition to adding it to the displayed list. The confirmation SHALL offer an action to copy the new worktree's launch command.

#### Scenario: Worktree created successfully
- **WHEN** a worktree is created successfully
- **THEN** the app shows a transient success message confirming the creation, which disappears automatically after a short delay

#### Scenario: Copy the launch command from the confirmation
- **WHEN** the user activates "Copy launch command" on the creation confirmation
- **THEN** the new worktree's launch command is copied to the clipboard, as with the worktree's own copy action

## ADDED Requirements

### Requirement: Open the new worktree dialog
The project view SHALL offer a "New worktree" action in its header that opens the "New worktree" dialog with the first field focused. Escape or "Cancel" SHALL close the dialog without creating anything, and reopening it SHALL start from empty fields.

#### Scenario: Open and cancel
- **WHEN** the user activates "New worktree", types a task id and presses Escape
- **THEN** the dialog closes, nothing is created, and opening it again shows an empty task id

#### Scenario: Enter creates
- **WHEN** the dialog has a valid task id and the user presses Enter
- **THEN** the worktree is created, same as activating "Create worktree"

## REMOVED Requirements

### Requirement: Collapse the "new worktree" form
**Reason**: The creation form is now a dialog that is only shown when the user asks for it, so it no longer takes space that needs collapsing.
**Migration**: Open the form with the "New worktree" action or Cmd/Ctrl+N ("Open the new worktree dialog").
