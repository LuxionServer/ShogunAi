## ADDED Requirements

### Requirement: Release the physical devices of removed worktrees
Removing a worktree, from the desktop app, the command line, cleanup, Claude Code's hook, Herdr or Orca, SHALL release the physical devices it holds and drop its waits for them, without running anything on the devices. A failure to release SHALL NOT fail the removal.

#### Scenario: Removed while holding
- **WHEN** `TASK-1` holds the Pixel 8 and is removed
- **THEN** the Pixel 8 is free and the next waiter for an Android device can acquire it

#### Scenario: Removed while waiting
- **WHEN** `TASK-2` waits for the A55 and is removed
- **THEN** `TASK-2` is no longer in the queue
