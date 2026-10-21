## MODIFIED Requirements

### Requirement: Add or remove secret files
The system SHALL let the user add or remove entries in the project's `secretFiles` list within the configuration form, shown as "Local files": exact paths of files or folders, stored only on this machine, copied into each new worktree in addition to what the repository declares. The browse dialog SHALL allow selecting files and folders. The system SHALL NOT add an entry that is already present in the list.

#### Scenario: Add a secret file entry
- **WHEN** the user adds a file name to the secret files list and saves
- **THEN** the saved `ProjectConfig.secretFiles` includes the new entry

#### Scenario: Add a folder entry
- **WHEN** the user selects a folder inside the base repository with the browse dialog and saves
- **THEN** the saved `ProjectConfig.secretFiles` includes the folder's path relative to the base repository

#### Scenario: Remove a secret file entry
- **WHEN** the user removes a file name from the secret files list and saves
- **THEN** the saved `ProjectConfig.secretFiles` no longer includes that entry

#### Scenario: Duplicate secret file entry is ignored
- **WHEN** the user adds a file name that is already present in the secret files list
- **THEN** the list is unchanged and no duplicate entry is added

#### Scenario: Duplicate folder entry with a trailing separator is ignored
- **WHEN** the list contains `keys` and the user adds `keys/`
- **THEN** the list is unchanged and no duplicate entry is added

#### Scenario: Existing projects keep their files
- **WHEN** the app loads a project saved by an earlier version with secret files
- **THEN** those entries appear as local files and are still copied into new worktrees

### Requirement: Paths follow the base repository when it moves
The system SHALL keep secret files and folders and the worktrees root pointing to the right place when the user changes the base repository path of a project (e.g. because the repository was moved on disk).

#### Scenario: Secret files follow the base repository
- **WHEN** a project has secret files or folders inside its base repository (stored relative, or as absolute paths inside it) and the user changes the base repository path and saves
- **THEN** the saved `ProjectConfig.secretFiles` are relative to the base repository, so they resolve inside the new base repository path

#### Scenario: Secret files outside the base repository are kept
- **WHEN** a secret file or folder entry is an absolute path outside the base repository
- **THEN** the entry is kept unchanged when the base repository path changes

#### Scenario: Worktrees root tied to the base repository follows it
- **WHEN** the worktrees root is inside the base repository, is its parent folder, or is a sibling folder, and the user changes the base repository path without editing the worktrees root
- **THEN** the worktrees root is updated to keep the same relative location with respect to the new base repository path

#### Scenario: Independent or manually edited worktrees root is kept
- **WHEN** the worktrees root is elsewhere (e.g. another disk), or the user has edited it by hand in the form
- **THEN** changing the base repository path does not modify the worktrees root
