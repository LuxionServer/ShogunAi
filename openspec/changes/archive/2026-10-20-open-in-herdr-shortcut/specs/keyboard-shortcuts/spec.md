## ADDED Requirements

### Requirement: Open the selected worktree in Herdr with a shortcut
The system SHALL let the user open the selected worktree in Herdr with the shortcut Cmd+E on macOS or Ctrl+E elsewhere, with the same effect as the "Open in Herdr" button, and SHALL offer it as an "Open in Herdr" item in the Worktree menu.

#### Scenario: Open the selection in Herdr
- **WHEN** a worktree is selected, no dialog is open and no text field has focus, and the user presses the shortcut
- **THEN** the worktree opens in Herdr, same as activating "Open in Herdr"

#### Scenario: Nothing selected
- **WHEN** no worktree is selected
- **THEN** the "Open in Herdr" item in the Worktree menu is disabled and the shortcut does nothing

#### Scenario: Text field has focus
- **WHEN** a text field has focus or a dialog is open and the user presses the shortcut
- **THEN** the worktree isn't opened in Herdr
