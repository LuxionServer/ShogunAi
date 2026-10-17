# Spec Delta

## ADDED Requirements

### Requirement: Configure the project's main branch
The system SHALL let the user set an optional main branch on the project configuration screen, used to compare and mark as merged the project's worktrees. Leaving it empty means it is detected from the repository. Surrounding whitespace is trimmed and an empty value is stored as not set.

#### Scenario: Default is detected
- **WHEN** the user creates a new project without filling the main branch field
- **THEN** the saved project has no main branch set, so it is detected from the repository

#### Scenario: Set a main branch
- **WHEN** the user enters `develop` in the main branch field and saves
- **THEN** the saved project's main branch is `develop`

#### Scenario: Clear the main branch
- **WHEN** the user empties the main branch field of a project that had one, or leaves only whitespace, and saves
- **THEN** the saved project has no main branch set

#### Scenario: Existing projects without a main branch
- **WHEN** the app loads a project stored before this field existed
- **THEN** the project loads normally with no main branch set

#### Scenario: Changing the main branch counts as an unsaved change
- **WHEN** the user edits the main branch field and then activates "Cancel"
- **THEN** the app asks for confirmation before discarding the change
