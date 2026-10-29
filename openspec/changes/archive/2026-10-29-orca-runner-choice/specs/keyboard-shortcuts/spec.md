## MODIFIED Requirements

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
