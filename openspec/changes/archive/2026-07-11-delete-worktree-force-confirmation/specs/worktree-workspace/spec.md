## MODIFIED Requirements

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
