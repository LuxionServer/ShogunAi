# Spec Delta

## ADDED Requirements

### Requirement: Read statuses without writing to the repository
The system SHALL read worktree statuses without writing to the repository, so reading them never modifies the index or takes Git's optional locks.

#### Scenario: Status read
- **WHEN** the status of a worktree is read
- **THEN** the uncommitted changes are read with Git's optional locks disabled, and the worktree's index file is left untouched
