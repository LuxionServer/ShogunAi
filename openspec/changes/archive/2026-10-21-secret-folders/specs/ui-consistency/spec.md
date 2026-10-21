## MODIFIED Requirements

### Requirement: Icon-only actions expose an accessible label
Every icon-only action control (an `IconButton` with no visible text) SHALL provide a `contentDescription` describing the action it performs, in the active UI language.

#### Scenario: Edit/delete project icons have descriptions
- **WHEN** the project list screen renders the edit and delete icon actions for a project
- **THEN** each `IconButton` exposes a non-empty `contentDescription` describing that action (e.g. "Edit project", "Delete project")

#### Scenario: Remove secret file icon has a description
- **WHEN** the project configuration screen renders the remove action for a local files entry, whether it is a file or a folder
- **THEN** the `IconButton` exposes a non-empty `contentDescription` describing the action (e.g. "Remove local file or folder")
