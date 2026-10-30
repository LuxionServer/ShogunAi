# worktree-views Specification

## Purpose
Defines the views that show worktrees across projects, so the user can watch every worktree in one place and narrow the view to one project or to a text when needed.

## Requirements

### Requirement: All worktrees view
The app SHALL offer an "All worktrees" view that lists the worktrees of every saved project, grouped by project in the sidebar's order, using the same rows and detail as the project view.

#### Scenario: Two projects
- **WHEN** the projects `api` (2 worktrees) and `main-repo` (3 worktrees) are saved and the user opens "All worktrees"
- **THEN** the view shows an `api` group with 2 rows and a `main-repo` group with 3 rows

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

### Requirement: Filter the view by text
The view SHALL offer a text filter, focused with Cmd/Ctrl+F, that keeps only the worktrees whose branch or folder name contains the text, ignoring case. Groups without matching worktrees SHALL be hidden while the filter is active, and clearing it SHALL show every worktree again.

#### Scenario: Filter by task id
- **WHEN** the user types `task-12` in the text filter
- **THEN** only worktrees whose branch or folder contains `TASK-12`, in any case, remain, and groups with none are hidden

#### Scenario: No match
- **WHEN** no worktree matches the text
- **THEN** the view says that no worktree matches the filter, with an action to clear it

### Requirement: Project group headers
Each project group SHALL have a header with the project's name, preceded by its multirepo's name when it belongs to one (`product / app`), its number of worktrees, a collapse toggle, and the project's actions: "Project settings" and "New worktree" as icons, and a "More" (⋯) menu with "Clean up" in danger text, disabled with its reason when the project has no candidates. The header SHALL NOT show "Clean up" as a button. The collapsed state of each group SHALL be remembered across restarts.

#### Scenario: Collapse a group
- **WHEN** the user collapses the `api` group and restarts the app
- **THEN** the `api` group is still collapsed in "All worktrees"

#### Scenario: New worktree from a group
- **WHEN** the user activates "New worktree" on the `api` group's header
- **THEN** the "New worktree" dialog opens for `api`

#### Scenario: Project of a multirepo
- **WHEN** "All worktrees" is grouped by project and `app` belongs to the multirepo `product`
- **THEN** its header reads `product / app`

#### Scenario: Clean up from a project group
- **WHEN** `api` has cleanup candidates and the user opens the ⋯ menu of its group header
- **THEN** it offers "Clean up" in danger text, which opens the cleanup confirmation for `api`

#### Scenario: Nothing to clean up in a project group
- **WHEN** `api` has no cleanup candidates
- **THEN** the ⋯ menu's "Clean up" is disabled and says there is nothing to clean up

### Requirement: Projects load independently
The view SHALL load each project's worktrees independently, showing each group as soon as its list is read. A project whose list can't be read SHALL show its error inside its group, and the other groups SHALL load and work normally.

#### Scenario: One repository missing
- **WHEN** `api`'s base repository no longer exists and the user opens "All worktrees"
- **THEN** the `api` group shows an error that its repository wasn't found, and `main-repo`'s worktrees are listed normally

### Requirement: New worktree from a view spanning several projects
When "New worktree" is started from the unfiltered view, the dialog SHALL let the user pick the project, defaulting to the project used for the last worktree created, or the first project when there is none.

#### Scenario: Pick the project
- **WHEN** the user presses Cmd/Ctrl+N in "All worktrees" after last creating a worktree in `api`
- **THEN** the dialog opens with `api` selected as the project, and the user can change it before creating

#### Scenario: Filtered view
- **WHEN** the user presses Cmd/Ctrl+N with the view filtered to `main-repo`
- **THEN** the dialog creates the worktree in `main-repo` without asking for the project

### Requirement: Active worktrees view
The app SHALL offer an "Active" view that lists the active worktrees of every saved project, using the same rows and detail as the other worktree views, with each row showing its project's name when the view isn't grouped by project.

#### Scenario: Active across projects
- **WHEN** `api` has an agent running in `TASK-1`, and `main-repo` has uncommitted changes in `TASK-7` and a merged `TASK-3`
- **THEN** "Active" lists `TASK-1` (with `api`) and `TASK-7` (with `main-repo`), and not `TASK-3`

