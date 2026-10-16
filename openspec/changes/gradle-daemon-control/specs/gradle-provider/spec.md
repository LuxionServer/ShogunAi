# Spec Delta

## Purpose

Defines the Gradle provider: how ShogunAi finds the Gradle and Kotlin daemons running on the machine, ties each Gradle daemon to the worktree of its last build, stops idle daemons without interrupting builds, and tells a Gradle repository whether worktrees can share build outputs.

## ADDED Requirements

### Requirement: Detect Gradle and Kotlin daemons
The system SHALL list the Gradle daemons and Kotlin compile daemons running on the machine, each with its process id and resident memory. Each Gradle daemon SHALL also have its Gradle version. Other Java processes SHALL NOT be listed.

#### Scenario: Daemons running
- **WHEN** a Gradle 9.6.1 daemon and a Kotlin compile daemon are running
- **THEN** both are listed with their process id and memory, and the Gradle one with version 9.6.1

#### Scenario: Unrelated Java process
- **WHEN** an IDE or another Java application is running but no daemon is
- **THEN** no daemon is listed

#### Scenario: Process inspection unavailable
- **WHEN** listing processes fails
- **THEN** the daemon list is reported as unavailable and nothing else is affected

### Requirement: Know whether a Gradle daemon is busy
The system SHALL report each Gradle daemon as busy, idle (with the time it became idle) or unknown, from the most recent state the daemon recorded. A daemon whose state can't be read SHALL be reported as unknown.

#### Scenario: Idle daemon
- **WHEN** a daemon's last recorded state change is becoming idle at 12:57
- **THEN** it's reported as idle since 12:57

#### Scenario: Busy daemon
- **WHEN** a daemon's last recorded state change is becoming busy
- **THEN** it's reported as busy

#### Scenario: State unreadable
- **WHEN** a daemon's state record is missing or can't be read
- **THEN** it's reported as unknown

### Requirement: Attribute a Gradle daemon to a worktree
The system SHALL tie each Gradle daemon to the directory of the last build it received, and, for a given repository, report it as belonging to one of its worktrees, to a worktree folder that no longer exists, or to another project. Kotlin daemons SHALL NOT be attributed.

#### Scenario: Build in a worktree
- **WHEN** a daemon's last build ran in `<worktrees>/task-123`, a worktree of the repository
- **THEN** the daemon is shown as belonging to that worktree

#### Scenario: Build in a nested worktree
- **WHEN** a daemon's last build ran in `<repository>/.claude/worktrees/task`, nested in the main worktree's folder
- **THEN** the daemon belongs to the nested worktree, not to the main one

#### Scenario: Worktree removed
- **WHEN** a daemon's last build ran in a folder that is no longer a worktree of the repository and no longer exists
- **THEN** the daemon is shown as belonging to a removed worktree

#### Scenario: Another project
- **WHEN** a daemon's last build ran outside every worktree of the repository and that folder still exists
- **THEN** the daemon is shown as belonging to another project

### Requirement: Stop idle daemons
The system SHALL stop a Gradle daemon only if it is idle when the stop is requested, checking its state again just before stopping it. It SHALL stop a Kotlin daemon only while no Gradle daemon is busy or unknown. A daemon that can't be stopped SHALL be reported with the reason, without stopping the rest.

#### Scenario: Idle Gradle daemon
- **WHEN** the user stops an idle Gradle daemon
- **THEN** its process ends and it disappears from the list

#### Scenario: Busy Gradle daemon
- **WHEN** the user asks to stop a Gradle daemon that is busy or unknown
- **THEN** it isn't stopped and the result says it's busy or its state is unknown

#### Scenario: Became busy in the meantime
- **WHEN** a daemon was idle when listed but is busy when the stop runs
- **THEN** it isn't stopped and the result says it's busy

#### Scenario: Kotlin daemon while a build runs
- **WHEN** the user asks to stop a Kotlin daemon while a Gradle daemon is busy
- **THEN** the Kotlin daemon isn't stopped and the result says a build is running

#### Scenario: Stop all idle
- **WHEN** the user stops all idle daemons and one Gradle daemon is busy
- **THEN** every idle Gradle daemon is stopped, the busy one and the Kotlin daemons are kept, and the result lists what was stopped and what was kept

### Requirement: Build cache notice
For a repository that declares the Gradle provider, the system SHALL report whether Gradle's build cache is enabled, that is, whether `org.gradle.caching=true` is set in the repository's `gradle.properties` or in the user's Gradle `gradle.properties`, and explain that enabling it lets a new worktree reuse outputs built in other worktrees.

#### Scenario: Cache enabled in the repository
- **WHEN** the repository's `gradle.properties` contains `org.gradle.caching=true`
- **THEN** no notice is shown

#### Scenario: Cache enabled for the user
- **WHEN** the repository doesn't set it but the user's Gradle `gradle.properties` contains `org.gradle.caching=true`
- **THEN** no notice is shown

#### Scenario: Cache not enabled
- **WHEN** neither file sets `org.gradle.caching=true`
- **THEN** a notice says the build cache is off and how to turn it on

### Requirement: Gradle daemons on the worktree screen
The worktree screen of a project whose repository declares the Gradle provider SHALL show a "Gradle daemons" section listing every daemon with its kind, version, memory, state and worktree, the total memory, the build cache notice when it applies, and actions to stop one idle daemon or all idle ones. It SHALL refresh together with the worktree list. Projects without the provider SHALL NOT show it.

#### Scenario: Gradle project
- **WHEN** the user opens the worktree screen of a project whose `.shogunai.json` declares `"providers": { "gradle": {} }`
- **THEN** the "Gradle daemons" section lists the running daemons and their total memory

#### Scenario: Project without the provider
- **WHEN** the repository doesn't declare the Gradle provider
- **THEN** the worktree screen has no "Gradle daemons" section

#### Scenario: Stop action on a busy daemon
- **WHEN** a Gradle daemon is busy or its state is unknown
- **THEN** its stop action is disabled

#### Scenario: Stop result
- **WHEN** the user stops all idle daemons
- **THEN** the screen reports how many were stopped and kept, and the section is refreshed
