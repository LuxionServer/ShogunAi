# worktree-auto-refresh Specification

## Purpose
Keeps the worktree screen up to date with what happens in the repository while it is open, so worktrees, commits and agents started from terminals show up without reloading by hand.

## Requirements

### Requirement: Reload when the repository's Git metadata changes
While a worktree view is shown, the system SHALL check the Git metadata of every saved project every 3 seconds, whichever projects the view shows, and reload that project's worktree list, statuses and overview when its metadata differs from the previous check. Projects whose metadata didn't change SHALL NOT be reloaded. The watched metadata is each main repository's `HEAD`, `index`, `packed-refs`, `FETCH_HEAD`, everything under `refs`, and everything under `worktrees`, which holds each linked worktree's `HEAD`, `index` and lock.

#### Scenario: Worktree created from a terminal
- **WHEN** a worktree is added to a shown project's repository by another tool
- **THEN** it appears in that project's group within a few seconds, without the user reloading

#### Scenario: Commit in a worktree
- **WHEN** a commit is made in one of the listed worktrees
- **THEN** its status and last commit date are updated within a few seconds

#### Scenario: Worktree locked or removed
- **WHEN** a listed worktree is locked, unlocked or removed by another tool
- **THEN** the list reflects it within a few seconds

#### Scenario: Nothing changed
- **WHEN** a project's metadata is the same as in the previous check
- **THEN** that project's list isn't reloaded, even if another shown project is

### Requirement: Refresh running agents on every check
The system SHALL detect running agents again on every check, once for the worktrees of all saved projects, even when no Git metadata changed, so agents starting or ending in a worktree are shown without a full reload.

#### Scenario: Agent starts
- **WHEN** an agent starts in a listed worktree and no Git metadata changes
- **THEN** the worktree shows the agent as running within a few seconds

#### Scenario: One detection for all projects
- **WHEN** "All worktrees" shows three projects and a check runs
- **THEN** running processes are inspected once, and each agent is attributed to its worktree in any of the three projects

### Requirement: Watch only while it is useful
The system SHALL only check the Git metadata of the projects shown in the current worktree view, SHALL stop checking a project when no shown view includes it, and SHALL skip a project's check while it is loading or busy creating, removing or cleaning up. Checks SHALL run while the main window is visible, focused or not, and SHALL pause while it is minimized, in the tray or the app is hidden; unreported states count as visible. The light refresh on focus SHALL run when the window regains focus.

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

### Requirement: Refresh device states at a slower pace
While a worktree view is shown, the system SHALL refresh the emulator and simulator states of a checked project at most every 10 seconds rather than on every 3-second check. It SHALL refresh them right away after the user starts or stops a device from ShogunAi and whenever the project's worktree list reloads.

#### Scenario: Nothing changes
- **WHEN** a project that declares the emulator provider is checked every 3 seconds for 30 seconds without changes
- **THEN** its emulator states are refreshed at most 3 times in that period

#### Scenario: Emulator stopped outside ShogunAi
- **WHEN** a worktree's emulator is closed from its own window
- **THEN** its row and detail show it as stopped within about 10 seconds

#### Scenario: Start from ShogunAi
- **WHEN** the user starts a worktree's emulator and it finishes booting
- **THEN** its row and detail show it as running as soon as the start completes, without waiting for the next device refresh

#### Scenario: Worktree list reloads
- **WHEN** a project's Git metadata changes and its worktree list reloads
- **THEN** its device states are refreshed with that reload

### Requirement: Light refresh when the window regains focus
When the main window regains focus, the system SHALL read again the status of every listed worktree of the shown projects and detect running agents once for all of them, without listing the worktrees again and without reading disk usage, ports or devices. A project SHALL be skipped while it is busy (loading, creating, removing or cleaning up) or when it was loaded or refreshed less than 5 seconds earlier.

#### Scenario: File edited in a terminal
- **WHEN** a file is edited, without staging it, in a listed worktree while the app is in the background, and the user focuses the app
- **THEN** that worktree's uncommitted changes count is updated, and no worktree's size is measured again

#### Scenario: Quick switching between windows
- **WHEN** the window regains focus twice within 5 seconds
- **THEN** the statuses are read again only once

#### Scenario: Busy project
- **WHEN** the window regains focus while a worktree is being created in a shown project
- **THEN** that project isn't refreshed and the other shown projects are
