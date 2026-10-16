## ADDED Requirements

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
When `daemonMaxHeap` is declared, `GRADLE_OPTS` SHALL set `org.gradle.jvmargs` to the effective JVM arguments with their heap limit replaced by the declared one, or added when they have none. The effective arguments are those of the user's Gradle `gradle.properties`, else of the repository's, else none.

#### Scenario: Repository arguments kept
- **WHEN** `daemonMaxHeap` is `2g` and the repository's `gradle.properties` sets `org.gradle.jvmargs=-Xmx6g -Dfile.encoding=UTF-8`
- **THEN** the daemon arguments in `GRADLE_OPTS` are `-Xmx2g -Dfile.encoding=UTF-8`

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

### Requirement: Gradle settings on the worktree screen
The "Gradle daemons" section SHALL state the declared settings (daemon heap, idle timeout, Kotlin daemon heap) and that they apply to builds started with the worktree's environment. Without settings, it SHALL NOT show them.

#### Scenario: Settings shown
- **WHEN** the repository declares `{ "gradle": { "daemonMaxHeap": "2g", "idleTimeoutMinutes": 30 } }`
- **THEN** the section shows a 2g daemon heap and a 30-minute idle timeout for worktree builds

#### Scenario: No settings
- **WHEN** the repository declares `{ "gradle": {} }`
- **THEN** the section shows no settings line
