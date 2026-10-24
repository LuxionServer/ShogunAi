# Spec Delta

## MODIFIED Requirements

### Requirement: Fail clearly when nothing is idle
When a pool is at its limit and none of its resources is releasable, acquiring SHALL fail without stopping anything, with an error that states the limit and names, for each resource in use, the project and worktree that holds it, its state, and when its lease expires or that its agent is working, says how many worktrees are waiting for the pool when any are, and says that waiting with `--wait`, stopping one or raising the limit in Preferences frees capacity.

#### Scenario: All emulators running
- **WHEN** the emulator limit is 2, the emulators of `api` · `TASK-1` and `main-repo` · `TASK-2` are running with valid leases and the user starts a third
- **THEN** the start fails, nothing is stopped, and the error lists `api` · `TASK-1` and `main-repo` · `TASK-2` as running with their lease expiry and points to `--wait`, stopping one or to Preferences

#### Scenario: Stopped but leased
- **WHEN** the limit is 1 and the only emulator is stopped with a valid lease
- **THEN** the start fails and the error says when that lease expires

#### Scenario: Others waiting
- **WHEN** the emulator pool is full and two worktrees are waiting for it, and the user starts another worktree's emulator without `--wait`
- **THEN** the start fails and the error says two worktrees are waiting
