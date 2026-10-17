# Spec Delta

## MODIFIED Requirements

### Requirement: Global keyboard shortcuts for frequent actions
The system SHALL let the user trigger frequent actions via keyboard shortcuts instead of requiring a mouse click: adding a project from anywhere in the main window, and opening the "New worktree" dialog from the worktree management view.

#### Scenario: New project shortcut
- **WHEN** the main window is focused and the user presses the "add project" shortcut (Shift+Cmd/Ctrl+N)
- **THEN** the content area shows the project configuration form, empty, same as activating "Add project"

#### Scenario: New worktree shortcut
- **WHEN** the worktree management view is active and the user presses the "new worktree" shortcut (Cmd/Ctrl+N)
- **THEN** the app opens the "New worktree" dialog, same as activating "New worktree"

#### Scenario: Shortcut ignored with invalid input
- **WHEN** the "New worktree" dialog does not have valid input (e.g. blank or invalid task id) and the user presses Enter
- **THEN** no worktree creation is attempted, consistent with the "Create worktree" button being disabled
