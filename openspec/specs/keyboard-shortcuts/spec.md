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

### Requirement: Open the selected worktree with a shortcut
The system SHALL let the user open the selected worktree in the default runner with Cmd+O on macOS or Ctrl+O elsewhere, offered as "Open in <default runner>" in the Worktree menu, and open its "Open with…" menu with Cmd/Ctrl+Shift+O, offered as "Open with…" in the Worktree menu. Cmd/Ctrl+E SHALL act as Cmd/Ctrl+O for one release without being shown in the menu. None of them SHALL act while a text field has focus or a dialog is open.

#### Scenario: Open the selection in the default runner
- **WHEN** a worktree is selected, the default runner is Orca, no dialog is open and no text field has focus, and the user presses Cmd/Ctrl+O
- **THEN** the worktree opens in Orca, same as activating "Open in Orca"

#### Scenario: Former shortcut
- **WHEN** a worktree is selected and the user presses Cmd/Ctrl+E
- **THEN** it opens in the default runner, same as Cmd/Ctrl+O

#### Scenario: Open the selection in Orca
- **WHEN** Orca is the runner in use and a worktree is selected
- **THEN** the Worktree menu shows "Open in Orca", and the shortcut opens the worktree in Orca, same as activating "Open in Orca"

#### Scenario: Nothing selected
- **WHEN** no worktree is selected
- **THEN** "Open in <default runner>" and "Open with…" are disabled in the Worktree menu and the shortcuts do nothing

#### Scenario: Text field has focus
- **WHEN** a text field has focus or a dialog is open and the user presses Cmd/Ctrl+O
- **THEN** the worktree isn't opened

### Requirement: Close the Preferences window with the keyboard
Cmd/Ctrl+W SHALL close the Preferences window while it has focus, as its close button does, without affecting the main window.

#### Scenario: Close Preferences
- **WHEN** the Preferences window has focus and the user presses Cmd/Ctrl+W
- **THEN** the Preferences window closes and the main window stays open

#### Scenario: Other keys
- **WHEN** the Preferences window has focus and the user presses W without the command key
- **THEN** the window stays open
