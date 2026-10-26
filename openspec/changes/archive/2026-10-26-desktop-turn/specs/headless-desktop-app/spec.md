# Spec Delta

## MODIFIED Requirements

### Requirement: Stop the app with its worktree
Removing a worktree, from the command line, the desktop app or Claude Code's `WorktreeRemove` hook, and cleaning it up SHALL first stop its headless app if it is running. A failure to stop it SHALL NOT block the removal; the command line SHALL report it on standard error, and the desktop app SHALL show a notice naming the worktree and the error until the user dismisses it.

#### Scenario: Removed while running
- **WHEN** a worktree whose headless app is running is removed
- **THEN** the app is stopped before the worktree's folder is deleted, and its isolated home is gone with the worktree

#### Scenario: Stop fails on the command line
- **WHEN** `shogun remove` removes a worktree whose headless app can't be stopped
- **THEN** the worktree is removed and standard error says the desktop app couldn't be stopped

#### Scenario: Stop fails in the desktop app
- **WHEN** the user deletes a worktree from the desktop app and its headless app can't be stopped
- **THEN** the worktree is removed and a notice says that worktree's desktop app couldn't be stopped, with the error, until dismissed

#### Scenario: Stop fails while cleaning up
- **WHEN** the user cleans up two worktrees from the desktop app and one's headless app can't be stopped
- **THEN** both are removed and the cleanup's result says that one's desktop app couldn't be stopped
