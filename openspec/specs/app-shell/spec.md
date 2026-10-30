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
The sidebar SHALL list every saved project by name, alphabetically unless the user has reordered them, with an "Add project" action. Projects that belong to a multirepo SHALL be listed inside that multirepo's folder instead of at the top level. The selected entry SHALL be visually marked.

#### Scenario: Projects listed
- **WHEN** the app has the projects `api`, `mobile` and `main-repo` saved and the user hasn't reordered them
- **THEN** the sidebar lists `api`, `main-repo` and `mobile`, in that order, followed by "Add project"

#### Scenario: Projects of a multirepo
- **WHEN** `api` and `mobile` belong to the multirepo `product`
- **THEN** the sidebar lists `main-repo` and the folder `product` with `api` and `mobile` inside

### Requirement: First launch without projects
When no project is saved, the content area SHALL show an empty state that explains what a project is and offers to add one, and SHALL offer to take the ShogunAi tour next to it.

#### Scenario: No projects
- **WHEN** the app starts with no saved projects
- **THEN** the content area shows the empty state with an action that opens the add-project form and an action that starts the tour

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
The app SHALL provide an application menu with File (Add project, and on Windows and Linux Preferences and Quit), View (Reload, Toggle sidebar) and Help (ShogunAi Tour, Check for updates, Copy diagnostic report, and on Windows and Linux About) entries, each with its keyboard shortcut when it has one. On macOS, Preferences and About SHALL be in the application menu named after the app.

#### Scenario: Reload from the menu
- **WHEN** the user chooses View > Reload or presses Cmd/Ctrl+R
- **THEN** the current view reloads its data

#### Scenario: macOS conventions
- **WHEN** the app runs on macOS
- **THEN** "About ShogunAi" and "Settings…" (Cmd+,) are in the ShogunAi menu

#### Scenario: Tour from the Help menu
- **WHEN** the user has projects and chooses Help > "ShogunAi Tour"
- **THEN** the tour starts

### Requirement: Preferences window
The app SHALL provide a Preferences window, opened from the menu or with Cmd/Ctrl+Comma, holding the theme mode, the language, a "Tools" section with the terminal and the default IDE, each chosen among the ones detected on the machine, and a "Resources" section with the capacity profile. Changes SHALL apply immediately to the main window.

#### Scenario: Change the theme from Preferences
- **WHEN** the user opens Preferences and selects Dark
- **THEN** the main window and Preferences switch to the dark scheme immediately

#### Scenario: Choose a terminal
- **WHEN** Terminal and iTerm2 are installed and the user chooses iTerm2 in Preferences
- **THEN** the worktree actions read "Open in iTerm2" and open iTerm2

#### Scenario: Nothing detected
- **WHEN** no supported terminal is installed
- **THEN** the terminal choice says none was found and "Open in terminal" isn't offered

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
The sidebar's views section SHALL list "Active" first, above "All worktrees", with the number of worktrees waiting for the user. When no view was saved from a previous session, the app SHALL open on "Active".

#### Scenario: First launch with projects
- **WHEN** the app starts with saved projects and no saved view
- **THEN** it opens on "Active"

#### Scenario: Count
- **WHEN** four worktrees are active across all projects and one of them is "Waiting for you"
- **THEN** the sidebar shows "Active" with 1

#### Scenario: Nothing waiting
- **WHEN** worktrees are active but none is "Waiting for you"
- **THEN** the sidebar shows "Active" without a count

### Requirement: Activity in the sidebar's projects
Each project in the sidebar SHALL show its number of worktrees waiting for the user, and the agent icon in the info color, next to the count, when an agent is running in any of its worktrees, with the running agents in its tooltip and accessible name. Projects with no worktree waiting for the user SHALL show no count. A multirepo's folder SHALL show the sum of its projects' counts and the agent icon when any of them has it.

#### Scenario: Project with an agent
- **WHEN** `api` has two worktrees waiting for the user and an agent running in another one
- **THEN** its sidebar entry shows 2 and the agent icon in the info color

#### Scenario: Unfinished work isn't counted
- **WHEN** `api`'s only active worktree is "Unfinished"
- **THEN** its sidebar entry shows no count

#### Scenario: Multirepo folder
- **WHEN** `api` and `mobile` belong to `product` and have two and one worktrees waiting for the user, with an agent in `api`
- **THEN** `product` shows 3 and the agent icon in the info color

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

### Requirement: Choose the capacity profile in Preferences
The "Resources" section SHALL offer the profiles Automatic, Light, Balanced, One per worktree and Custom, and show the limits each one gives on this machine for emulators, iOS simulators (on macOS only) and Gradle daemons. Custom SHALL let the user set each limit to a positive number or to no limit. The choice SHALL be saved with the app's settings and used by the app and the `shogun` command.

