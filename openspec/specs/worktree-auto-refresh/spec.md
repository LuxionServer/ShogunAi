# worktree-auto-refresh Specification

## Purpose
Keeps the worktree screen up to date with what happens in the repository while it is open, so worktrees, commits and agents started from terminals show up without reloading by hand.

## Requirements

### Requirement: Reload when the repository's Git metadata changes
While the worktree screen is open, the system SHALL check the repository's Git metadata every 3 seconds and reload the worktree list, statuses and overview when it differs from the previous check. The watched metadata is the main repository's `HEAD`, `index`, `packed-refs`, `FETCH_HEAD`, everything under `refs`, and everything under `worktrees`, which holds each linked worktree's `HEAD`, `index` and lock.

#### Scenario: Worktree created from a terminal
- **WHEN** a worktree is added to the repository by another tool while the screen is open
- **THEN** it appears in the list within a few seconds, without the user reloading

#### Scenario: Commit in a worktree
- **WHEN** a commit is made in one of the listed worktrees
- **THEN** its status and last commit date are updated within a few seconds

#### Scenario: Worktree locked or removed
- **WHEN** a listed worktree is locked, unlocked or removed by another tool
- **THEN** the list reflects it within a few seconds

#### Scenario: Nothing changed
- **WHEN** the metadata is the same as in the previous check
- **THEN** the list isn't reloaded

### Requirement: Refresh running agents on every check
The system SHALL detect running agents again on every check, even when the Git metadata didn't change, so agents starting or ending in a worktree are shown without a full reload.

#### Scenario: Agent starts
- **WHEN** an agent starts in a listed worktree and no Git metadata changes
- **THEN** the worktree shows the agent as running within a few seconds

### Requirement: Watch only while it is useful
The system SHALL only check while the worktree screen is shown, SHALL stop when the user leaves it, and SHALL skip a check while the list is loading or a create, remove or clean up action is running.

#### Scenario: Leaving the screen
- **WHEN** the user goes back to the project list
- **THEN** no more checks run for that project

#### Scenario: Action in progress
- **WHEN** a check is due while a worktree is being created, removed or cleaned up
- **THEN** that check is skipped and the next one runs normally
