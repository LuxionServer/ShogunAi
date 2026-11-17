## ADDED Requirements

### Requirement: Open the app on a worktree from the command line
When `<path>` given to `shogun app` is inside a worktree of the project other than its main checkout, the app SHALL show that worktree selected in "All worktrees", revealing it if its project is collapsed or filtered out, instead of only showing the project. The same request SHALL serve a click on an agent notification. A request naming a worktree that no longer exists SHALL show its project.

#### Scenario: Inside a linked worktree
- **WHEN** the user runs `shogun app` inside the worktree `feature/login` of a registered project
- **THEN** the app comes to the front with `feature/login` selected

#### Scenario: Inside the main checkout
- **WHEN** the user runs `shogun app` inside the main checkout
- **THEN** the app shows the project's worktrees, as before

#### Scenario: Worktree removed before the request is served
- **WHEN** the request names a worktree that was removed in the meantime
- **THEN** the app shows its project's worktrees with nothing selected

#### Scenario: Older app
- **WHEN** an app from before this change receives a request to open a worktree
- **THEN** it ignores and deletes the request, as any request it doesn't understand
