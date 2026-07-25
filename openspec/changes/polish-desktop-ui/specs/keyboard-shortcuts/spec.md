## ADDED Requirements

### Requirement: Global keyboard shortcuts for frequent actions
The system SHALL let the user trigger frequent actions via keyboard shortcuts instead of requiring a mouse click: creating a new project from the project list screen, and creating a new worktree from the worktree management screen.

#### Scenario: New project shortcut
- **WHEN** the project list screen is active and the user presses the "new project" shortcut (Cmd/Ctrl+N)
- **THEN** the app navigates to the project configuration screen with an empty form, same as clicking "Nuevo proyecto"

#### Scenario: New worktree shortcut
- **WHEN** the worktree management screen is active, the create-worktree form has valid input, and the user presses the "new worktree" shortcut (Cmd/Ctrl+N)
- **THEN** the app creates a worktree using the current create-worktree form state, same as clicking "Crear worktree"

#### Scenario: Shortcut ignored with invalid input
- **WHEN** the worktree management screen is active, the create-worktree form does not have valid input (e.g. blank or invalid task id), and the user presses the "new worktree" shortcut
- **THEN** no worktree creation is attempted, consistent with the "Crear worktree" button being disabled
