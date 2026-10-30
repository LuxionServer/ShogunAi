# Spec Delta

## ADDED Requirements

### Requirement: View header in one row
A worktree view's header SHALL fit in one row with, in this order, the view's title, the text filter, a "View ▾" menu, "Clean up N" when there is something to clean up, the "⋯" menu when it has entries, and the primary action. The header SHALL NOT wrap into a second row at any window width the app allows.

#### Scenario: Wide window
- **WHEN** the user opens "All worktrees" in a wide window with two worktrees selected for cleanup
- **THEN** one row shows "All worktrees", the filter field, "View", "Clean up 2" and "New worktree", and no "⋯" button

#### Scenario: Narrow window
- **WHEN** the window is resized to the app's minimum width
- **THEN** the header still has a single row

### Requirement: The View menu
"View ▾" SHALL hold the project filter in "Active" and "All worktrees", the grouping outside a project's view, the order and, last after a separator, "Reload" with its Cmd/Ctrl+R shortcut. Each choice SHALL be a checked item under its section's title, and picking one SHALL apply it and close the menu.

#### Scenario: Options of "All worktrees"
- **WHEN** the user opens "View" in "All worktrees" with projects `api` and `main-repo`
- **THEN** it shows a project section with "All projects" checked, `api` and `main-repo`, a grouping section, an order section with "Name" checked, and "Reload ⌘R" last

#### Scenario: Options of a project's view
- **WHEN** the user opens "View" in `api`'s view
- **THEN** it shows the order section and "Reload", and no project or grouping section

#### Scenario: Reload from the View menu
- **WHEN** the user picks "Reload" in "View"
- **THEN** the shown projects are reloaded, as with Cmd/Ctrl+R

### Requirement: Compact header on narrow windows
When the header is narrower than the app's compact breakpoint, the text filter SHALL be shown as a search icon and the primary action as a "+" icon button whose tooltip and accessible name are its label. Activating the icon or pressing Cmd/Ctrl+F SHALL show the filter field focused; it SHALL go back to the icon when it loses focus while empty, and SHALL stay as a field while it holds text.

#### Scenario: Filter from the icon
- **WHEN** the header is compact and the user activates the search icon
- **THEN** the filter field appears focused in its place

#### Scenario: Filter kept while it has text
- **WHEN** the header is compact, the filter holds `task-12` and the user clicks a row
- **THEN** the filter field stays visible with `task-12`

#### Scenario: New worktree as "+"
- **WHEN** the header is compact in "All worktrees"
- **THEN** the primary action is a "+" button named "New worktree", and it opens the "New worktree" dialog

## MODIFIED Requirements

### Requirement: Filter the view by project
Selecting a project in the sidebar SHALL show that project's worktree view, which SHALL NOT offer a project filter. "Active" and "All worktrees" SHALL offer a project filter in their header's "View" menu with "All projects" and each project, which narrows the current view to the picked project without changing the sidebar's selection. Each of the two views SHALL keep its own filter while the app runs.

#### Scenario: Filter from the header
- **WHEN** the user is in "All worktrees" and picks `api` in the header's "View" menu
- **THEN** only the `api` group is shown, the view is still "All worktrees" and "All worktrees" stays selected in the sidebar

#### Scenario: Back to all projects
- **WHEN** "All worktrees" is filtered to `api` and the user picks "All projects"
- **THEN** every project's group is shown

#### Scenario: Filter Active
- **WHEN** the user is in "Active" and picks `api` in the header's "View" menu
- **THEN** only `api`'s active worktrees are shown, grouped as before, and "Active" stays selected in the sidebar

#### Scenario: No filter in a project's view
- **WHEN** the user selects `api` in the sidebar
- **THEN** the view shows `api`'s worktrees and its "View" menu has no project filter

### Requirement: Sort worktrees within groups
Every worktree view SHALL offer in its header's "View" menu a sort choice with "Name" and "Last activity", defaulting to "Name", and SHALL remember each view's choice across restarts. "Name" SHALL order by branch in natural order, ignoring case and comparing numbers by value. "Last activity" SHALL order by activity date, most recent first. In both, the main worktree SHALL come first within its group.

#### Scenario: Natural order
- **WHEN** a group has `feature/TASK-10`, `feature/TASK-9` and `feature/task-11`, sorted by name
- **THEN** they are listed as `feature/TASK-9`, `feature/TASK-10`, `feature/task-11`

#### Scenario: By last activity
- **WHEN** the user picks "Last activity" in "Active", where `TASK-1` had a commit an hour ago and `TASK-2` was created 10 minutes ago without commits
- **THEN** `TASK-2` is listed before `TASK-1` within their groups, and the choice is kept after a restart

#### Scenario: Each view keeps its own order
- **WHEN** the user sorts "Active" by last activity
- **THEN** "All worktrees" and the projects' views keep their own order
