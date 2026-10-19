# Spec Delta

## ADDED Requirements

### Requirement: Worktree shortcuts respect text editing
Shortcuts that act on the selected worktree SHALL NOT fire while a text field has focus, so the platform's text editing keys keep working, and their menu items SHALL be disabled when no worktree is selected.

#### Scenario: Delete a word in the filter
- **WHEN** the filter field has focus with text in it and the user presses Cmd+Backspace on macOS or Ctrl+Backspace elsewhere
- **THEN** the text is edited as usual and the delete dialog doesn't open

#### Scenario: Nothing selected
- **WHEN** no worktree is selected
- **THEN** "Delete worktree" and the other worktree items in the menu bar are disabled
