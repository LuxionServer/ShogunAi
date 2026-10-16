# Spec Delta

## MODIFIED Requirements

### Requirement: Add or remove secret files
The system SHALL let the user add or remove entries in the project's `secretFiles` list within the configuration form, shown as "Local files": exact paths, stored only on this machine, copied into each new worktree in addition to what the repository declares. The system SHALL NOT add a file name that is already present in the list.

#### Scenario: Add a secret file entry
- **WHEN** the user adds a file name to the secret files list and saves
- **THEN** the saved `ProjectConfig.secretFiles` includes the new entry

#### Scenario: Remove a secret file entry
- **WHEN** the user removes a file name from the secret files list and saves
- **THEN** the saved `ProjectConfig.secretFiles` no longer includes that entry

#### Scenario: Duplicate secret file entry is ignored
- **WHEN** the user adds a file name that is already present in the secret files list
- **THEN** the list is unchanged and no duplicate entry is added

#### Scenario: Existing projects keep their files
- **WHEN** the app loads a project saved by an earlier version with secret files
- **THEN** those entries appear as local files and are still copied into new worktrees

## ADDED Requirements

### Requirement: Show the repository's provisioning
The project configuration screen SHALL show, read-only, the patterns the base repository declares in `.shogunai.json` and in `.worktreeinclude`, each with the file it comes from, and SHALL refresh them when the base repository path changes. When `.shogunai.json` can't be read, the screen SHALL say so instead of listing its patterns.

#### Scenario: Repository with both files
- **WHEN** the user opens the configuration of a project whose repository has `.shogunai.json` with `local.properties` and `.worktreeinclude` with `.env`
- **THEN** the screen lists `local.properties` from `.shogunai.json` and `.env` from `.worktreeinclude`

#### Scenario: Repository without provisioning files
- **WHEN** the base repository has neither file
- **THEN** the screen says the repository declares nothing to copy

#### Scenario: Base repository path changes
- **WHEN** the user changes the base repository path in the form to another repository
- **THEN** the listed patterns are those of the new repository

#### Scenario: Unreadable repository file
- **WHEN** the repository's `.shogunai.json` is malformed
- **THEN** the screen shows that the file can't be read

### Requirement: Move local files into the repository
The project configuration screen SHALL offer to move the local files that are inside the base repository into `.shogunai.json`, as patterns anchored to the repository root. The move SHALL take effect when the user saves; the repository file SHALL NOT be written otherwise.

#### Scenario: Move relative entries
- **WHEN** a project has the local files `local.properties`, `app/google-services.json` and `/Users/me/keys/release.jks` (outside the repository), the user activates "Move to .shogunai.json" and saves
- **THEN** `.shogunai.json` contains the patterns `/local.properties` and `/app/google-services.json` in `copy`, and the project's local files only contain `/Users/me/keys/release.jks`

#### Scenario: Existing repository file is extended
- **WHEN** `.shogunai.json` already exists with patterns or other fields and the user moves local files into it
- **THEN** the new patterns are appended without duplicates and the existing patterns and fields are kept

#### Scenario: Cancel after moving
- **WHEN** the user activates "Move to .shogunai.json" and then cancels the form
- **THEN** the app asks for confirmation as with any unsaved change, and if confirmed neither `.shogunai.json` nor the project change

#### Scenario: Repository file can't be written
- **WHEN** the user saves after moving local files and writing `.shogunai.json` fails
- **THEN** the app shows an error, keeps the form open, and the project's local files are not changed

#### Scenario: Unreadable repository file blocks the move
- **WHEN** `.shogunai.json` exists but is malformed
- **THEN** "Move to .shogunai.json" is not available, so the file is never overwritten

#### Scenario: Nothing to move
- **WHEN** the project has no local files inside the base repository
- **THEN** "Move to .shogunai.json" is not available
