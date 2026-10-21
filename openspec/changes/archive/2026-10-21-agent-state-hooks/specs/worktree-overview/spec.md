# Spec Delta

## ADDED Requirements

### Requirement: Show the state Claude Code's hooks report
When a Claude process runs in a worktree and a live session of that worktree has a recorded state, the agent SHALL carry that state. A record SHALL be live while its session's Claude process runs. A blocked or done record whose session's transcript was written after it SHALL read as working. When Herdr reports the same agent, Herdr's state SHALL be used.

#### Scenario: Blocked outside Herdr
- **WHEN** Claude runs in a worktree outside Herdr and its session was recorded as blocked
- **THEN** the worktree's row and detail show Claude as blocked, and the worktree is under "Needs you"

#### Scenario: Permission granted
- **WHEN** a session recorded as blocked writes to its transcript afterwards
- **THEN** Claude is shown as working

#### Scenario: Stale record
- **WHEN** a worktree has a record of a session whose Claude process no longer runs
- **THEN** the record is ignored, and no agent is shown unless a process or Herdr reports one

#### Scenario: Herdr also reports it
- **WHEN** Herdr reports Claude as working and the hook record says done
- **THEN** Claude is shown as working
