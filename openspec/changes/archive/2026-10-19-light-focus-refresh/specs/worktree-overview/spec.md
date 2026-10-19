# Spec Delta

## MODIFIED Requirements

### Requirement: Reload the overview with the list
The system SHALL detect running agents every time the worktree list is reloaded. A manual reload SHALL compute every listed worktree's disk usage again; any other reload SHALL compute it only for worktrees whose size isn't known yet. Sizes already computed SHALL stay shown until they are computed again.

#### Scenario: Reload
- **WHEN** the user reloads the worktree list
- **THEN** agents and sizes are read again for the listed worktrees, and each size stays shown until its new value is computed

#### Scenario: Automatic reload
- **WHEN** a project's list is reloaded because its Git metadata changed and one new worktree appeared
- **THEN** agents are read again and only the new worktree's size is computed
