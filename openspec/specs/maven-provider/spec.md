# maven-provider Specification

## Purpose
Defines the Maven provider: how ShogunAi bounds the memory of Maven builds started in worktrees, shows the Maven daemons running on the machine with their state and the worktree each works in, and stops the idle ones on request.

## Requirements

### Requirement: Bound the memory of Maven builds
When the repository declares the `maven` provider with a `mavenMaxHeap` option, the system SHALL add `MAVEN_OPTS` to every worktree's environment whose value is that heap as `-Xmx`. Without the option no variable SHALL be added. A variable with the same name declared in `env` SHALL take precedence.

#### Scenario: Heap bound
- **WHEN** the repository declares `"maven": { "mavenMaxHeap": "1g" }`
- **THEN** every worktree's environment has `MAVEN_OPTS` containing `-Xmx1g`

#### Scenario: No option
- **WHEN** the repository declares `"maven": {}`
- **THEN** no `MAVEN_OPTS` is added

#### Scenario: Declared variable wins
- **WHEN** `env` declares `MAVEN_OPTS` and the provider has `mavenMaxHeap`
- **THEN** the worktree's environment has the declared value

#### Scenario: Invalid size
- **WHEN** `mavenMaxHeap` is `1 GB`
- **THEN** the file is reported as invalid with an error that names `.shogunai.json` and the field

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

### Requirement: Attribute a Maven daemon to a worktree
For a given repository, the system SHALL report each Maven daemon as belonging to the worktree that contains its working directory (the deepest one when worktrees are nested), to a removed worktree when that folder no longer exists, or to another project. A daemon whose working directory can't be read SHALL be reported as unknown.

#### Scenario: Daemon in a worktree
- **WHEN** a daemon's working directory is `<worktrees>/task-123/service`
- **THEN** it belongs to the worktree `task-123`

#### Scenario: Worktree removed
- **WHEN** a daemon's working directory no longer exists and was inside a worktrees folder of the repository
- **THEN** it's shown as belonging to a removed worktree

#### Scenario: Another project
- **WHEN** a daemon's working directory is outside every worktree of the repository and still exists
- **THEN** it's shown as belonging to another project

#### Scenario: Directory unreadable
- **WHEN** a daemon's working directory can't be determined
- **THEN** it's shown as unknown

### Requirement: Provider only for repositories that declare it
Without the `maven` declaration, the system SHALL NOT list Maven daemons, show anything Maven-related or add variables for Maven.

#### Scenario: Repository without the provider
- **WHEN** a repository doesn't declare `"maven"` in `providers`
- **THEN** no Maven daemon is read for it and its worktree screen has no Maven section

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
