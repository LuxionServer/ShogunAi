# Spec Delta

## MODIFIED Requirements

### Requirement: Watch only while it is useful
The system SHALL only check while the project's worktree view is shown, SHALL stop when the user selects another view, and SHALL skip a check while the list is loading or a create, remove or clean up action is running.

#### Scenario: Leaving the screen
- **WHEN** the user selects another project or view in the sidebar
- **THEN** no more checks run for the project that was shown

#### Scenario: Action in progress
- **WHEN** a check is due while a worktree is being created, removed or cleaned up
- **THEN** that check is skipped and the next one runs normally
