# project-configuration Specification

## Purpose
TBD - created by archiving change project-worktree-ui. Update Purpose after archive.
## Requirements
### Requirement: Create a project
The system SHALL let the user create a new project by entering a name, base repository path, worktrees root path, and a list of secret files, matching the fields of `ProjectConfig`.

#### Scenario: Successful project creation
- **WHEN** the user fills in name, base repository path, and worktrees root, and submits the form
- **THEN** the app saves a new `Project` via `ProjectRepository` and navigates to the worktree management screen for it

#### Scenario: Required field missing
- **WHEN** the user submits the form without a name or without a base repository path
- **THEN** the app shows a validation error and does not save the project

### Requirement: Edit an existing project
The system SHALL let the user edit an existing project's configuration from the project configuration screen.

#### Scenario: Successful edit
- **WHEN** the user opens an existing project's configuration, changes a field, and submits
- **THEN** the app persists the updated `ProjectConfig` for that project's id and navigates back to the worktree management screen

### Requirement: Add or remove secret files
The system SHALL let the user add or remove entries in the project's `secretFiles` list within the configuration form.

#### Scenario: Add a secret file entry
- **WHEN** the user adds a file name to the secret files list and saves
- **THEN** the saved `ProjectConfig.secretFiles` includes the new entry

#### Scenario: Remove a secret file entry
- **WHEN** the user removes a file name from the secret files list and saves
- **THEN** the saved `ProjectConfig.secretFiles` no longer includes that entry

