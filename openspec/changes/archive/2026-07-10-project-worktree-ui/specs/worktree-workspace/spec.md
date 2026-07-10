## ADDED Requirements

### Requirement: List worktrees for the active project
The system SHALL show the active project's worktrees on the worktree management screen using `ListWorktreesUseCase`, including path, branch, and whether each is the main worktree.

#### Scenario: Worktrees loaded successfully
- **WHEN** the worktree management screen opens for a project
- **THEN** it invokes `ListWorktreesUseCase` and displays the returned worktrees, marking the main worktree distinctly

#### Scenario: Listing fails
- **WHEN** `ListWorktreesUseCase` returns a failed `Result` (e.g. `WorktreeError.GitCommandFailed`)
- **THEN** the screen shows an error message derived from the `WorktreeError` instead of a worktree list

### Requirement: Create a worktree for a task
The system SHALL let the user create a new worktree by entering a task id and choosing a branch type (`feature` or `fix`), using `CreateWorktreeUseCase`.

#### Scenario: Successful creation
- **WHEN** the user enters a non-empty task id, selects a branch type, and confirms
- **THEN** the app invokes `CreateWorktreeUseCase(taskId, branchType)` and, on success, adds the returned `Worktree` to the displayed list

#### Scenario: Creation fails with a domain error
- **WHEN** `CreateWorktreeUseCase` returns a failed `Result` (e.g. `WorktreeError.WorktreeAlreadyExists`, `WorktreeError.SecretFileNotFound`, `WorktreeError.BaseRepositoryNotFound`)
- **THEN** the screen shows an error message describing the failure and does not add a worktree to the list

### Requirement: Remove a worktree
The system SHALL let the user remove a non-main worktree from the list, using `RemoveWorktreeUseCase`, with an option to also delete its local branch.

#### Scenario: Successful removal
- **WHEN** the user chooses to remove a worktree from the list and confirms
- **THEN** the app invokes `RemoveWorktreeUseCase(worktreePath, branchToDelete, force)` and, on success, removes it from the displayed list

#### Scenario: Removal fails
- **WHEN** `RemoveWorktreeUseCase` returns a failed `Result`
- **THEN** the screen shows an error message and keeps the worktree in the displayed list

#### Scenario: Main worktree cannot be removed
- **WHEN** the user views the main worktree entry in the list
- **THEN** the screen does not offer a remove action for it
