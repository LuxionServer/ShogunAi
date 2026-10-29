# Spec Delta

## MODIFIED Requirements

### Requirement: Add or remove secret files
The system SHALL let the user add or remove entries in the project's `secretFiles` list within the configuration form, shown as "Local files": exact paths of files or folders, or patterns, stored only on this machine, copied or linked into each new worktree in addition to what the repository declares. Entries SHALL be typed in the field or chosen with the in-app local file picker, which offers the base repository's untracked and ignored files and folders. The system SHALL NOT add an entry that is already present in the list.

#### Scenario: Add a secret file entry
- **WHEN** the user adds a file name to the secret files list and saves
- **THEN** the saved `ProjectConfig.secretFiles` includes the new entry

#### Scenario: Add a folder entry
- **WHEN** the user checks an ignored folder of the base repository in the local file picker, applies it and saves
- **THEN** the saved `ProjectConfig.secretFiles` includes the folder's path relative to the base repository

#### Scenario: Add a pattern entry
- **WHEN** the user types `*.jks` in the local files field and adds it
- **THEN** the list shows `*.jks` marked as a pattern, and it's saved as an entry

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
- **THEN** those entries appear as local files in copy mode and are still copied into new worktrees

### Requirement: Pick hidden files as local files
The local file picker SHALL list hidden files and folders, such as those whose name starts with a dot, so they can be chosen like any other entry.

#### Scenario: Pick a dotfile
- **WHEN** the user opens the local file picker and the base repository ignores `.env`
- **THEN** `.env` is listed and can be selected
