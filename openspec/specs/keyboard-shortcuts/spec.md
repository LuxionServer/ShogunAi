# keyboard-shortcuts Specification

## Purpose
Keyboard shortcuts for frequent actions, so creating a project or a worktree does not require the mouse.

## Requirements

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

### Requirement: Worktree shortcuts respect text editing
Shortcuts that act on the selected worktree SHALL NOT fire while a text field has focus, so the platform's text editing keys keep working, and their menu items SHALL be disabled when no worktree is selected.

#### Scenario: Delete a word in the filter
- **WHEN** the filter field has focus with text in it and the user presses Cmd+Backspace on macOS or Ctrl+Backspace elsewhere
- **THEN** the text is edited as usual and the delete dialog doesn't open

#### Scenario: Nothing selected
- **WHEN** no worktree is selected
- **THEN** "Delete worktree" and the other worktree items in the menu bar are disabled

### Requirement: Open the selected worktree in Herdr with a shortcut
The system SHALL let the user open the selected worktree in the runner in use with the shortcut Cmd+E on macOS or Ctrl+E elsewhere, with the same effect as that runner's "Open in Herdr" or "Open in Orca" button, and SHALL offer it in the Worktree menu as an item named after that runner.

#### Scenario: Open the selection in Herdr
- **WHEN** Herdr is the runner in use, a worktree is selected, no dialog is open and no text field has focus, and the user presses the shortcut
- **THEN** the worktree opens in Herdr, same as activating "Open in Herdr"

#### Scenario: Open the selection in Orca
- **WHEN** Orca is the runner in use and a worktree is selected
- **THEN** the Worktree menu shows "Open in Orca", and the shortcut opens the worktree in Orca, same as activating "Open in Orca"

#### Scenario: Nothing selected
- **WHEN** no worktree is selected
- **THEN** the runner's item in the Worktree menu is disabled and the shortcut does nothing

#### Scenario: Text field has focus
- **WHEN** a text field has focus or a dialog is open and the user presses the shortcut
- **THEN** the worktree isn't opened in any runner
