# command-palette Specification

## Purpose
Gives the user one keyboard entry point to every view, project, worktree and frequent action of the app, so getting somewhere or running something doesn't depend on where it sits in the window.

## Requirements

### Requirement: Open the command palette
Cmd/Ctrl+K and View > Command palette SHALL open the command palette over the main window with its search field focused, from any view. Escape, or clicking outside it, SHALL close it without running anything.

#### Scenario: Open and close
- **WHEN** the user presses Cmd/Ctrl+K in any view and then Escape
- **THEN** the palette opens with the search field focused, then closes, and the view is unchanged

### Requirement: What the palette searches
The palette SHALL search the views (Active, All worktrees, each visible machine resource, Preferences), every saved project, the worktrees of every saved project, and the actions "Add project", "New worktree in <project>", "Settings of <project>", "Clean up", "Reload" and "Toggle sidebar".

#### Scenario: Find a worktree of another project
- **WHEN** the user is in the view filtered to `main-repo` and types `TASK-9`
- **THEN** the palette lists the worktree `feature/TASK-9` of `api`

### Requirement: Matching and ranking
Results SHALL match when the typed characters appear in order in the result's name, branch, folder or project, ignoring case, gaps allowed. Results SHALL be ranked: exact or prefix matches first, then matches at word starts (after `/`, `-`, `_` or a space), then any other match, ties SHALL go to the most recently used item, and remaining ties SHALL go to projects before worktrees.

#### Scenario: Prefix beats subsequence
- **WHEN** the user types `api` with the project `api` and a worktree `fix/rapid-ui` saved
- **THEN** the project `api` is listed before `fix/rapid-ui`

#### Scenario: Gaps allowed
- **WHEN** the user types `ft9`
- **THEN** `feature/TASK-9` is listed

#### Scenario: Project before worktree on a tie
- **WHEN** the user types `web` with the project `web-app` and its main worktree saved, and neither was used recently
- **THEN** the project `web-app` is listed before the worktree `main · web-app`

### Requirement: Recent items with an empty query
With an empty query, the palette SHALL list the items run most recently from it, most recent first, up to 20, remembered across restarts. Before anything has been run, it SHALL list the views.

#### Scenario: Reopen after use
- **WHEN** the user ran "Settings of api" from the palette and opens it again
- **THEN** "Settings of api" is the first result before typing

### Requirement: Results show their context
Each result SHALL show its kind (view, project, worktree or action), its context when it has one (for a worktree, its project and state indicator), and the keyboard shortcut of the action when it has one.

#### Scenario: Worktree result
- **WHEN** a worktree result is listed
- **THEN** it shows the branch, its project's name and its state indicator, marked as a worktree

### Requirement: Run results from the keyboard
The up and down arrows SHALL move through the results, Enter SHALL run the highlighted result's main action and close the palette, and Cmd/Ctrl+Enter on a worktree SHALL copy its launch command. The main actions are opening a view, opening a project's worktree view, selecting a worktree in "All worktrees", and running an action.

#### Scenario: Select a worktree
- **WHEN** the user highlights `feature/TASK-9` of `api` and presses Enter
- **THEN** the palette closes and "All worktrees" opens with that worktree selected and its detail shown

#### Scenario: Copy from the palette
- **WHEN** the user highlights a worktree and presses Cmd/Ctrl+Enter
- **THEN** its launch command is copied, the palette closes and a confirmation is shown, whichever view is open and whether or not the worktree is listed in it

### Requirement: No destructive actions
The palette SHALL NOT offer any action that deletes or removes something.

#### Scenario: Search for delete
- **WHEN** the user types `delete`
- **THEN** no result deletes a worktree, a project, a simulator or DerivedData

### Requirement: Typing starts immediately
Keys typed right after opening the palette SHALL reach its search field, without waiting for it to be shown.

#### Scenario: Type right after opening
- **WHEN** the user presses Cmd/Ctrl+K and immediately types `t202`
- **THEN** the search field contains `t202` and the results are filtered by it

### Requirement: The highlighted result is visible
Whenever the query or the results change, the highlighted result SHALL be visible in the list, and the list SHALL show the first results when the query changes.

#### Scenario: Query changes after scrolling
- **WHEN** the user scrolls the results down, then edits the query
- **THEN** the list shows the first results, including the highlighted one

### Requirement: Result rows lay out their parts
A result row SHALL give its title the free width of the row, truncating it only when it doesn't fit, and SHALL right-align the keyboard shortcut.

#### Scenario: Short title with free space
- **WHEN** a result has a title that fits in the row and a shortcut
- **THEN** the title isn't truncated and the shortcut sits at the row's right edge

### Requirement: The palette shows its secondary action
When the highlighted result is a worktree, the palette SHALL show that Cmd/Ctrl+Enter copies its launch command.

#### Scenario: Worktree highlighted
- **WHEN** the user highlights a worktree in the palette
- **THEN** a hint shows that Cmd/Ctrl+Enter copies its launch command

#### Scenario: Project highlighted
- **WHEN** the highlighted result is a project
- **THEN** the hint isn't shown
