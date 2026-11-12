## MODIFIED Requirements

### Requirement: Add or remove secret files
The system SHALL let the user add or remove entries in the project's `secretFiles` list within the configuration form, shown as "Local files": exact paths of files or folders, or patterns, stored only on this machine, copied or linked into each new worktree in addition to what the repository declares. Entries SHALL be typed in the field or chosen with the in-app local file picker, which offers the base repository's untracked and ignored files and folders.

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

### Requirement: Show the repository's provisioning
The project configuration screen SHALL show, read-only, the patterns the base repository declares in `.shogunai.json` (`copy` and `link`) and in `.worktreeinclude`, each with the file it comes from and `link` patterns marked as linked, and SHALL refresh them when the base repository path changes. When `.shogunai.json` can't be read, the screen SHALL say so instead of listing its patterns.

#### Scenario: Repository with both files
- **WHEN** the user opens the configuration of a project whose repository has `.shogunai.json` with `local.properties` and `.worktreeinclude` with `.env`
- **THEN** the screen lists `local.properties` from `.shogunai.json` and `.env` from `.worktreeinclude`

#### Scenario: Linked patterns
- **WHEN** the repository's `.shogunai.json` declares `node_modules` under `link`
- **THEN** the screen lists `node_modules` from `.shogunai.json`, marked as linked

#### Scenario: Only post-creation commands
- **WHEN** the repository's `.shogunai.json` declares two post-creation commands and no patterns
- **THEN** the screen says two commands run after creation and points to the repository configuration section, and doesn't say the repository declares nothing

#### Scenario: Repository without provisioning files
- **WHEN** the base repository has neither file
- **THEN** the screen says the repository declares nothing to copy

#### Scenario: Base repository path changes
- **WHEN** the user changes the base repository path in the form to another repository
- **THEN** the listed patterns are those of the new repository

#### Scenario: Unreadable repository file
- **WHEN** the repository's `.shogunai.json` is malformed
- **THEN** the screen shows that the file can't be read

## ADDED Requirements

### Requirement: No duplicate local file entries
The system SHALL NOT add an entry to the project's local files that is already present in the list.

#### Scenario: Same file name twice
- **WHEN** the user adds a file name that is already present in the secret files list
- **THEN** the list is unchanged and no duplicate entry is added

#### Scenario: Folder with a trailing separator
- **WHEN** the list contains `keys` and the user adds `keys/`
- **THEN** the list is unchanged and no duplicate entry is added

### Requirement: Show the repository's post-creation commands
When the base repository declares post-creation commands, the project configuration screen SHALL say how many there are and where they're listed.

#### Scenario: Two commands declared
- **WHEN** the repository's `.shogunai.json` declares two post-creation commands and no patterns
- **THEN** the screen says two commands run after creation and points to the repository configuration section, and doesn't say the repository declares nothing
