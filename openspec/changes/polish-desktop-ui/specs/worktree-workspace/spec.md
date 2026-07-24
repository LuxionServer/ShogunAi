## MODIFIED Requirements

### Requirement: List local branches eligible for a new worktree
The system SHALL let the user see all local branches, using `ListLocalBranchesUseCase`, which returns every local branch paired with whether it is already checked out in a worktree (including the base repository itself). In the dropdown, branches SHALL be visually grouped by domain — the segment of the branch name before the first `/`, or "otras" for branches without a `/` — with a clickable header shown for each distinct domain, in order of first appearance. Clicking a header SHALL collapse or expand the branches under that domain. The group containing the currently selected branch SHALL start expanded; other groups start collapsed. Once the user expands or collapses a group, that state SHALL persist across subsequent openings of the dropdown within the same screen instance, instead of resetting to collapsed every time.

#### Scenario: Local branches loaded successfully
- **WHEN** the user switches the create-worktree dialog to "Existing branch" mode
- **THEN** the app invokes `ListLocalBranchesUseCase` and displays every returned branch, each flagged with whether it is already checked out

#### Scenario: A branch already checked out is shown but disabled
- **WHEN** a local branch is currently checked out in the base repository or in another worktree
- **THEN** the branch still appears in the dropdown, rendered disabled with a note that it already has a worktree, instead of being omitted

#### Scenario: Listing local branches fails
- **WHEN** `ListLocalBranchesUseCase` returns a failed `Result` (e.g. `WorktreeError.GitCommandFailed`)
- **THEN** the screen shows an error message derived from the `WorktreeError` instead of a branch list

#### Scenario: Branches are grouped by domain in the dropdown
- **WHEN** the "Existing branch" dropdown displays local branches such as `feature/TASK-1`, `feature/TASK-2`, `fix/TASK-3`, and `develop`
- **THEN** the dropdown shows a "feature" header followed by `feature/TASK-1` and `feature/TASK-2`, then a "fix" header followed by `fix/TASK-3`, then an "otras" header followed by `develop`, instead of a single flat alphabetical list

#### Scenario: The group containing the selected branch starts expanded
- **WHEN** the "Existing branch" dropdown is opened and a branch is already selected
- **THEN** the domain group containing the selected branch is shown expanded, while other groups start collapsed

#### Scenario: A domain header can be collapsed and expanded
- **WHEN** the "Existing branch" dropdown is opened
- **THEN** the user can click a header (e.g. "feature") to expand it
- **WHEN** the user clicks an expanded header again
- **THEN** the branches under it are hidden again, without closing the dropdown

#### Scenario: Group expansion state is remembered across dropdown openings
- **WHEN** the user expands or collapses a domain group, closes the dropdown, and reopens it within the same worktree screen visit
- **THEN** each group's expanded/collapsed state matches what the user last set, instead of resetting to collapsed

## ADDED Requirements

### Requirement: Show a loading indicator while loading local branches
The system SHALL show the same visual loading indicator used elsewhere on the worktree screen while `ListLocalBranchesUseCase` is in progress for the "Existing branch" create-worktree mode, instead of a plain text message.

#### Scenario: Branches are loading
- **WHEN** the user switches the create-worktree form to "Existing branch" mode and `ListLocalBranchesUseCase` has not yet returned
- **THEN** the screen shows the same loading indicator style used for reloading the worktree list, instead of only a "Cargando ramas..." text

### Requirement: Dismiss an error message
The system SHALL let the user manually dismiss an error message shown on the worktree management screen, in addition to it being replaced automatically by a subsequent successful action.

#### Scenario: User dismisses an error
- **WHEN** an error message is visible on the worktree management screen and the user activates its dismiss control
- **THEN** the error message is hidden and no further action is taken

### Requirement: Show feedback when a worktree is created successfully
The system SHALL show a visible, transient confirmation when a worktree is created successfully, in addition to adding it to the displayed list.

#### Scenario: Worktree created successfully
- **WHEN** a worktree is created successfully
- **THEN** the screen shows a transient success message confirming the creation, which disappears automatically after a short delay

### Requirement: Show a format hint for the task id field
The system SHALL show a hint or example of the expected task id format next to the task id field, in addition to the existing invalid-format error message.

#### Scenario: Task id field is empty or unfocused
- **WHEN** the user has not yet entered an invalid task id
- **THEN** the field shows a hint with an example of a valid task id format

### Requirement: Collapse the "new worktree" form
The system SHALL let the user collapse and expand the "Nuevo worktree" section of the worktree management screen, instead of it always occupying fixed vertical space.

#### Scenario: Collapse the form
- **WHEN** the user activates the collapse control on the "Nuevo worktree" section
- **THEN** the section's fields are hidden, leaving only its header visible

#### Scenario: Expand the form
- **WHEN** the user activates the expand control on a collapsed "Nuevo worktree" section
- **THEN** the section's fields become visible again
