# Spec Delta

## ADDED Requirements

### Requirement: Identify the build that holds a busy daemon
For each busy Gradle daemon, the system SHALL report the build it is running: the build's directory and when it started, from the daemon's own record. When it can find the client process connected to the daemon for that build, it SHALL also report the client's process id and the tasks it asked for. When that process descends from the recorded agent session of a worktree, it SHALL also report that session and its worktree. Idle and unknown daemons SHALL have no holding build.

#### Scenario: Build started by an agent session
- **WHEN** a daemon is busy with `./gradlew benchRun`, started at 21:02 in `<worktrees>/api/task-123` by a process whose ancestor is the agent of a session recorded in `<worktrees>/apps/task-123`
- **THEN** the daemon's holding build has directory `<worktrees>/api/task-123`, start 21:02, the client's process id, the task `benchRun`, and that session in the `apps` worktree

#### Scenario: Client not traceable to a session
- **WHEN** a busy daemon's client was started from a terminal that isn't an agent session
- **THEN** the holding build has the directory, start, client process id and tasks, and no session

#### Scenario: Connection lookup unavailable
- **WHEN** the connection between the daemon and its client can't be inspected on this machine
- **THEN** the holding build has the directory and start only, and listing the daemons succeeds

#### Scenario: Idle daemon
- **WHEN** a daemon is idle
- **THEN** it has no holding build
