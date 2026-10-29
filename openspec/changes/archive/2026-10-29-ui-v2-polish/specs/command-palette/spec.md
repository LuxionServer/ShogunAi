## MODIFIED Requirements

### Requirement: Open the command palette
Cmd/Ctrl+K and View > Command palette SHALL open the command palette over the main window with its search field focused, from any view. Escape, or clicking outside it, SHALL close it without running anything, on every platform, including macOS, where the window only receives Escape's key release.

#### Scenario: Open and close
- **WHEN** the user presses Cmd/Ctrl+K in any view and then Escape
- **THEN** the palette opens with the search field focused, then closes, and the view is unchanged

#### Scenario: Escape released only
- **WHEN** the palette is open and only Escape's key release reaches the app
- **THEN** the palette closes, and the view's selection and text filter are unchanged

### Requirement: Results show their context
Each result SHALL show its kind (view, setting, project, worktree or action), its context when it has one (for a worktree, its project and state indicator), and the keyboard shortcut of the action when it has one. Each kind SHALL have its own icon.

#### Scenario: Worktree result
- **WHEN** a worktree result is listed
- **THEN** it shows the branch, its project's name and its state indicator, marked as a worktree

#### Scenario: Preferences result
- **WHEN** the user types `pref`
- **THEN** "Preferences" is listed marked as a setting, with the settings icon and its shortcut, not as a view
