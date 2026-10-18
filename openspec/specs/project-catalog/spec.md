# project-catalog Specification

## Purpose
Keeps the list of configured projects across restarts and lets the user open, edit, delete and reload them.

## Requirements

### Requirement: Project persistence
The system SHALL persist known projects (id, name, `ProjectConfig`) to a local JSON file so they are available across app restarts.

#### Scenario: Projects survive a restart
- **WHEN** the user has previously created one or more projects and relaunches the app
- **THEN** the app loads the previously saved projects from local storage and shows them in the sidebar

#### Scenario: No projects saved yet
- **WHEN** the app launches and no project file exists yet
- **THEN** the app starts with no projects in the sidebar and shows the first-launch empty state instead of failing

### Requirement: Reload the project list
The system SHALL let the user manually trigger a fresh read of the persisted project catalog with the View > Reload action, and SHALL automatically re-run this read whenever the OS window regains focus, so that projects added, edited, or removed outside the current app session are reflected in the sidebar without restarting the app.

#### Scenario: User manually reloads the project list
- **WHEN** the user activates View > Reload
- **THEN** the app re-reads the project catalog via `ProjectRepository.list()` and updates the sidebar to match its result

#### Scenario: Application window regains focus while the project list screen is active
- **WHEN** the application window transitions from unfocused to focused (e.g. the user switches back from another app)
- **THEN** the app re-reads the project catalog via `ProjectRepository.list()` and updates the sidebar to match its result

### Requirement: Open a project from the sidebar
The system SHALL show a project's worktrees in the content area when the user selects the project in the sidebar.

#### Scenario: User selects a project
- **WHEN** the user selects a project in the sidebar
- **THEN** the content area shows the worktree management view for that project

### Requirement: Add a project from the sidebar
The system SHALL let the user start creating a project from the sidebar's "Add project" action, from File > Add project, or from the first-launch empty state.

#### Scenario: User starts adding a project
- **WHEN** the user activates "Add project"
- **THEN** the content area shows the project configuration form, empty

### Requirement: Edit a project from the sidebar
The system SHALL let the user open the configuration of an existing project from the project's context menu in the sidebar, pre-filled with that project's current data.

#### Scenario: User edits a project
- **WHEN** the user opens a project's context menu in the sidebar and chooses "Edit project"
- **THEN** the content area shows the project configuration form with that project's `ProjectConfig` pre-loaded for editing

### Requirement: Remove a project from the sidebar
The system SHALL let the user remove a project from the project's context menu in the sidebar, after explicit confirmation, removing it from persisted storage and from the sidebar. Removing a project SHALL NOT touch its repository or worktrees on disk.

#### Scenario: User confirms removal
- **WHEN** the user chooses "Remove project" in a project's context menu and confirms the dialog
- **THEN** the app removes the project via `ProjectRepository`, it no longer appears in the sidebar, and its repository and worktrees stay on disk

#### Scenario: User cancels removal
- **WHEN** the user chooses "Remove project" and cancels the dialog
- **THEN** the project is not removed and remains in the sidebar

#### Scenario: Removing the selected project
- **WHEN** the user removes the project that is currently selected
- **THEN** the selection moves to the first remaining project, or to the empty state when none remain
