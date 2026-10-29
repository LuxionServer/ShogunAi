## MODIFIED Requirements

### Requirement: Project summary when nothing is selected
When no worktree is selected, the detail pane SHALL show the project's summary: the number of worktrees, their total size, and how many can be cleaned up. The summary SHALL be informative only and offer no action; cleaning up stays in the view's ⋯ menu.

#### Scenario: Nothing selected
- **WHEN** a project with five worktrees, two of them merged and clean, is shown with no selection
- **THEN** the detail pane shows five worktrees, their total size and two to clean up, with no "Clean up" action
