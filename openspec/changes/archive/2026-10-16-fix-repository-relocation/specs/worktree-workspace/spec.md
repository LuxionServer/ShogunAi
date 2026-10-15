## ADDED Requirements

### Requirement: Repair worktrees after moving the repository
The system SHALL re-link the base repository and its worktrees with `git worktree repair`, via `RepairWorktreesUseCase`, after the user saves an existing project with a different base repository path or worktrees root. The worktree management screen SHALL always use the project's current configuration.

#### Scenario: Repair after moving the repository
- **WHEN** the user saves an existing project whose base repository path or worktrees root changed
- **THEN** the worktree management screen runs `git worktree repair` from the new base repository before listing worktrees

#### Scenario: Worktrees moved along with the worktrees root
- **WHEN** a worktree reported by `git worktree list` no longer exists at its old path but exists at the same relative location under the new worktrees root
- **THEN** its new path is passed to `git worktree repair` so Git updates the link in both directions

#### Scenario: Repair fails
- **WHEN** `RepairWorktreesUseCase` returns a failed `Result` (e.g. `WorktreeError.BaseRepositoryNotFound` or `WorktreeError.GitCommandFailed`)
- **THEN** the screen shows the error message and still lists the worktrees

#### Scenario: Screen uses the edited configuration
- **WHEN** the user edits a project's configuration and saves
- **THEN** the worktree management screen uses use cases built from the saved configuration, not a cached view model from before the edit
