# Spec Delta

## MODIFIED Requirements

### Requirement: Confirm before cleaning up
The view's header SHALL offer "Clean up N" for the shown projects, only when something in them can be cleaned up, and each project group's header a "Clean up" for its project. Each SHALL be enabled only when its scope has a cleanup candidate and no cleanup runs, saying why in a tooltip otherwise. Activating it SHALL show a confirmation listing the candidates by project, with an unchecked option to also delete their local branches. No Git command SHALL run until the user confirms.

#### Scenario: Nothing to clean up
- **WHEN** no worktree in a project group's scope is selected for cleanup
- **THEN** that group's "Clean up" action is disabled and its tooltip says why, and the view's header shows no "Clean up N" button

#### Scenario: Confirmation dialog
- **WHEN** the user activates "Clean up 2" in the header of "All worktrees" with candidates in `api` and `main-repo`
- **THEN** a dialog lists the candidates under an `api` heading and a `main-repo` heading, with an unchecked "also delete their local branches" option, and nothing is removed until the user confirms

#### Scenario: User cancels
- **WHEN** the user cancels the cleanup dialog
- **THEN** no Git command runs and the list is unchanged

#### Scenario: One project from its group
- **WHEN** the user activates "Clean up" on the `api` group's header
- **THEN** the dialog only lists `api`'s candidates
