## ADDED Requirements

### Requirement: Report a missing base repository
A command that lists a project's worktrees SHALL check that the project's base repository folder exists first and, when it doesn't, fail with exit code 1 and a message naming that folder, instead of a Git failure.

#### Scenario: Base repository folder removed
- **WHEN** a command lists the worktrees of a project whose base repository folder no longer exists
- **THEN** it prints that the base repository doesn't exist at its path and exits with code 1