### Requirement: What counts as an active worktree
A worktree SHALL be active when its state is "Needs you", "Agent working", "New" or "Stopped". A worktree whose status isn't read yet SHALL only be active when an agent is running in it.

#### Scenario: Freshly created worktree
- **WHEN** a worktree was just created from the main branch's tip, with no changes and no agent
- **THEN** it is active, under "New"

#### Scenario: Main worktree with changes
- **WHEN** the main worktree has uncommitted changes and no agent
- **THEN** it is active

#### Scenario: Clean main worktree
- **WHEN** the main worktree is clean and has no agent
- **THEN** it isn't active

#### Scenario: Inactive worktree
- **WHEN** a worktree is flagged as inactive
- **THEN** it isn't active

#### Scenario: Status still loading
- **WHEN** a non-main worktree's status hasn't been read and no agent runs in it
- **THEN** it isn't shown in "Active" until its status is read

### Requirement: Keep the selected worktree visible
A worktree that is selected in "Active" SHALL stay listed while it remains selected, even if it stops being active, and SHALL leave the list once it is deselected.

#### Scenario: Agent finishes while selected
- **WHEN** the selected worktree's agent ends and the worktree is clean and merged
- **THEN** it stays in "Active", in the group for its new state, until the user selects something else

### Requirement: Group by state
"Active" SHALL group its worktrees by state, in this order: "Needs you", "Agent working", "New" and "Stopped". When "All worktrees" or a project's view is grouped by state, it SHALL also show "Merged", "Inactive" and "Folder missing" groups after those, and a trailing "Other" group for the clean main worktrees and the worktrees whose status isn't read yet, so that no worktree disappears. Empty groups SHALL be hidden.

#### Scenario: Active groups
- **WHEN** one active worktree has uncommitted changes and no agent, one has a working agent, one was just created and one has pushed commits of its own
- **THEN** "Active" shows them under "Needs you", "Agent working", "New" and "Stopped", in that order

#### Scenario: All worktrees by state
- **WHEN** "All worktrees" is grouped by state and two worktrees are merged
- **THEN** a "Merged" group lists them after the active groups

#### Scenario: Agent waiting for the user
- **WHEN** Herdr reports a worktree's agent as blocked
- **THEN** the worktree is listed under "Needs you" instead of "Agent working"

### Requirement: Remember each view's grouping
"Active" and "All worktrees" SHALL each offer grouping by state or by project, defaulting to state for "Active" and to project for "All worktrees", and SHALL remember each view's choice across restarts. A single project's view SHALL NOT offer the grouping choice and SHALL always group by state, since grouping by project would show one group.

#### Scenario: Change and restart
- **WHEN** the user groups "Active" by project and restarts the app
- **THEN** "Active" is grouped by project and "All worktrees" keeps its own grouping

#### Scenario: Single project's view
- **WHEN** the user opens a project from the sidebar
- **THEN** the view shows no "by project" option and groups its worktrees by state

#### Scenario: Remembered choice is kept
- **WHEN** the user groups "All worktrees" by project, opens a project and returns to "All worktrees"
- **THEN** "All worktrees" is still grouped by project

### Requirement: Nothing active
When no worktree is active, "Active" SHALL say so and offer "New worktree" and "See all worktrees".

#### Scenario: Everything finished
- **WHEN** every worktree of every project is clean and merged, inactive or the main one, with no agent
- **THEN** "Active" shows the empty state with both actions

### Requirement: Selection scrolls into view without jumping
Selecting a worktree SHALL scroll the list only when its row isn't fully visible, and the row SHALL end up fully visible below the sticky group header. A row that is already fully visible SHALL NOT move the list.

#### Scenario: Move the selection with the arrows
- **WHEN** the user moves the selection with the arrows between rows that are fully visible
- **THEN** the list doesn't scroll

#### Scenario: Select a worktree outside the visible area
- **WHEN** a worktree outside the visible area is selected, for example from the palette
- **THEN** the list scrolls until its row is fully visible and not covered by its group's header

