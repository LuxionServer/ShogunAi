## ADDED Requirements

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
