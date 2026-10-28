# Spec Delta

## MODIFIED Requirements

### Requirement: Watch only while it is useful
The system SHALL only check the Git metadata of the projects shown in the current worktree view, SHALL stop checking a project when no shown view includes it, and SHALL skip a project's check while its list is loading or a create, remove or clean up action is running on it. Checks SHALL keep running while the main window is visible, whether or not it is focused. No check SHALL run while the main window is minimized or hidden in the tray, or while the app is hidden; a state the platform doesn't report SHALL count as visible. The light refresh on focus SHALL run when the window regains focus.

#### Scenario: Leaving the screen
- **WHEN** the user switches from "All worktrees" to the view filtered to `api`
- **THEN** only `api` keeps being checked

#### Scenario: Action in progress
- **WHEN** a check is due while a worktree is being created, removed or cleaned up in a project
- **THEN** that project's check is skipped, the other projects are checked, and the next check runs normally

#### Scenario: Window in the background
- **WHEN** the window stays visible on a second monitor while the user works in another app, and an agent starts in a shown worktree
- **THEN** checks keep running and the worktree shows the agent within a few seconds, without the window regaining focus

#### Scenario: Window minimized or app hidden
- **WHEN** the user minimizes the window, closes it to the tray, or hides the app, for a minute
- **THEN** no check runs during that minute, and checks resume when the window is shown again

#### Scenario: Loaded but hidden project
- **WHEN** `api` and `web` were loaded and the view now shows only `web`
- **THEN** checks read the Git metadata of `web` only, and agents are still detected for both
