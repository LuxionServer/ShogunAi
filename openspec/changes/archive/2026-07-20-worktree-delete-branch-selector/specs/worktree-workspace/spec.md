## MODIFIED Requirements

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
