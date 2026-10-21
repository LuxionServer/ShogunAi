# maven-provider Specification

## Purpose
Defines the Maven provider: how ShogunAi bounds the memory of Maven builds started in worktrees and shows the Maven daemons running on the machine with the worktree each works in.

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
The system SHALL list the Maven daemons (mvnd) running on the machine, each with its process id and resident memory. Other Java processes, including plain `mvn` builds, SHALL NOT be listed.

#### Scenario: Daemons running
- **WHEN** two mvnd daemons are running
- **THEN** both are listed with process id and memory

#### Scenario: Unrelated Java process
- **WHEN** an IDE or a plain `mvn` build is running but no daemon is
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

### Requirement: Maven daemons are never stopped
The system SHALL NOT stop, kill or restart a Maven daemon, because whether one is busy can't be known reliably. Daemons SHALL only be listed.

#### Scenario: List only
- **WHEN** the user looks at the Maven daemons of a worktree
- **THEN** no control stops a daemon and the cleanup of worktrees leaves them running

### Requirement: Provider only for repositories that declare it
Without the `maven` declaration, the system SHALL NOT list Maven daemons, show anything Maven-related or add variables for Maven.

#### Scenario: Repository without the provider
- **WHEN** a repository doesn't declare `"maven"` in `providers`
- **THEN** no Maven daemon is read for it and its worktree screen has no Maven section
