# Spec Delta

## MODIFIED Requirements

### Requirement: Reload the worktree list
The system SHALL let the user manually trigger a fresh read of the active project's worktree list via a visible "Reload" action on the worktree management screen and via View > Reload, SHALL automatically re-run this read every time the user selects the project in the sidebar, and SHALL automatically re-run this read whenever the OS window regains focus while the worktree management screen is active, so that worktrees or branches deleted outside the app (e.g. via another Git client, or a branch deleted after merging a pull request) are reflected without restarting the app. Whenever the reload is triggered while the create-worktree form is in "Existing branch" mode, the system SHALL also reload the local branch list, so branches created or updated outside the app are reflected without restarting it.

#### Scenario: User manually reloads the worktree list
- **WHEN** the user activates the "Reload" action on the worktree management screen, or View > Reload while it is shown
- **THEN** the app invokes `ListWorktreesUseCase` again and updates the displayed list to match its result

#### Scenario: Manual reload also refreshes local branches in "Existing branch" mode
- **WHEN** the user activates the "Reload" action while the create-worktree form is in "Existing branch" mode
- **THEN** the app also invokes `ListLocalBranchesUseCase` again and updates the displayed branch options to match its result

#### Scenario: Manual reload does not fetch branches in "New branch" mode
- **WHEN** the user activates the "Reload" action while the create-worktree form is in "New branch" mode
- **THEN** the app does not invoke `ListLocalBranchesUseCase`

#### Scenario: Worktree screen re-entered after an external change
- **WHEN** the user selects another view in the sidebar and then selects the project again
- **THEN** the app invokes `ListWorktreesUseCase` again, so a worktree or branch removed externally while the user was away no longer appears in the displayed list

#### Scenario: Application window regains focus while the worktree screen is active
- **WHEN** the worktree management screen is the active view and the application window transitions from unfocused to focused (e.g. the user switches back from another app)
- **THEN** the app invokes `ListWorktreesUseCase` again, so a worktree or branch removed externally while the app was unfocused no longer appears in the displayed list

#### Scenario: Reload is in progress
- **WHEN** a reload (manual or triggered by selecting the project) is in progress
- **THEN** the screen shows a loading indicator and disables the manual "Reload" action until the reload completes

#### Scenario: Reload fails
- **WHEN** `ListWorktreesUseCase` returns a failed `Result` during a reload
- **THEN** the screen shows an error message derived from the failure, consistent with the initial load's error handling
