# Spec Delta

## ADDED Requirements

### Requirement: Build that holds a busy daemon
The Gradle daemons view SHALL show, under the row of each daemon that has a holding build, a line with the tasks its client asked for, how long ago the build started and the agent session that launched it, as identified by the Gradle provider for `shogun gradle`. It SHALL leave out the start and the session when they aren't known, and SHALL say that the daemon is running a build when the tasks aren't known. Daemons without a holding build SHALL show no such line.

#### Scenario: Build launched by an agent session
- **WHEN** a busy daemon runs `:app:assembleDebug`, started three minutes ago by a client that descends from the Claude session `1a2b3c4d-0000` of a saved worktree
- **THEN** under its row the view reads that it is building `:app:assembleDebug`, started 3 minutes ago, in the agent session `claude 1a2b3c4d`

#### Scenario: Client not traceable to a session
- **WHEN** a busy daemon runs `test`, started a minute ago from a terminal that isn't an agent session
- **THEN** the line names the task `test` and when it started, and no session

#### Scenario: Client not found
- **WHEN** a busy daemon's build started two minutes ago but its client can't be found
- **THEN** the line says it is running a build, started 2 minutes ago

#### Scenario: Idle daemon
- **WHEN** a daemon is idle
- **THEN** no build line is shown under its row
