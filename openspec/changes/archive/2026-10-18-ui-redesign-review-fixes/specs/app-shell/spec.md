# Spec Delta

## ADDED Requirements

### Requirement: Focus returns to the main window
When a dialog or secondary window (Preferences, a confirmation, "New worktree") closes, the main window SHALL regain focus, so the first click acts on the clicked element and the menu bar keeps all its menus.

#### Scenario: Close Preferences
- **WHEN** the user closes Preferences with Cmd/Ctrl+W
- **THEN** the main window is focused, the menu bar shows its menus and the first click is not swallowed

#### Scenario: Cancel a dialog
- **WHEN** the user closes "New worktree" with Escape or answers "Keep editing" to a discard confirmation
- **THEN** the main window is focused and the menu bar shows its menus