#### Scenario: Automatic shows its limits
- **WHEN** the user opens the "Resources" section on a 16 GB machine with 8 cores
- **THEN** Automatic is selected and says it gives 1 emulator, 1 iOS simulator and 2 Gradle daemons

#### Scenario: Custom limits
- **WHEN** the user chooses Custom and sets emulators to 3
- **THEN** the next emulator start from the app or from `shogun emulator start` uses a limit of 3

#### Scenario: Invalid number
- **WHEN** the user types 0 or a non-number as a Custom limit
- **THEN** the field shows that it must be a positive number or no limit, and the previous value is kept

### Requirement: Errors in secondary windows don't close the app
An unexpected error raised while composing, recomposing or running effects of the Preferences or About window SHALL close only that window, SHALL be recorded in the diagnostics log as an unhandled error, and SHALL be reported to the user with a message in the main window in the selected language. The main window and the rest of the app SHALL keep running.

#### Scenario: Error when opening Preferences
- **WHEN** the Preferences window throws while it is first composed
- **THEN** the Preferences window closes, the main window stays open, an unhandled-error entry is added to the diagnostics log and the main window shows a message saying the window closed because of an error

#### Scenario: Error after the window is open
- **WHEN** the Preferences window is open and its content throws during a later recomposition or in a background effect
- **THEN** the same happens: only Preferences closes, the error is recorded and the main window shows the message

#### Scenario: Error in About
- **WHEN** the About window throws
- **THEN** only the About window closes, the error is recorded and the main window shows the message

#### Scenario: Reopening after an error
- **WHEN** a secondary window closed because of an error and the user opens it again
- **THEN** the window opens normally and is isolated in the same way

### Requirement: Reorder the sidebar by dragging
The user SHALL be able to drag a top-level entry of the sidebar (a project or a multirepo folder) to another place among the top-level entries, and a project inside a multirepo folder to another place inside that folder, in the expanded and in the compact sidebar. An entry SHALL NOT move between levels, and a short press without movement SHALL still select it.

#### Scenario: Drag a project up
- **WHEN** the sidebar lists `api`, `main-repo` and `mobile` and the user drags `mobile` above `api`
- **THEN** the sidebar lists `mobile`, `api` and `main-repo`

#### Scenario: Inside a multirepo folder
- **WHEN** the folder `product` holds `api`, `app` and `docs` and the user drags `docs` to the top of the folder
- **THEN** the folder holds `docs`, `api` and `app`, and the top-level order is unchanged

#### Scenario: Dropped outside its level
- **WHEN** the user drags `tools`, a top-level project, onto the projects inside `product`
- **THEN** `tools` stays at the top level, placed at the nearest position of its level, and doesn't join the multirepo

#### Scenario: Click without dragging
- **WHEN** the user presses and releases `api` without moving the pointer
- **THEN** `api` is selected and the order doesn't change

### Requirement: Reorder the sidebar from the context menu
The context menu of every reorderable sidebar entry SHALL offer "Move up" and "Move down", which move it one place within its level and are unavailable at the first and last place, and "Sort alphabetically", which drops the manual order of that level.

#### Scenario: Move down
- **WHEN** the sidebar lists `api`, `main-repo` and `mobile` and the user chooses "Move down" on `api`
- **THEN** the sidebar lists `main-repo`, `api` and `mobile`

#### Scenario: First entry
- **WHEN** the user opens the context menu of the first entry of its level
- **THEN** "Move up" isn't offered and "Move down" is

#### Scenario: Back to alphabetical order
- **WHEN** the top level was reordered and the user chooses "Sort alphabetically" on one of its entries
- **THEN** the top-level entries are listed alphabetically again, and the order inside each multirepo folder is kept

### Requirement: The sidebar order persists
The manual order of each level SHALL be saved in the app's settings and restored on the next launch. Entries without a saved place SHALL be listed after the ordered ones, alphabetically among themselves, and saved places of entries that no longer exist SHALL be ignored.

#### Scenario: Restart after reordering
- **WHEN** the user moves `mobile` to the top and restarts the app
- **THEN** `mobile` is still the first top-level entry

#### Scenario: New project after reordering
- **WHEN** the top level was reordered as `mobile`, `api`, `main-repo` and the user adds the project `billing`
- **THEN** the sidebar lists `mobile`, `api`, `main-repo` and `billing`

#### Scenario: Removed project
- **WHEN** the user removes `api` after reordering and later adds a project named `api` again
- **THEN** the new `api` is listed at the end of its level
