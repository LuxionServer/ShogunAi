## ADDED Requirements

### Requirement: Reload the worktree list
The system SHALL let the user manually trigger a fresh read of the active project's worktree list via a visible "Reload" action on the worktree management screen, SHALL automatically re-run this read every time the user navigates back to the worktree management screen for a project, and SHALL automatically re-run this read whenever the OS window regains focus while the worktree management screen is active, so that worktrees or branches deleted outside the app (e.g. via another Git client, or a branch deleted after merging a pull request) are reflected without restarting the app.

#### Scenario: User manually reloads the worktree list
- **WHEN** the user activates the "Reload" action on the worktree management screen
- **THEN** the app invokes `ListWorktreesUseCase` again and updates the displayed list to match its result

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
