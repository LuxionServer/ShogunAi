# Spec Delta

## ADDED Requirements

### Requirement: Take down the Compose projects of removed worktrees
When the repository declares the Docker Compose provider, the cleanup dialog SHALL offer an option, checked by default, to also take down the Compose projects of the worktrees it removes, keeping their volumes. It runs after the removals, and a project that can't be taken down SHALL NOT turn the cleanup into a failure.

#### Scenario: Option shown
- **WHEN** the repository declares the Docker Compose provider and the user activates "Clean up"
- **THEN** the dialog shows a checked "also take down their Compose projects" option

#### Scenario: Repository without the provider
- **WHEN** the repository doesn't declare the provider
- **THEN** the dialog doesn't show the option and Docker isn't run

#### Scenario: Projects taken down
- **WHEN** the option is checked and a removed worktree had a Compose project
- **THEN** that project is taken down, its volumes are kept and the report says how many were taken down

#### Scenario: Docker unavailable
- **WHEN** Docker isn't reachable when the cleanup runs
- **THEN** the worktrees are still reported as removed and the report says the projects were kept
