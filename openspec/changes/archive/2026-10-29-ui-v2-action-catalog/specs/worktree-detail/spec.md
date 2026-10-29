## MODIFIED Requirements

### Requirement: Detail actions
The detail pane SHALL present the worktree's actions in an action bar: "Open in <default runner>" as its primary action, a split button whose second part opens the "Open with…" menu; then the icon actions "Copy launch command", "Open in <terminal>" and "Open folder" when available; then a "More" (⋯) menu with "Copy path" and, for every worktree except the main one, "Delete worktree…" last, after a separator, in danger text. The primary action SHALL be disabled with a loading indicator while that worktree is being opened in the default runner. The "Open with…" menu SHALL list every available way to open the worktree, the default runner first and marked as the default, then "Copy launch command", "Set default runner…" and, when the project has several agents, a "With agent" section with the project's agents and the worktree's one checked.

#### Scenario: Main worktree
- **WHEN** the main worktree is selected
- **THEN** the detail offers "Open in <default runner>", the icon actions and "Copy path", and no delete action

#### Scenario: Opening in Herdr
- **WHEN** the user activates "Open in Herdr" in the detail and Herdr hasn't answered yet
- **THEN** the primary action shows a loading indicator and is disabled until the opening finishes

#### Scenario: Open with another tool
- **WHEN** the default runner is Herdr, Orca and IntelliJ IDEA are installed, and the user opens "Open with…"
- **THEN** the menu lists "Open in Herdr" marked as default with ⌘O, "Open in Orca", the terminal, "Open in IntelliJ IDEA", Claude Desktop when installed and "Open folder"

#### Scenario: Copy launch command is an icon
- **WHEN** the user activates the copy icon or presses ⇧⌘C
- **THEN** the launch command is copied and the icon confirms it in place

### Requirement: Row quick actions
Hovering a row SHALL reveal a copy-launch-command icon action. Each row SHALL have a context menu built from the action catalog with "Open in <default runner>", "Open with ▸", "Copy launch command", "Copy path" and, except for the main worktree, "Delete worktree…".

#### Scenario: Copy from the row
- **WHEN** the user hovers a row and activates its copy icon
- **THEN** that worktree's launch command is copied without changing the selection

#### Scenario: Context menu
- **WHEN** the user opens the context menu of a non-main worktree's row
- **THEN** it offers open in the default runner, open with, copy launch command, copy path and delete worktree

### Requirement: Keyboard shortcuts on the selected worktree
With a worktree selected, Cmd/Ctrl+O SHALL open it in the default runner, Cmd/Ctrl+Shift+O SHALL open its "Open with…" menu, Cmd/Ctrl+Shift+C SHALL copy its launch command, and Cmd/Ctrl+Backspace SHALL open the delete confirmation for it unless it is the main worktree. Escape SHALL clear the selection.

#### Scenario: Copy with the keyboard
- **WHEN** a worktree is selected and the user presses Cmd/Ctrl+Shift+C
- **THEN** its launch command is copied to the clipboard

#### Scenario: Delete with the keyboard
- **WHEN** a non-main worktree is selected and the user presses Cmd/Ctrl+Backspace
- **THEN** the delete confirmation dialog opens for it

#### Scenario: Open with from the keyboard
- **WHEN** a worktree is selected and the user presses Cmd/Ctrl+Shift+O
- **THEN** the detail's "Open with…" menu opens with its first item focused
