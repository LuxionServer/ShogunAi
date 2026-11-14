## MODIFIED Requirements

### Requirement: Watch only while it is useful
The system SHALL only check the Git metadata of the projects shown in the current worktree view, SHALL stop checking a project when no shown view includes it, and SHALL skip a project's check while it is loading or busy creating, removing or cleaning up.

#### Scenario: Leaving the screen
- **WHEN** the user switches from "All worktrees" to the view filtered to `api`
- **THEN** only `api` keeps being checked

#### Scenario: Action in progress
- **WHEN** a check is due while a worktree is being created, removed or cleaned up in a project
- **THEN** that project's check is skipped, the other projects are checked, and the next check runs normally

#### Scenario: Window in the background
- **WHEN** the profile is Balanced, the window stays visible on a second monitor while the user works in another app, and an agent starts in a shown worktree
- **THEN** checks keep running every 15 seconds and the worktree shows the agent by the next check, without the window regaining focus

#### Scenario: Window minimized or app hidden
- **WHEN** both notification switches are off and the user minimizes the window, closes it to the tray, or hides the app, for a minute
- **THEN** no check runs during that minute, and checks resume when the window is shown again

#### Scenario: Loaded but hidden project
- **WHEN** `api` and `web` were loaded and the view now shows only `web`
- **THEN** checks read the Git metadata of `web` only, and agents are still detected for both

### Requirement: Check while the window is visible
Checks SHALL run while the main window is visible, at the refresh cadence's focused interval when it is focused and at its unfocused interval when it isn't, and SHALL pause while it is minimized, in the tray or the app is hidden; unreported states count as visible and focused. While the window isn't visible and a notification switch is on, only running agents SHALL keep being detected, at the unfocused interval, without reading Git metadata or devices. The light refresh on focus SHALL run when the window regains focus.

#### Scenario: Visible but not focused
- **WHEN** the profile is Balanced, the window stays visible on a second monitor while the user works in another app, and an agent starts in a shown worktree
- **THEN** checks keep running every 15 seconds and the worktree shows the agent by the next check, without the window regaining focus

#### Scenario: Minimized, in the tray or hidden
- **WHEN** both notification switches are off and the user minimizes the window, closes it to the tray, or hides the app, for a minute
- **THEN** no check runs during that minute, and checks resume when the window is shown again

#### Scenario: Hidden with notifications on
- **WHEN** the profile is Balanced, "Blocked" is on and the window is closed to the tray
- **THEN** running agents are detected every 15 seconds for every saved project, and no Git metadata or device state is read until the window is shown again

#### Scenario: Focus regained
- **WHEN** the window regains focus
- **THEN** the light refresh on focus runs
