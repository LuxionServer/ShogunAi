# gradle-provider Specification

## Purpose
Defines the Gradle provider: how ShogunAi finds the Gradle and Kotlin daemons running on the machine, ties each Gradle daemon to the worktree of its last build, stops idle daemons without interrupting builds, and tells a Gradle repository whether worktrees can share build outputs.

## Requirements

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
For a repository that declares the Gradle provider, the system SHALL report whether Gradle's build cache is enabled, that is, whether `org.gradle.caching=true` is set in the repository's `gradle.properties`, in the `gradle.properties` of a Gradle build in a direct subfolder when the main folder isn't one, or in the user's Gradle `gradle.properties`, and explain that enabling it lets a new worktree reuse outputs built in other worktrees.

#### Scenario: Cache enabled in the repository
- **WHEN** the repository's `gradle.properties` contains `org.gradle.caching=true`
- **THEN** no notice is shown

#### Scenario: Cache enabled in a nested build
- **WHEN** the repository's Gradle build is `android/` and `android/gradle.properties` contains `org.gradle.caching=true`
- **THEN** no notice is shown

#### Scenario: Cache enabled for the user
- **WHEN** the repository doesn't set it but the user's Gradle `gradle.properties` contains `org.gradle.caching=true`
- **THEN** no notice is shown

#### Scenario: Cache not enabled
- **WHEN** neither file sets `org.gradle.caching=true`
- **THEN** a notice says the build cache is off and how to turn it on

### Requirement: Gradle settings in the worktree environment
When the Gradle provider declares at least one setting, every worktree's environment, the main worktree's included, SHALL contain `GRADLE_OPTS` with the Gradle system properties for those settings and nothing else, identical for every worktree of the repository. Without settings, the provider SHALL add no variable. A `GRADLE_OPTS` declared in `env` SHALL take precedence.

#### Scenario: Settings declared
- **WHEN** the repository declares `{ "gradle": { "idleTimeoutMinutes": 30 } }` and a worktree's environment is resolved
- **THEN** it contains `GRADLE_OPTS` with `-Dorg.gradle.daemon.idletimeout=1800000`

#### Scenario: Same value in every worktree
- **WHEN** two worktrees and the main worktree of the repository resolve their environment
- **THEN** all three have the same `GRADLE_OPTS`

#### Scenario: No settings
- **WHEN** the repository declares `{ "gradle": {} }`
- **THEN** the provider adds no variable to the environment

#### Scenario: Declared variable wins
- **WHEN** the repository declares Gradle settings and `env` sets `GRADLE_OPTS`
- **THEN** the worktree's `GRADLE_OPTS` is the one declared in `env`

### Requirement: Daemon heap keeps the other JVM arguments
When `daemonMaxHeap` is declared, `GRADLE_OPTS` SHALL set `org.gradle.jvmargs` to the effective JVM arguments with their heap limit replaced by the declared one, or added when they have none. The effective arguments are those of the user's Gradle `gradle.properties`, else of the repository's, else of the first Gradle build in a direct subfolder, in name order, when the main folder isn't one, else none.

#### Scenario: Repository arguments kept
- **WHEN** `daemonMaxHeap` is `2g` and the repository's `gradle.properties` sets `org.gradle.jvmargs=-Xmx6g -Dfile.encoding=UTF-8`
- **THEN** the daemon arguments in `GRADLE_OPTS` are `-Xmx2g -Dfile.encoding=UTF-8`

#### Scenario: Nested build arguments kept
- **WHEN** `daemonMaxHeap` is `2g`, the repository's Gradle build is `android/` and `android/gradle.properties` sets `org.gradle.jvmargs=-Xmx8G -XX:MaxMetaspaceSize=4G`
- **THEN** the daemon arguments in `GRADLE_OPTS` are `-Xmx2g -XX:MaxMetaspaceSize=4G`

#### Scenario: User arguments take precedence
- **WHEN** both the user's and the repository's `gradle.properties` set `org.gradle.jvmargs`
- **THEN** the arguments in `GRADLE_OPTS` start from the user's

#### Scenario: No arguments anywhere
- **WHEN** `daemonMaxHeap` is `2g` and neither file sets `org.gradle.jvmargs`
- **THEN** the daemon arguments in `GRADLE_OPTS` are `-Xmx2g`

### Requirement: Kotlin daemon heap
When `kotlinDaemonMaxHeap` is declared, `GRADLE_OPTS` SHALL set the `kotlin.daemon.jvmargs` project property to the declared heap limit.

#### Scenario: Kotlin heap declared
- **WHEN** `kotlinDaemonMaxHeap` is `1g`
- **THEN** `GRADLE_OPTS` sets the project property `kotlin.daemon.jvmargs` to `-Xmx1g`

### Requirement: Builds started from the worktree's shell use the settings
A Gradle build started with the wrapper from a shell that has the worktree's environment SHALL run on a daemon with the declared heap and idle timeout, even when the repository's or the user's `gradle.properties` sets other values.

#### Scenario: Override of gradle.properties
- **WHEN** the repository sets `org.gradle.jvmargs=-Xmx6g`, `daemonMaxHeap` is `2g`, and an agent runs `./gradlew build` after `eval "$(shogun env)"`
- **THEN** the build's daemon runs with a 2g heap

#### Scenario: Daemon reused across worktrees
- **WHEN** a build finished in one worktree and its daemon is idle, and an agent then builds in another worktree of the same repository
- **THEN** the second build can reuse that daemon

### Requirement: Trim idle daemons to the pool limit
The desktop app SHALL check the Gradle daemon pool each time it refreshes the machine summaries and, when more Gradle daemons run than the limit, stop idle ones as the Gradle daemon pool defines. Each daemon stopped this way SHALL be reported in the Gradle daemons view with the reason. Without the desktop app running, `shogun pool trim` SHALL do the same on demand.

#### Scenario: App running
- **WHEN** the Gradle limit is 2, the app is running and an agent's build leaves a third Gradle daemon idle
- **THEN** within the next summary refresh the longest-idle daemon is stopped and the Gradle daemons view says it was stopped to stay within the limit

#### Scenario: One per worktree
- **WHEN** the profile is One per worktree
- **THEN** no daemon is stopped to honour a limit
