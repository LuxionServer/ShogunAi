## MODIFIED Requirements

### Requirement: Create a project
The system SHALL let the user create a new project by entering a name, base repository path, worktrees root path, and a list of secret files, matching the fields of `ProjectConfig`. Required fields (name, base repository path) SHALL show inline validation feedback next to the field itself, not only a disabled submit button.

#### Scenario: Successful project creation
- **WHEN** the user fills in name, base repository path, and worktrees root, and submits the form
- **THEN** the app saves a new `Project` via `ProjectRepository` and navigates to the worktree management screen for it

#### Scenario: Required field missing
- **WHEN** the user submits the form without a name or without a base repository path
- **THEN** the app shows a validation error and does not save the project

#### Scenario: Required field shows inline feedback
- **WHEN** the "Nombre" or "Ruta del repositorio base" field is blank
- **THEN** the app marks that field with an error state and a supporting message explaining it is required, in addition to disabling "Guardar"

### Requirement: Add or remove secret files
The system SHALL let the user add or remove entries in the project's `secretFiles` list within the configuration form. The system SHALL NOT add a file name that is already present in the list.

#### Scenario: Add a secret file entry
- **WHEN** the user adds a file name to the secret files list and saves
- **THEN** the saved `ProjectConfig.secretFiles` includes the new entry

#### Scenario: Remove a secret file entry
- **WHEN** the user removes a file name from the secret files list and saves
- **THEN** the saved `ProjectConfig.secretFiles` no longer includes that entry

#### Scenario: Duplicate secret file entry is ignored
- **WHEN** the user adds a file name that is already present in the secret files list
- **THEN** the list is unchanged and no duplicate entry is added

## ADDED Requirements

### Requirement: Confirm before discarding unsaved changes
The system SHALL ask for confirmation before discarding unsaved edits when the user cancels out of the project configuration form, if any field differs from its initial value.

#### Scenario: Cancel with unsaved changes
- **WHEN** the user has changed at least one field and activates "Cancelar"
- **THEN** the app shows a confirmation dialog before discarding the changes and returning to the previous screen

#### Scenario: Cancel without changes
- **WHEN** the user activates "Cancelar" without having changed any field
- **THEN** the app returns to the previous screen immediately without showing a confirmation dialog
