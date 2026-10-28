## ADDED Requirements

### Requirement: Know whether a Maven daemon is busy
The system SHALL report each Maven daemon as busy, idle (with the time it became idle) or unknown, from the most recent state change the daemon recorded in its own log. A readable log with no recorded state change SHALL be reported as busy, because the daemon restarts its log when a build starts and is created busy. A daemon whose log can't be found or read SHALL be reported as unknown.

#### Scenario: Idle daemon
- **WHEN** a daemon's last recorded state change is becoming idle at 12:57
- **THEN** it's reported as idle since 12:57

#### Scenario: Busy daemon
- **WHEN** a daemon's last recorded state change is becoming busy, cancelled or broken
- **THEN** it's reported as busy

#### Scenario: Log restarted by a running build
- **WHEN** a daemon's log has no recorded state change, as while a build runs
- **THEN** it's reported as busy

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

### Requirement: Detect Maven daemons
The system SHALL list the Maven daemons (mvnd) running on the machine, each with its process id and resident memory, whatever main class the daemon's Java process runs. Other Java processes, including plain `mvn` builds and the `mvnd` client, SHALL NOT be listed.

#### Scenario: Daemons running
- **WHEN** two mvnd daemons are running
- **THEN** both are listed with process id and memory

#### Scenario: Daemon started through the Classworlds launcher
- **WHEN** an mvnd daemon runs with `org.codehaus.plexus.classworlds.launcher.Launcher` as main class and its `mvnd.id` on the command line
- **THEN** it is listed

#### Scenario: Unrelated Java process
- **WHEN** an IDE, a plain `mvn` build or the `mvnd` client is running but no daemon is
- **THEN** no daemon is listed

#### Scenario: Process inspection unavailable
- **WHEN** listing processes fails
- **THEN** the daemon list is reported as unavailable and nothing else is affected

## REMOVED Requirements

### Requirement: Maven daemons are never stopped
**Reason**: The state of a Maven daemon can now be read reliably from the daemon's own log, so idle daemons can be stopped with the same safeguards as Gradle daemons.
**Migration**: None. Daemons are only stopped on an explicit user action (stop in the Machine section, `shogun maven stop`, or the cleanup option).
