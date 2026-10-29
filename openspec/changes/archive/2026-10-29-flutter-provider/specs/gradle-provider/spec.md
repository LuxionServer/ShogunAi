# Spec Delta

## MODIFIED Requirements

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
