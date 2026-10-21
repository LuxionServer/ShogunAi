# Spec Delta

## MODIFIED Requirements

### Requirement: Flag inactive worktrees
The system SHALL flag a non-main worktree as inactive, in its row and its detail, when it has commits of its own, no uncommitted changes, no AI agent running in it, and its activity date is older than the inactivity threshold. The threshold SHALL default to 3 days. A worktree without commits of its own SHALL NOT be flagged as inactive.

#### Scenario: Abandoned worktree
- **WHEN** a clean worktree with no running agent had its last commit of its own 5 days ago, with the default threshold
- **THEN** its row and detail show it as inactive

#### Scenario: Recent work
- **WHEN** a clean worktree with no running agent had its last commit of its own 2 days ago, with the default threshold
- **THEN** it isn't flagged as inactive

#### Scenario: Agent running or pending changes
- **WHEN** a worktree whose last commit of its own is 20 days old has uncommitted changes or an agent running in it
- **THEN** it isn't flagged as inactive

#### Scenario: Old base commit in a new worktree
- **WHEN** a worktree was created today from a branch whose last commit is 30 days old, and has no commits of its own
- **THEN** it isn't flagged as inactive and is shown as new

## ADDED Requirements

### Requirement: Configure the inactivity threshold
The app's settings SHALL let the user set the inactivity threshold as a whole number of days from 1 to 90, and SHALL remember it across restarts. A change SHALL apply to every view without reloading the worktrees.

#### Scenario: Longer threshold
- **WHEN** the user sets the threshold to 7 days and a clean worktree's last commit of its own was 5 days ago
- **THEN** the worktree is no longer flagged as inactive, and it moves out of "Inactive" without a reload

#### Scenario: Out of range
- **WHEN** the user enters 0 or 120 days
- **THEN** the value isn't accepted and the setting keeps its previous value
