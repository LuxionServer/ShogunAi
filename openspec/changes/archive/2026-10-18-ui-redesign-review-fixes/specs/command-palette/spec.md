# Spec Delta

## MODIFIED Requirements

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

### Requirement: Run results from the keyboard
The up and down arrows SHALL move through the results, Enter SHALL run the highlighted result's main action and close the palette, and Cmd/Ctrl+Enter on a worktree SHALL copy its launch command. The main actions are opening a view, opening a project's worktree view, selecting a worktree in "All worktrees", and running an action.

#### Scenario: Select a worktree
- **WHEN** the user highlights `feature/TASK-9` of `api` and presses Enter
- **THEN** the palette closes and "All worktrees" opens with that worktree selected and its detail shown

#### Scenario: Copy from the palette
- **WHEN** the user highlights a worktree and presses Cmd/Ctrl+Enter
- **THEN** its launch command is copied, the palette closes and a confirmation is shown, whichever view is open and whether or not the worktree is listed in it

## ADDED Requirements

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
