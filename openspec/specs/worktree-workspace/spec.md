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
The system SHALL let the user remove a non-main worktree from the list, using `RemoveWorktreeUseCase`, with an option to also delete its local branch. Clicking the remove action SHALL always show a confirmation dialog before any Git command runs, with a checkbox labeled to delete the local branch as well, unchecked by default; the branch is only passed to `RemoveWorktreeUseCase` as `branchToDelete` if the user checks it. When the removal fails because the worktree has uncommitted or untracked changes, the system SHALL update the same dialog to offer a force retry, reusing the user's branch-deletion choice, instead of leaving the user with no recourse.

#### Scenario: Confirmation dialog shown before removing
- **WHEN** the user activates the remove action for a non-main worktree
- **THEN** the app shows a confirmation dialog with a "delete local branch" checkbox unchecked by default, and does not invoke `RemoveWorktreeUseCase` until the user confirms

#### Scenario: Successful removal without deleting the branch
- **WHEN** the user confirms the removal dialog with the "delete local branch" checkbox unchecked
- **THEN** the app invokes `RemoveWorktreeUseCase(worktreePath, branchToDelete = null, force = false)` and, on success, removes the worktree from the displayed list and dismisses the dialog

#### Scenario: Successful removal deleting the branch
- **WHEN** the user checks "delete local branch" in the confirmation dialog and confirms
- **THEN** the app invokes `RemoveWorktreeUseCase(worktreePath, branchToDelete = worktree.branch, force = false)` and, on success, removes the worktree from the displayed list and dismisses the dialog

#### Scenario: User cancels the confirmation dialog
- **WHEN** the user dismisses or cancels the confirmation dialog
- **THEN** no Git command is executed and the worktree remains in the displayed list

#### Scenario: Removal fails for a reason unrelated to uncommitted changes
- **WHEN** `RemoveWorktreeUseCase` returns a failed `Result` whose error does not indicate that `--force` would resolve it
- **THEN** the screen shows an error message and keeps the worktree in the displayed list, without escalating the dialog to a force-retry state

#### Scenario: Removal fails because the worktree has uncommitted or untracked changes
- **WHEN** `RemoveWorktreeUseCase` returns a failed `Result` whose `WorktreeError.GitCommandFailed` indicates the worktree needs `--force` to be removed
- **THEN** the same confirmation dialog updates to ask whether to force-delete the worktree, preserving the "delete local branch" checkbox state the user had chosen, instead of only showing the raw error message

#### Scenario: User confirms force removal
- **WHEN** the user confirms the force-delete state of the dialog shown after a failed removal
- **THEN** the app invokes `RemoveWorktreeUseCase(worktreePath, branchToDelete, force = true)` using the branch-deletion choice already selected, and on success removes the worktree from the displayed list and dismisses the dialog

#### Scenario: User cancels force removal
- **WHEN** the user dismisses or cancels the dialog while it is in the force-retry state
- **THEN** the worktree remains in the displayed list and no further removal command is executed

#### Scenario: No branch checkbox for a detached worktree
- **WHEN** the worktree being removed has no associated branch (detached HEAD)
- **THEN** the confirmation dialog does not show the "delete local branch" checkbox

#### Scenario: Main worktree cannot be removed
- **WHEN** the user views the main worktree entry in the list
- **THEN** the screen does not offer a remove action for it

### Requirement: Reload the worktree list
The system SHALL let the user manually trigger a fresh read of the active project's worktree list via a visible "Reload" action on the worktree management screen, SHALL automatically re-run this read every time the user navigates back to the worktree management screen for a project, and SHALL automatically re-run this read whenever the OS window regains focus while the worktree management screen is active, so that worktrees or branches deleted outside the app (e.g. via another Git client, or a branch deleted after merging a pull request) are reflected without restarting the app. Whenever the reload is triggered while the create-worktree form is in "Existing branch" mode, the system SHALL also reload the local branch list, so branches created or updated outside the app are reflected without restarting it.

#### Scenario: User manually reloads the worktree list
- **WHEN** the user activates the "Reload" action on the worktree management screen
- **THEN** the app invokes `ListWorktreesUseCase` again and updates the displayed list to match its result

#### Scenario: Manual reload also refreshes local branches in "Existing branch" mode
- **WHEN** the user activates the "Reload" action while the create-worktree form is in "Existing branch" mode
- **THEN** the app also invokes `ListLocalBranchesUseCase` again and updates the displayed branch options to match its result

#### Scenario: Manual reload does not fetch branches in "New branch" mode
- **WHEN** the user activates the "Reload" action while the create-worktree form is in "New branch" mode
- **THEN** the app does not invoke `ListLocalBranchesUseCase`

#### Scenario: Worktree screen re-entered after an external change
- **WHEN** the user navigates away from a project's worktree management screen and back to it again
- **THEN** the app invokes `ListWorktreesUseCase` again, so a worktree or branch removed externally while the user was away no longer appears in the displayed list

#### Scenario: Application window regains focus while the worktree screen is active
- **WHEN** the worktree management screen is the active screen and the application window transitions from unfocused to focused (e.g. the user switches back from another app)
- **THEN** the app invokes `ListWorktreesUseCase` again, so a worktree or branch removed externally while the app was unfocused no longer appears in the displayed list

