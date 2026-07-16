# project-catalog Specification

## Purpose
TBD - created by archiving change project-worktree-ui. Update Purpose after archive.
## Requirements
### Requirement: Project persistence
The system SHALL persist known projects (id, name, `ProjectConfig`) to a local JSON file so they are available across app restarts.

#### Scenario: Projects survive a restart
- **WHEN** the user has previously created one or more projects and relaunches the app
- **THEN** the app loads the previously saved projects from local storage and shows them in the project list

#### Scenario: No projects saved yet
- **WHEN** the app launches and no project file exists yet
- **THEN** the app starts with an empty project list instead of failing

### Requirement: Project list on launch
The system SHALL show a project selector/list screen as the first screen on launch, listing all persisted projects by name.

#### Scenario: User selects an existing project
- **WHEN** the user picks a project from the list
- **THEN** the app navigates to the worktree management screen for that project

#### Scenario: User starts creating a new project
- **WHEN** the user chooses to create a new project from the list screen
- **THEN** the app navigates to the project configuration screen with an empty form

### Requirement: Edit a project from the list
The system SHALL let the user open the configuration screen for an existing project directly from the project list, pre-filled with that project's current data.

#### Scenario: User edits a project from the list
- **WHEN** the user activates the edit action on a project in the list
- **THEN** the app navigates to the project configuration screen with that project's `ProjectConfig` pre-loaded for editing

### Requirement: Delete a project from the list
The system SHALL let the user delete an existing project from the project list, after explicit confirmation, removing it from persisted storage and from the visible list.

#### Scenario: User confirms deletion
- **WHEN** the user activates the delete action on a project in the list and confirms the deletion in the confirmation dialog
- **THEN** the app removes the project via `ProjectRepository` and the project no longer appears in the list

#### Scenario: User cancels deletion
- **WHEN** the user activates the delete action on a project in the list and dismisses or cancels the confirmation dialog
- **THEN** the project is not deleted and remains in the list

### Requirement: Reload the project list
The system SHALL let the user manually trigger a fresh read of the persisted project catalog via a visible "Reload" action on the project list screen, and SHALL automatically re-run this read whenever the OS window regains focus while the project list screen is active, so that projects added, edited, or removed outside the current app session are reflected without restarting the app.

#### Scenario: User manually reloads the project list
- **WHEN** the user activates the "Reload" action on the project list screen
- **THEN** the app re-reads the project catalog via `ProjectRepository.list()` and updates the displayed list to match its result

#### Scenario: Application window regains focus while the project list screen is active
- **WHEN** the project list screen is the active screen and the application window transitions from unfocused to focused (e.g. the user switches back from another app)
- **THEN** the app re-reads the project catalog via `ProjectRepository.list()` and updates the displayed list to match its result

