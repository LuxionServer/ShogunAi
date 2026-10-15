## ADDED Requirements

### Requirement: Paths follow the base repository when it moves
The system SHALL keep secret files and the worktrees root pointing to the right place when the user changes the base repository path of a project (e.g. because the repository was moved on disk).

#### Scenario: Secret files follow the base repository
- **WHEN** a project has secret files inside its base repository (stored relative, or as absolute paths inside it) and the user changes the base repository path and saves
- **THEN** the saved `ProjectConfig.secretFiles` are relative to the base repository, so they resolve inside the new base repository path

#### Scenario: Secret files outside the base repository are kept
- **WHEN** a secret file entry is an absolute path outside the base repository
- **THEN** the entry is kept unchanged when the base repository path changes

#### Scenario: Worktrees root tied to the base repository follows it
- **WHEN** the worktrees root is inside the base repository, is its parent folder, or is a sibling folder, and the user changes the base repository path without editing the worktrees root
- **THEN** the worktrees root is updated to keep the same relative location with respect to the new base repository path

#### Scenario: Independent or manually edited worktrees root is kept
- **WHEN** the worktrees root is elsewhere (e.g. another disk), or the user has edited it by hand in the form
- **THEN** changing the base repository path does not modify the worktrees root
