## MODIFIED Requirements

### Requirement: Reload when the repository's Git metadata changes
While a worktree view is shown, the system SHALL check the Git metadata of every saved project at the interval of the refresh cadence, whichever projects the view shows, and reload that project's worktree list, statuses and overview when its metadata differs from the previous check. Projects whose metadata didn't change SHALL NOT be reloaded.

#### Scenario: Worktree created from a terminal
- **WHEN** a worktree is added to a shown project's repository by another tool
- **THEN** it appears in that project's group by the next check, without the user reloading

#### Scenario: Commit in a worktree
- **WHEN** a commit is made in one of the listed worktrees
- **THEN** its status and last commit date are updated by the next check

#### Scenario: Worktree locked or removed
- **WHEN** a listed worktree is locked, unlocked or removed by another tool
- **THEN** the list reflects it by the next check

#### Scenario: Nothing changed
- **WHEN** a project's metadata is the same as in the previous check
- **THEN** that project's list isn't reloaded, even if another shown project is

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
- **WHEN** the user minimizes the window, closes it to the tray, or hides the app, for a minute
- **THEN** no check runs during that minute, and checks resume when the window is shown again

#### Scenario: Loaded but hidden project
- **WHEN** `api` and `web` were loaded and the view now shows only `web`
- **THEN** checks read the Git metadata of `web` only, and agents are still detected for both

## ADDED Requirements

### Requirement: The Git metadata that is watched
A project's watched Git metadata SHALL be its main repository's `HEAD`, `index`, `packed-refs`, `FETCH_HEAD`, everything under `refs`, and everything under `worktrees`, which holds each linked worktree's `HEAD`, `index` and lock.

#### Scenario: Fetch
- **WHEN** a fetch from a terminal updates `FETCH_HEAD` and a remote ref of a shown project
- **THEN** that project is reloaded by the next check

#### Scenario: Linked worktree's index
- **WHEN** a file is staged in a listed linked worktree, changing its `index` under `worktrees`
- **THEN** that project is reloaded by the next check

### Requirement: Check while the window is visible
Checks SHALL run while the main window is visible, at the refresh cadence's focused interval when it is focused and at its unfocused interval when it isn't, and SHALL pause while it is minimized, in the tray or the app is hidden; unreported states count as visible and focused. The light refresh on focus SHALL run when the window regains focus.

#### Scenario: Visible but not focused
- **WHEN** the profile is Balanced, the window stays visible on a second monitor while the user works in another app, and an agent starts in a shown worktree
- **THEN** checks keep running every 15 seconds and the worktree shows the agent by the next check, without the window regaining focus

#### Scenario: Minimized, in the tray or hidden
- **WHEN** the user minimizes the window, closes it to the tray, or hides the app, for a minute
- **THEN** no check runs during that minute, and checks resume when the window is shown again

#### Scenario: Focus regained
- **WHEN** the window regains focus
- **THEN** the light refresh on focus runs
