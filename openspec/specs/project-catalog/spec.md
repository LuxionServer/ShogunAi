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

