# worktree-auto-refresh Specification

## Purpose
Keeps the worktree screen up to date with what happens in the repository while it is open, so worktrees, commits and agents started from terminals show up without reloading by hand.

## Requirements

### Requirement: Reload when the repository's Git metadata changes
While a worktree view is shown, the system SHALL check the Git metadata of every project shown in it every 3 seconds and reload that project's worktree list, statuses and overview when its metadata differs from the previous check. Projects whose metadata didn't change SHALL NOT be reloaded. The watched metadata is each main repository's `HEAD`, `index`, `packed-refs`, `FETCH_HEAD`, everything under `refs`, and everything under `worktrees`, which holds each linked worktree's `HEAD`, `index` and lock.

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
The system SHALL detect running agents again on every check, once for the worktrees of all shown projects, even when no Git metadata changed, so agents starting or ending in a worktree are shown without a full reload.

#### Scenario: Agent starts
- **WHEN** an agent starts in a listed worktree and no Git metadata changes
- **THEN** the worktree shows the agent as running within a few seconds

#### Scenario: One detection for all projects
- **WHEN** "All worktrees" shows three projects and a check runs
- **THEN** running processes are inspected once, and each agent is attributed to its worktree in any of the three projects

### Requirement: Watch only while it is useful
The system SHALL only check the projects shown in the current worktree view, SHALL stop checking a project when no shown view includes it, and SHALL skip a project's check while its list is loading or a create, remove or clean up action is running on it.

#### Scenario: Leaving the screen
- **WHEN** the user switches from "All worktrees" to the view filtered to `api`
- **THEN** only `api` keeps being checked

#### Scenario: Action in progress
- **WHEN** a check is due while a worktree is being created, removed or cleaned up in a project
- **THEN** that project's check is skipped, the other projects are checked, and the next check runs normally
