# Spec Delta

## MODIFIED Requirements

### Requirement: List worktrees for the active project
The system SHALL show the active project's worktrees on the worktree management view using `ListWorktreesUseCase`, one row per worktree. Each row SHALL show the branch as its main label (or that the worktree is detached), and the worktree's folder name as secondary information. The main worktree SHALL be listed first and marked distinctly.

#### Scenario: Worktrees loaded successfully
- **WHEN** the worktree management view opens for a project
- **THEN** it invokes `ListWorktreesUseCase` and displays the returned worktrees, with the main worktree first and marked distinctly

#### Scenario: Listing fails
- **WHEN** `ListWorktreesUseCase` returns a failed `Result` (e.g. `WorktreeError.GitCommandFailed`)
- **THEN** the view shows an error message derived from the `WorktreeError` instead of a worktree list

#### Scenario: Branch as the main label
- **WHEN** a worktree at `~/projects/main-repo-worktrees/TASK-123` has the branch `feature/TASK-123`
- **THEN** its row shows `feature/TASK-123` as the main label and `TASK-123` as secondary information, without the full path

### Requirement: Remove a worktree
The system SHALL let the user remove a non-main worktree, using `RemoveWorktreeUseCase`, with an option to also delete its local branch. The removal SHALL be started from the worktree's detail, its row's context menu or the delete shortcut. Starting it SHALL always show a confirmation dialog before any Git command runs, with a checkbox labeled to delete the local branch as well, unchecked by default; the branch is only passed to `RemoveWorktreeUseCase` as `branchToDelete` if the user checks it. When the removal fails because the worktree has uncommitted or untracked changes, the system SHALL update the same dialog to offer a force retry, reusing the user's branch-deletion choice, instead of leaving the user with no recourse. While `RemoveWorktreeUseCase` is running (initial attempt or force retry), the dialog SHALL show a loading indicator and disable both its confirm and cancel actions until the call completes.

#### Scenario: Confirmation dialog shown before removing
- **WHEN** the user activates "Delete worktree" for a non-main worktree from its detail, its context menu or the shortcut
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
- **THEN** the app shows an error message and keeps the worktree in the displayed list, without escalating the dialog to a force-retry state

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
- **WHEN** the user selects the main worktree or opens its row's context menu
- **THEN** neither the detail nor the context menu offers a delete action, and the delete shortcut does nothing

#### Scenario: Removal is in progress
- **WHEN** the user confirms removal (initial attempt or force retry) and `RemoveWorktreeUseCase` has not yet returned
- **THEN** the dialog shows a loading indicator and both its confirm and cancel actions are disabled until the call completes

## ADDED Requirements

### Requirement: Open the project's settings from its view
The worktree management view SHALL offer a "Project settings" action in its header that opens the project's configuration.

#### Scenario: Open settings
- **WHEN** the user activates "Project settings" in a project's worktree view
- **THEN** the content area shows that project's configuration, pre-filled with its current data
