## ADDED Requirements

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
