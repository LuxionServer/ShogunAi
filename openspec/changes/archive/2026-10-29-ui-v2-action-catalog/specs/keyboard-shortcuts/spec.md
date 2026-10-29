## REMOVED Requirements

### Requirement: Open the selected worktree in Herdr with a shortcut
**Reason**: The shortcut is no longer tied to one runner; it follows the default runner.
**Migration**: Use Cmd/Ctrl+O, which opens the selection in the default runner (Herdr unless changed in Preferences). Cmd/Ctrl+E keeps working as an alias for one release.

## ADDED Requirements

### Requirement: Open the selected worktree with a shortcut
The system SHALL let the user open the selected worktree in the default runner with Cmd+O on macOS or Ctrl+O elsewhere, offered as "Open in <default runner>" in the Worktree menu, and open its "Open with…" menu with Cmd/Ctrl+Shift+O, offered as "Open with…" in the Worktree menu. Cmd/Ctrl+E SHALL act as Cmd/Ctrl+O for one release without being shown in the menu. None of them SHALL act while a text field has focus or a dialog is open.

#### Scenario: Open the selection in the default runner
- **WHEN** a worktree is selected, the default runner is Orca, no dialog is open and no text field has focus, and the user presses Cmd/Ctrl+O
- **THEN** the worktree opens in Orca, same as activating "Open in Orca"

#### Scenario: Former shortcut
- **WHEN** a worktree is selected and the user presses Cmd/Ctrl+E
- **THEN** it opens in the default runner, same as Cmd/Ctrl+O

#### Scenario: Nothing selected
- **WHEN** no worktree is selected
- **THEN** "Open in <default runner>" and "Open with…" are disabled in the Worktree menu and the shortcuts do nothing

#### Scenario: Text field has focus
- **WHEN** a text field has focus or a dialog is open and the user presses Cmd/Ctrl+O
- **THEN** the worktree isn't opened
