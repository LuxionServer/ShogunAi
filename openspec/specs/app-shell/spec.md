# app-shell Specification

## Purpose
Defines the structure of the main window: a persistent sidebar to move between views, a content area, and the desktop conventions around them (application menu, Preferences, About, restored window state).

## Requirements

### Requirement: Window with a sidebar and a content area
The main window SHALL show a sidebar and a content area side by side. The sidebar SHALL stay visible while the user moves between views, and selecting an entry in it SHALL show that view in the content area without leaving the window.

#### Scenario: Switching between projects
- **WHEN** the user selects one project in the sidebar and then another
- **THEN** the content area shows the second project's worktrees, and the sidebar stays in place with the second project marked as selected

### Requirement: Sidebar lists the projects
The sidebar SHALL list every saved project by name, alphabetically, with an "Add project" action. The selected entry SHALL be visually marked.

#### Scenario: Projects listed
- **WHEN** the app has the projects `api`, `mobile` and `main-repo` saved
- **THEN** the sidebar lists `api`, `main-repo` and `mobile`, in that order, followed by "Add project"

### Requirement: First launch without projects
When no project is saved, the content area SHALL show an empty state that explains what a project is and offers to add one.

#### Scenario: No projects
- **WHEN** the app starts with no saved projects
- **THEN** the content area shows the empty state with an action that opens the add-project form

### Requirement: Reopen the last view
The app SHALL reopen on the view that was selected when it was last closed. If that view no longer exists (for example, a deleted project), it SHALL open on "Active", or on the empty state when there are none.

#### Scenario: Restart on a project
- **WHEN** the user closes the app while `mobile` is selected and starts it again
- **THEN** the app opens with `mobile` selected

#### Scenario: Last project was deleted
- **WHEN** the last selected project no longer exists at startup
- **THEN** the app opens on "Active"

### Requirement: Restore the window's size and position
The app SHALL restore the main window's size, position and maximized state from the previous session. If the saved position is outside every connected display, the window SHALL open centered on the main display.

#### Scenario: Restart after resizing
- **WHEN** the user resizes and moves the window, closes the app and starts it again
- **THEN** the window opens with the same size and position

#### Scenario: Display disconnected
- **WHEN** the window was last on a display that is no longer connected
- **THEN** the window opens centered on the main display

### Requirement: Compact sidebar on narrow windows
The sidebar SHALL collapse to a compact rail of icons when the window is narrower than a threshold, and expand again when it is wider. The user SHALL also be able to toggle it from the View menu and with Cmd/Ctrl+Backslash, and that choice SHALL be remembered.

#### Scenario: Narrow window
- **WHEN** the window is resized below the threshold
- **THEN** the sidebar shows only icons and tooltips, and the content area takes the freed width

#### Scenario: Manual toggle
- **WHEN** the user presses Cmd/Ctrl+Backslash on a wide window
- **THEN** the sidebar collapses to the rail, and stays collapsed after a restart

### Requirement: Application menu
The app SHALL provide an application menu with File (Add project, and on Windows and Linux Preferences and Quit), View (Reload, Toggle sidebar) and Help (About) entries, each with its keyboard shortcut. On macOS, Preferences and About SHALL be in the application menu named after the app.

#### Scenario: Reload from the menu
- **WHEN** the user chooses View > Reload or presses Cmd/Ctrl+R
- **THEN** the current view reloads its data

#### Scenario: macOS conventions
- **WHEN** the app runs on macOS
- **THEN** "About ShogunAi" and "Settings…" (Cmd+,) are in the ShogunAi menu

### Requirement: Preferences window
The app SHALL provide a Preferences window, opened from the menu or with Cmd/Ctrl+Comma, holding the theme mode and the language. Changes SHALL apply immediately to the main window.

#### Scenario: Change the theme from Preferences
- **WHEN** the user opens Preferences and selects Dark
- **THEN** the main window and Preferences switch to the dark scheme immediately

### Requirement: About shows the version
The app SHALL provide an About window with the app's name, icon, version and a link to the project's repository.

#### Scenario: Open About
- **WHEN** the user chooses Help > About (or About ShogunAi on macOS)
- **THEN** a window shows the app's icon, name and version

### Requirement: Sidebar views section
The sidebar SHALL show a views section above the projects, with an "All worktrees" entry showing the total number of worktrees. Project entries SHALL open the worktree view of that project.

#### Scenario: All worktrees entry
- **WHEN** the saved projects have 7 worktrees in total
- **THEN** the sidebar's views section shows "All worktrees" with 7

#### Scenario: Project entry is a filter
- **WHEN** the user selects `api` in the sidebar
- **THEN** the content area shows the worktree view with only `api`'s worktrees and `api` marked as selected in the sidebar

### Requirement: Active is the main view
The sidebar's views section SHALL list "Active" first, above "All worktrees", with the number of active worktrees. When no view was saved from a previous session, the app SHALL open on "Active".

#### Scenario: First launch with projects
- **WHEN** the app starts with saved projects and no saved view
- **THEN** it opens on "Active"

#### Scenario: Count
- **WHEN** four worktrees are active across all projects
- **THEN** the sidebar shows "Active" with 4

### Requirement: Activity in the sidebar's projects
Each project in the sidebar SHALL show its number of active worktrees, and an indicator in the info color when an agent is running in any of its worktrees. Projects with no active worktree SHALL show no count.

#### Scenario: Project with an agent
- **WHEN** `api` has two active worktrees and an agent running in one of them
- **THEN** its sidebar entry shows 2 and the agent indicator

### Requirement: Shortcuts for the views
Cmd/Ctrl+1 SHALL open "Active" and Cmd/Ctrl+2 SHALL open "All worktrees", from anywhere in the main window, and both SHALL be listed in the View menu.

#### Scenario: Jump to Active
- **WHEN** the user is in a project's settings and presses Cmd/Ctrl+1
- **THEN** the content area shows "Active", after the usual unsaved-changes confirmation if the settings had changes

### Requirement: Focus returns to the main window
When a dialog or secondary window (Preferences, a confirmation, "New worktree") closes, the main window SHALL regain focus, so the first click acts on the clicked element and the menu bar keeps all its menus.

#### Scenario: Close Preferences
- **WHEN** the user closes Preferences with Cmd/Ctrl+W
- **THEN** the main window is focused, the menu bar shows its menus and the first click is not swallowed

#### Scenario: Cancel a dialog
- **WHEN** the user closes "New worktree" with Escape or answers "Keep editing" to a discard confirmation
- **THEN** the main window is focused and the menu bar shows its menus
