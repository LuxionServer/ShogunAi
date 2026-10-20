# Spec Delta

## ADDED Requirements

### Requirement: Branch deletion is a separate step of removal
When the user chose to delete the local branch, the system SHALL delete it after the worktree is removed, as a step with its own result. The branch SHALL be deleted only if it is contained in the project's main branch at that moment; otherwise it SHALL be kept. A kept branch SHALL NOT make the removal fail.

#### Scenario: Merged branch
- **WHEN** the user removes a worktree choosing to delete its branch and the branch is contained in the main branch
- **THEN** the worktree and the branch are deleted and the dialog closes

#### Scenario: Squash or rebase merged branch
- **WHEN** the user removes a worktree choosing to delete its branch and the branch isn't contained in the main branch, for example because its pull request was squash merged
- **THEN** the worktree is removed and disappears from the list, the branch is kept, and the app says the worktree was removed and the branch was kept because it isn't merged into the main branch

#### Scenario: Branch deletion fails
- **WHEN** the worktree is removed and Git then refuses to delete the merged branch
- **THEN** the worktree is reported as removed and the branch as kept, with Git's reason

### Requirement: Force-delete a kept branch
After a removal keeps an unmerged branch, the system SHALL offer to delete it anyway. The offer SHALL name the branch, warn that its commits may be lost, and run nothing until the user confirms.

#### Scenario: Delete the kept branch
- **WHEN** the user confirms deleting the kept branch
- **THEN** the branch is deleted even though it isn't merged, and the app reports it as deleted

#### Scenario: Leave the kept branch
- **WHEN** the user dismisses the notice
- **THEN** the branch stays

## MODIFIED Requirements

### Requirement: Remove a worktree
The system SHALL let the user remove a non-main worktree, using `RemoveWorktreeUseCase`, with an option to also delete its local branch. The removal SHALL be started from the worktree's detail, its row's context menu or the delete shortcut. Starting it SHALL always show a confirmation dialog before any Git command runs, with a checkbox labeled to delete the local branch as well, unchecked by default; the branch is only deleted if the user checks it, as a separate step after the worktree is removed. When the removal fails because the worktree has uncommitted or untracked changes, the system SHALL update the same dialog to offer a force retry, reusing the user's branch-deletion choice, instead of leaving the user with no recourse. While `RemoveWorktreeUseCase` is running (initial attempt or force retry), the dialog SHALL show a loading indicator and disable both its confirm and cancel actions until the call completes.

#### Scenario: Confirmation dialog shown before removing
- **WHEN** the user activates "Delete worktree" for a non-main worktree from its detail, its context menu or the shortcut
- **THEN** the app shows a confirmation dialog with a "delete local branch" checkbox unchecked by default, and does not invoke `RemoveWorktreeUseCase` until the user confirms

#### Scenario: Successful removal without deleting the branch
- **WHEN** the user confirms the removal dialog with the "delete local branch" checkbox unchecked
- **THEN** the app invokes `RemoveWorktreeUseCase(worktreePath, force = false)` without a branch to delete and, on success, removes the worktree from the displayed list and dismisses the dialog

#### Scenario: Successful removal deleting the branch
- **WHEN** the user checks "delete local branch" in the confirmation dialog and confirms
- **THEN** the app invokes `RemoveWorktreeUseCase(worktreePath, branchToDelete = worktree.branch, force = false)` and, on success, removes the worktree from the displayed list and, unless the branch was kept, dismisses the dialog

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
