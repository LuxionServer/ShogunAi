# Spec Delta

## MODIFIED Requirements

### Requirement: Confirm before cleaning up
The system SHALL offer a "Clean up N" button in the worktree view's header, covering the projects shown in the view and shown only when something in them can be cleaned up, and a "Clean up" action in each project group's header, covering that project. Each action SHALL be enabled only when at least one worktree in its scope is selected for cleanup and no cleanup runs, and when disabled SHALL say why in a tooltip. Activating it SHALL show a confirmation dialog listing the selected worktrees grouped by project, with an option to also delete their local branches that is off by default. No Git command SHALL run until the user confirms.

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
