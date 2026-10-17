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
The app SHALL reopen on the view that was selected when it was last closed. If that view no longer exists (for example, a deleted project), it SHALL open on the first project, or on the empty state when there are none.

#### Scenario: Restart on a project
- **WHEN** the user closes the app while `mobile` is selected and starts it again
- **THEN** the app opens with `mobile` selected

#### Scenario: Last project was deleted
- **WHEN** the last selected project no longer exists at startup
- **THEN** the app opens on the first project of the sidebar

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
