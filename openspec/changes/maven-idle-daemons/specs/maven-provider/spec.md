## ADDED Requirements

### Requirement: Know whether a Maven daemon is busy
The system SHALL report each Maven daemon as busy, idle (with the time it became idle) or unknown, from the most recent state change the daemon recorded in its own log. A daemon whose log can't be found or read, or whose log has no recorded state change, SHALL be reported as unknown. A daemon that has only started and not yet finished its first build SHALL NOT be reported as idle.

#### Scenario: Idle daemon
- **WHEN** a daemon's last recorded state change is becoming idle at 12:57
- **THEN** it's reported as idle since 12:57

#### Scenario: Busy daemon
- **WHEN** a daemon's last recorded state change is becoming busy, cancelled or broken
- **THEN** it's reported as busy

#### Scenario: Fresh daemon
- **WHEN** a daemon has started and its log has no state change yet
- **THEN** it's reported as unknown

#### Scenario: Log unreadable
- **WHEN** a daemon's log is missing, can't be read or its location isn't on its command line
- **THEN** it's reported as unknown and is still listed with its memory and worktree

### Requirement: Stop idle Maven daemons
The system SHALL stop a Maven daemon only if it is idle when the stop is requested, checking its state again just before stopping it, and SHALL let the daemon shut down cleanly rather than kill it forcibly. A daemon that is busy or unknown SHALL be kept and reported with the reason, and a daemon that can't be stopped SHALL be reported with the reason, without stopping the rest.

#### Scenario: Idle daemon
- **WHEN** the user stops an idle Maven daemon
- **THEN** its process ends and it disappears from the list

#### Scenario: Busy or unknown daemon
- **WHEN** the user asks to stop a Maven daemon that is busy or whose state is unknown
- **THEN** it isn't stopped and the result says it's busy or its state is unknown

#### Scenario: Became busy in the meantime
- **WHEN** a daemon was idle when listed but is busy when the stop runs
- **THEN** it isn't stopped and the result says it's busy

#### Scenario: Stop all idle
- **WHEN** the user stops all idle Maven daemons and one is busy
- **THEN** every idle daemon is stopped, the busy one is kept, and the result lists what was stopped and what was kept

## MODIFIED Requirements

### Requirement: Attribute a Maven daemon to a worktree
For a given repository, the system SHALL tie each Maven daemon to the directory of the last build it received, as recorded in its log, or to its process working directory when no build is recorded, and report it as belonging to the worktree that contains that directory (the deepest one when worktrees are nested), to a removed worktree when that folder no longer exists, or to another project. A daemon whose directory can't be determined SHALL be reported as unknown.

#### Scenario: Daemon in a worktree
- **WHEN** a daemon's last build ran in `<worktrees>/task-123/service`
- **THEN** it belongs to the worktree `task-123`

#### Scenario: Daemon reused by another worktree
- **WHEN** a daemon was started from the worktree `task-1` and its last build ran in the worktree `task-2`
- **THEN** it belongs to the worktree `task-2`

#### Scenario: No build recorded
- **WHEN** a daemon's log records no build and its working directory is `<worktrees>/task-123`
- **THEN** it belongs to the worktree `task-123`

#### Scenario: Worktree removed
- **WHEN** a daemon's directory no longer exists and was inside a worktrees folder of the repository
- **THEN** it's shown as belonging to a removed worktree

#### Scenario: Another project
- **WHEN** a daemon's directory is outside every worktree of the repository and still exists
- **THEN** it's shown as belonging to another project

#### Scenario: Directory unreadable
- **WHEN** a daemon's log records no build and its working directory can't be determined
- **THEN** it's shown as unknown

## REMOVED Requirements

### Requirement: Maven daemons are never stopped
**Reason**: The state of a Maven daemon can now be read reliably from the daemon's own log, so idle daemons can be stopped with the same safeguards as Gradle daemons.
**Migration**: None. Daemons are only stopped on an explicit user action (stop in the Machine section, `shogun maven stop`, or the cleanup option).
