# Spec Delta

## ADDED Requirements

### Requirement: A created worktree appears once
When a creation finishes while the project's list is being refreshed, or after a refresh already listed the new worktree, the list SHALL show the worktree exactly once.

#### Scenario: Window focused during a slow creation
- **WHEN** a creation with a slow post-creation command is running, the user switches to another app and back, and the creation then finishes
- **THEN** the new worktree appears once and the app keeps working

#### Scenario: Reload during creation
- **WHEN** the user presses Cmd/Ctrl+R while a worktree is being created
- **THEN** the new worktree appears once when the creation finishes

### Requirement: Removal errors are shown in the removal dialog
When removing a worktree fails, the removal dialog SHALL stay open and show the error with its technical details. While the removal runs, the dialog SHALL NOT be dismissed by clicking outside it or pressing Escape. When the removed worktree was selected, the selection SHALL be cleared.

#### Scenario: Removal fails
- **WHEN** removing a worktree fails because a file in it is locked
- **THEN** the dialog shows the error, and the user can retry or close it

#### Scenario: Click outside during removal
- **WHEN** the user clicks outside the dialog while the removal runs
- **THEN** the dialog stays open until the removal ends

#### Scenario: Selected worktree removed
- **WHEN** the selected worktree is removed
- **THEN** no worktree is selected and the detail shows the project summary

### Requirement: Branch loading errors are shown in the create dialog
When loading the local branches for "From existing branch" fails, the create dialog SHALL show the error in the dialog with a way to retry, instead of saying there are no branches.

#### Scenario: Git fails while listing branches
- **WHEN** the dialog opens in existing-branch mode and listing branches fails
- **THEN** the dialog shows the error and a retry action, and doesn't show "No branches available"
