## MODIFIED Requirements

### Requirement: Run the installation check without changing anything
The system SHALL offer an installation check, as `shogun doctor` in the CLI and as "Check Installation…" in the desktop app's Help menu, that runs every check in this specification in the order listed. It SHALL NOT write, create or delete any file, install anything, start a daemon, or start any program other than to read a version, to list what is installed (Android Virtual Devices, simulator runtimes, a repository's worktrees) or to ask whether a daemon is running (`docker info`).

#### Scenario: Nothing changes
- **WHEN** the installation check runs on a machine where the hooks are outdated and a project's local file is missing
- **THEN** both are reported and no settings file, project, worktree or local file is modified

#### Scenario: Missing multirepo folder isn't forgotten
- **WHEN** a known multirepo's folder no longer has its declaration
- **THEN** the check reports it and the multirepo stays in ShogunAi's list of known multirepos

#### Scenario: Docker isn't started
- **WHEN** a project declares `docker-compose` and Docker's daemon isn't running
- **THEN** the check reports it and Docker's daemon is still not running afterwards

### Requirement: Check the tools of each project's providers
For each registered project whose base folder exists, the check SHALL read the providers its repository declares and report one check per tool they need: the Android SDK and an AVD for `emulator`, the simulators and an iOS runtime for `ios-simulator`, Xcode for `xcode` and `pod` when it declares CocoaPods, and `flutter` (or `fvm`) for `flutter`. The tools of `gradle`, `maven` and `docker-compose` are checked as their own requirements say.

#### Scenario: Tools found
- **WHEN** a project declares `docker-compose`, `docker compose version` runs and `docker info` succeeds
- **THEN** a check for that project's Docker Compose passes, naming where `docker` was found and its version, followed by a check for Docker's daemon that passes

#### Scenario: No providers
- **WHEN** a project's `.shogunai.json` declares no providers, or the repository has none
- **THEN** no provider tool check is reported for it

#### Scenario: Wrapper in the repository
- **WHEN** a project declares `gradle`, its base folder has `gradlew` and `gradle` isn't on the `PATH`
- **THEN** the Gradle check passes, naming the wrapper

#### Scenario: Tool missing, no runtime check
- **WHEN** a project declares `maven` and neither `mvnw` nor `mvn` is found
- **THEN** the Maven check warns and no Java check is reported for Maven

## ADDED Requirements

### Requirement: Check the build tools and the Java they use
For a project that declares `gradle`, the check SHALL report the Gradle wrapper or `gradle`, followed by the Java runtime Gradle uses. For one that declares `maven`, it SHALL report the Maven wrapper or `mvn`, followed by the Java runtime Maven uses. The Java runtime check SHALL be reported only when the wrapper or command was found.

#### Scenario: Gradle wrapper and its Java
- **WHEN** a project declares `gradle`, its base folder has `gradlew` and `gradle` isn't on the `PATH`
- **THEN** the Gradle check passes, naming the wrapper, and is followed by a check of the Java runtime Gradle uses

#### Scenario: Maven missing
- **WHEN** a project declares `maven` and neither `mvnw` nor `mvn` is found
- **THEN** the Maven check warns and no Java check is reported for Maven

### Requirement: Check Docker Compose and its daemon
For a project that declares `docker-compose`, the check SHALL report `docker compose`, followed by Docker's daemon. The daemon check SHALL be reported only when `docker compose version` ran.

#### Scenario: Compose and daemon
- **WHEN** a project declares `docker-compose`, `docker compose version` runs and `docker info` succeeds
- **THEN** a check for that project's Docker Compose passes, naming where `docker` was found and its version, followed by a check for Docker's daemon that passes

#### Scenario: Compose missing
- **WHEN** a project declares `docker-compose` and `docker` isn't found
- **THEN** the Docker Compose check warns and no check for Docker's daemon is reported