#### Scenario: Reload is in progress
- **WHEN** a reload (manual or triggered by re-entering the screen) is in progress
- **THEN** the screen shows a loading indicator and disables the manual "Reload" action until the reload completes

#### Scenario: Reload fails
- **WHEN** `ListWorktreesUseCase` returns a failed `Result` during a reload
- **THEN** the screen shows an error message derived from the failure, consistent with the initial load's error handling

### Requirement: List local branches eligible for a new worktree
The system SHALL let the user see all local branches, using `ListLocalBranchesUseCase`, which returns every local branch paired with whether it is already checked out in a worktree (including the base repository itself). In the dropdown, branches SHALL be visually grouped by domain — the segment of the branch name before the first `/`, or "otras" for branches without a `/` — with a clickable header shown for each distinct domain, in order of first appearance. Clicking a header SHALL collapse or expand the branches under that domain, starting collapsed.

#### Scenario: Local branches loaded successfully
- **WHEN** the user switches the create-worktree dialog to "Existing branch" mode
- **THEN** the app invokes `ListLocalBranchesUseCase` and displays every returned branch, each flagged with whether it is already checked out

#### Scenario: A branch already checked out is shown but disabled
- **WHEN** a local branch is currently checked out in the base repository or in another worktree
- **THEN** the branch still appears in the dropdown, rendered disabled with a note that it already has a worktree, instead of being omitted

#### Scenario: Listing local branches fails
- **WHEN** `ListLocalBranchesUseCase` returns a failed `Result` (e.g. `WorktreeError.GitCommandFailed`)
- **THEN** the screen shows an error message derived from the `WorktreeError` instead of a branch list

#### Scenario: Branches are grouped by domain in the dropdown
- **WHEN** the "Existing branch" dropdown displays local branches such as `feature/TASK-1`, `feature/TASK-2`, `fix/TASK-3`, and `develop`
- **THEN** the dropdown shows a "feature" header followed by `feature/TASK-1` and `feature/TASK-2`, then a "fix" header followed by `fix/TASK-3`, then an "otras" header followed by `develop`, instead of a single flat alphabetical list

#### Scenario: A domain header can be collapsed and expanded
- **WHEN** the "Existing branch" dropdown is opened
- **THEN** every domain group starts collapsed, showing only its header, until the user clicks a header (e.g. "feature") to expand it
- **WHEN** the user clicks an expanded header again
- **THEN** the branches under it are hidden again, without closing the dropdown

### Requirement: Create a worktree from an existing branch
The system SHALL let the user create a new worktree for a branch that already exists locally, using `CreateWorktreeFromBranchUseCase`, instead of only being able to create worktrees for brand-new branches. The worktree's directory is derived from the branch name: if the branch starts with a known `BranchType` prefix (`feature/` or `fix/`), that prefix is stripped first; the remaining string then has every `/` replaced with `-`.

#### Scenario: Successful creation
- **WHEN** the user selects "Existing branch" mode, picks a branch not already checked out from the list, and confirms
- **THEN** the app invokes `CreateWorktreeFromBranchUseCase(branch)`, which runs `git worktree add <path> <branch>` (without creating a new branch), copies the project's secret files into the new worktree, and on success adds the returned `Worktree` to the displayed list

#### Scenario: Directory matches the new-branch flow for a known branch type prefix
- **WHEN** the requested branch starts with a known `BranchType` prefix, e.g. `feature/TASK-123`
- **THEN** the worktree directory is `<worktreesRoot>/TASK-123` (the prefix stripped), the same directory `CreateWorktreeUseCase` would have produced for that task id and branch type, instead of `<worktreesRoot>/feature-TASK-123`

#### Scenario: Creation fails because the branch doesn't exist
- **WHEN** the requested branch does not resolve to an existing local ref
- **THEN** `CreateWorktreeFromBranchUseCase` returns a failed `Result` with `WorktreeError.BranchNotFound` and no worktree is created

#### Scenario: Creation fails because the branch is already checked out elsewhere
- **WHEN** `git worktree add` fails because the requested branch is already checked out in another worktree or in the base repository
- **THEN** `CreateWorktreeFromBranchUseCase` returns a failed `Result` with `WorktreeError.BranchAlreadyCheckedOut` instead of `WorktreeError.GitCommandFailed`, and the screen shows an error message describing the conflict

#### Scenario: Creation fails because the destination directory already exists
- **WHEN** the derived branch name maps to a worktree directory that already exists
- **THEN** `CreateWorktreeFromBranchUseCase` returns a failed `Result` with `WorktreeError.WorktreeAlreadyExists` without touching Git

#### Scenario: Creation fails because Git LFS is required but not installed
- **WHEN** `git worktree add` fails and its stderr indicates the repository's `post-checkout` hook could not find `git-lfs` on the user's `PATH`
- **THEN** `CreateWorktreeFromBranchUseCase` removes the partially created worktree (`git worktree remove --force`), best-effort, and returns a failed `Result` with `WorktreeError.GitLfsNotFound` instead of `WorktreeError.GitCommandFailed`

#### Scenario: Creation fails while copying secrets
- **WHEN** copying a secret file into the new worktree fails after `git worktree add` succeeded
- **THEN** `CreateWorktreeFromBranchUseCase` removes the partially created worktree (`git worktree remove --force`), best-effort, and returns a failed `Result` with `WorktreeError.SecretCopyFailed`

