# Spec Delta

## MODIFIED Requirements

### Requirement: Confirm before cleaning up
The system SHALL offer a "Clean up" action in the worktree view's header, covering the projects shown in the view, and in each project group's header, covering that project. Each action SHALL be enabled only when at least one worktree in its scope is selected for cleanup. Activating it SHALL show a confirmation dialog listing the selected worktrees grouped by project, with an option to also delete their local branches that is off by default. No Git command SHALL run until the user confirms.

#### Scenario: Nothing to clean up
- **WHEN** no worktree in the action's scope is selected for cleanup
- **THEN** that "Clean up" action is disabled

#### Scenario: Confirmation dialog
- **WHEN** the user activates "Clean up" in "All worktrees" with candidates in `api` and `main-repo`
- **THEN** a dialog lists the candidates under an `api` heading and a `main-repo` heading, with an unchecked "also delete their local branches" option, and nothing is removed until the user confirms

#### Scenario: User cancels
- **WHEN** the user cancels the cleanup dialog
- **THEN** no Git command runs and the list is unchanged

#### Scenario: One project from its group
- **WHEN** the user activates "Clean up" on the `api` group's header
- **THEN** the dialog only lists `api`'s candidates

## ADDED Requirements

### Requirement: Provider options follow the projects in scope
The cleanup dialog SHALL show each provider option (stop Gradle daemons, stop emulators, delete simulators, delete DerivedData) when at least one project in its scope declares that provider, and SHALL apply it only to the worktrees of projects that declare it.

#### Scenario: Mixed projects
- **WHEN** the cleanup covers `api`, which declares the Gradle provider, and `web`, which doesn't
- **THEN** the dialog offers stopping Gradle daemons, and only daemons of `api`'s removed worktrees are stopped
