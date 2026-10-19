# Spec Delta

## MODIFIED Requirements

### Requirement: Watch only while it is useful
The system SHALL only check the Git metadata of the projects shown in the current worktree view, SHALL stop checking a project when no shown view includes it, and SHALL skip a project's check while its list is loading or a create, remove or clean up action is running on it. No check SHALL run while the main window isn't focused or is minimized; the light refresh on focus SHALL run when it regains focus.

#### Scenario: Leaving the screen
- **WHEN** the user switches from "All worktrees" to the view filtered to `api`
- **THEN** only `api` keeps being checked

#### Scenario: Action in progress
- **WHEN** a check is due while a worktree is being created, removed or cleaned up in a project
- **THEN** that project's check is skipped, the other projects are checked, and the next check runs normally

#### Scenario: Window in the background
- **WHEN** the user switches to another app for a minute
- **THEN** no check runs during that minute, and when the user comes back the shown worktrees' statuses and agents are refreshed once

#### Scenario: Loaded but hidden project
- **WHEN** `api` and `web` were loaded and the view now shows only `web`
- **THEN** checks read the Git metadata of `web` only, and agents are still detected for both