### Requirement: State groups explain themselves
Each state group header SHALL show, next to its title, a hint of a few words saying what puts a worktree in that group, and SHALL show the full criterion in a tooltip on hover. The "Inactive" criterion SHALL name the configured number of days. Hints and tooltips SHALL be translated like the rest of the interface.

#### Scenario: Hint in the header
- **WHEN** "Active" shows a "New" group
- **THEN** its header reads "New" with the hint "no commits yet"

#### Scenario: Full criterion on hover
- **WHEN** the inactivity threshold is 3 days and the user hovers the "Inactive" group's title
- **THEN** a tooltip says the worktrees are clean, have no agent running and no activity in the last 3 days

### Requirement: Errors stay visible in every grouping
A project's load failure and the errors of its actions (removal, cleanup, branch loading, repair, closing Herdr workspaces) SHALL be shown when the view is grouped by state, and when the project's group is collapsed, with the project's name. A project whose load failed SHALL NOT count as having nothing active.

#### Scenario: Failed project in the Active view
- **WHEN** the Active view is grouped by state and one of the shown projects fails to load
- **THEN** the view shows that project's error above the state groups, and doesn't show "Nothing active"

#### Scenario: Collapsed group
- **WHEN** a project group is collapsed and a cleanup in that project fails
- **THEN** the error is still shown under the group header

### Requirement: Each worktree has one state
Each worktree SHALL have the first state that applies: "Folder missing" when its folder is gone; "Needs you" when an agent is blocked or done; "Agent working" when an agent is working or runs without a known state; "Needs you" when there are uncommitted changes or unpushed commits; "Other" for the main worktree or an unread status; then "New" with no commits of its own, "Merged", "Inactive", and "Stopped" otherwise. An idle agent SHALL NOT decide the state.

#### Scenario: Idle agent with pending work
- **WHEN** Herdr reports a worktree's agent as idle and the worktree has 2 uncommitted changes
- **THEN** the worktree is under "Needs you"

#### Scenario: Agent working with uncommitted changes
- **WHEN** a worktree's agent is working and the worktree has uncommitted changes
- **THEN** the worktree is under "Agent working"

#### Scenario: Blocked agent
- **WHEN** a worktree's agent is blocked
- **THEN** the worktree is under "Needs you", whatever its Git status

#### Scenario: Idle agent without pending work
- **WHEN** Herdr reports a worktree's agent as idle, the worktree is clean and its pushed branch has commits of its own
- **THEN** the worktree is under "Stopped", and its row shows the idle agent

#### Scenario: Agent without a known state
- **WHEN** a `claude` process runs in a clean worktree and no tool reports its state
- **THEN** the worktree is under "Agent working"

#### Scenario: New worktree
- **WHEN** a worktree's branch has no commits the main branch lacks, isn't merged, has no changes and no agent
- **THEN** the worktree is under "New"

#### Scenario: Pushed work waiting
- **WHEN** a worktree's branch has commits of its own, all pushed to its upstream, no changes and no agent, and its activity date is recent
- **THEN** the worktree is under "Stopped"

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

### Requirement: The list takes the whole width without a selection
When no worktree is selected, a worktree view SHALL show only its list across the content area, with no detail pane or summary panel beside it.

#### Scenario: Nothing selected
- **WHEN** a view is shown on a wide window with no worktree selected
- **THEN** the list takes the whole content width

#### Scenario: Selection cleared
- **WHEN** the user clears the selection on a wide window
- **THEN** the detail pane closes and the list takes the whole content width again

### Requirement: View summary in the header
A worktree view's header SHALL show, on one line under the view's title, the number of worktrees shown, their total size and, when there are any, how many can be cleaned up. The summary SHALL be informative only and offer no action, and SHALL NOT be shown while the view's projects are loading.

#### Scenario: Project view
- **WHEN** a project with five worktrees totalling 1.2 GB, two of them merged and clean, is shown
- **THEN** the header reads "5 worktrees · 1.2 GB · 2 to clean up" (or its translation), with no "Clean up" action in it

#### Scenario: Nothing to clean up
- **WHEN** no shown worktree can be cleaned up
- **THEN** the summary names only the worktrees and their size

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
