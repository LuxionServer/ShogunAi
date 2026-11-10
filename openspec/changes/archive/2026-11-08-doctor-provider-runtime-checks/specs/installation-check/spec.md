## MODIFIED Requirements

### Requirement: Run the installation check without changing anything
The system SHALL offer an installation check, as `shogun doctor` in the CLI and as "Check Installation…" in the desktop app's Help menu. It SHALL run every check in this specification, in the order listed, and SHALL NOT write, create or delete any file, install anything or start any program other than to read a version, to list what is installed (Android Virtual Devices, simulator runtimes, a repository's worktrees) or to ask whether a daemon is running (`docker info`); it SHALL NOT start a daemon.

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
For each registered project whose base folder exists, the check SHALL read the providers its repository declares and report one check per tool they need: the Gradle wrapper or `gradle` for `gradle`, followed by the Java runtime Gradle uses; the Maven wrapper or `mvn` for `maven`, followed by the Java runtime Maven uses; the Android SDK and an AVD for `emulator`, the simulators and an iOS runtime for `ios-simulator`, Xcode for `xcode` and `pod` when it declares CocoaPods, `docker compose` for `docker-compose`, followed by Docker's daemon; and `flutter` (or `fvm`) for `flutter`. The Java runtime check SHALL be reported only when the Gradle or Maven wrapper or command was found, and the Docker daemon check only when `docker compose version` ran.

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

### Requirement: Read the Gradle and Maven versions
The Gradle and Maven checks SHALL name the version the project builds with: the version in the wrapper's distribution URL (`gradle/wrapper/gradle-wrapper.properties` for `gradlew`, `.mvn/wrapper/maven-wrapper.properties` for `mvnw`) when the wrapper is used, else the version printed by `gradle --version` or `mvn --version`. A version that can't be read SHALL NOT change the check's state; the check SHALL then say nothing about the version.

#### Scenario: Gradle wrapper version
- **WHEN** a project has `gradlew` and its `gradle-wrapper.properties` has `distributionUrl=https\://services.gradle.org/distributions/gradle-8.10.2-bin.zip`
- **THEN** the Gradle check passes, naming the wrapper and Gradle 8.10.2

#### Scenario: Maven on the PATH
- **WHEN** a project has no `mvnw` and `mvn --version` prints `Apache Maven 3.9.9`
- **THEN** the Maven check passes, naming `mvn` and Maven 3.9.9

#### Scenario: No wrapper properties
- **WHEN** a project has `mvnw` but no `.mvn/wrapper/maven-wrapper.properties`
- **THEN** the Maven check passes without naming a version

### Requirement: Check the Java runtime behind Gradle and Maven
The Java runtime check SHALL use `bin/java` (`bin\java.exe` on Windows) under `JAVA_HOME` when it is set and not blank, else `java` on the `PATH`, as the wrappers do. It SHALL run `java -version`, read the major version, and pass, naming it and where `java` was found, when Java runs and suits the tool's version. It SHALL warn, with a fix, when no Java is found, when `JAVA_HOME` has no `java`, or when `java -version` fails, naming its output.

#### Scenario: Java for Gradle passes
- **WHEN** a project's wrapper uses Gradle 8.10.2, `JAVA_HOME` isn't set and `java -version` on the `PATH` prints `openjdk version "21.0.5"`
- **THEN** the Java check for Gradle passes, naming Java 21 and where `java` was found

#### Scenario: Old Java version format
- **WHEN** `java -version` prints `java version "1.8.0_412"` and the project's Maven is 3.9.9
- **THEN** the Java check for Maven passes with Java 8

#### Scenario: JAVA_HOME without java
- **WHEN** `JAVA_HOME` is `/opt/jdk-gone` and `/opt/jdk-gone/bin/java` doesn't exist
- **THEN** the Java check warns, naming `JAVA_HOME` and the folder, with the fix to correct or unset it, and doesn't fall back to the `PATH`

#### Scenario: JAVA_HOME on Windows
- **WHEN** on Windows `JAVA_HOME` is `C:\jdk-21` and `C:\jdk-21\bin\java.exe` exists
- **THEN** the Java check runs `C:\jdk-21\bin\java.exe -version`

#### Scenario: macOS stub without a JDK
- **WHEN** `java` on the `PATH` is `/usr/bin/java` and `java -version` exits with 1 printing `Unable to locate a Java Runtime`
- **THEN** the Java check warns that Java doesn't work, with that output, and the fix to install a JDK

#### Scenario: No Java
- **WHEN** `JAVA_HOME` isn't set and no `java` is on the `PATH`
- **THEN** the Java check warns that no Java was found, with the fix to install a JDK

### Requirement: Java that suits the Gradle or Maven version
The Java runtime check SHALL warn when Java is older than the tool version needs: Gradle 9 or later needs Java 17 and older Gradle Java 8; Maven 4 or later needs Java 17, Maven 3.9 Java 8 and older Maven Java 7. The fix SHALL be to install that Java or newer and point `JAVA_HOME` at it. When the tool's version is unknown, the check SHALL only require that Java runs.

#### Scenario: Java too old for Gradle 9
- **WHEN** a project's wrapper uses Gradle 9.1.0 and `java -version` prints `openjdk version "11.0.24"`
- **THEN** the Java check for Gradle warns that Gradle 9.1.0 needs Java 17, with the fix to install Java 17 or newer and point `JAVA_HOME` at it

#### Scenario: Java too old for Maven 4
- **WHEN** a project's Maven is 4.0.0 and Java is 11
- **THEN** the Java check for Maven warns that Maven 4.0.0 needs Java 17

#### Scenario: Unknown tool version
- **WHEN** the Gradle version can't be read and Java 11 runs
- **THEN** the Java check for Gradle passes

### Requirement: Gradle that supports the Java version
The Java runtime check for Gradle SHALL warn when Gradle's version is older than the first Gradle that runs on that Java: 4.3 for Java 9, 4.7 for 10, 5.0 for 11, 5.4 for 12, 6.0 for 13, 6.3 for 14, 6.7 for 15, 7.0 for 16, 7.3 for 17, 7.5 for 18, 7.6 for 19, 8.3 for 20, 8.5 for 21, 8.8 for 22, 8.10 for 23, 8.14 for 24 and 9.1 for 25. The fix SHALL be to update the Gradle wrapper to that version or newer, or to point `JAVA_HOME` at an older Java. A Java newer than 25 SHALL NOT be judged.

#### Scenario: Java too new for an old Gradle
- **WHEN** a project's wrapper uses Gradle 8.4 and Java is 21
- **THEN** the Java check for Gradle warns that Java 21 needs Gradle 8.5, with the fix to update the wrapper or use an older Java

#### Scenario: Java newer than the table
- **WHEN** a project's wrapper uses Gradle 9.1.0 and Java is 27
- **THEN** the Java check for Gradle passes

### Requirement: Check Docker Compose's version
The Docker Compose check SHALL read Compose's version from `docker compose version`, name it, and warn when it is older than 2.0, the first version with the `docker compose ls` ShogunAi uses to list Compose projects, with the fix to update Docker or its Compose plugin. A version it can't read SHALL NOT change its state.

#### Scenario: Compose version passes
- **WHEN** `docker compose version` prints `Docker Compose version v2.29.1`
- **THEN** the Docker Compose check passes, naming 2.29.1

#### Scenario: Compose too old
- **WHEN** `docker compose version` prints `docker-compose version 1.29.2, build 5becea4c`
- **THEN** the Docker Compose check warns that 1.29.2 is older than 2.0, with the fix to update Docker's Compose plugin

### Requirement: Check Docker's daemon
The Docker daemon check SHALL run `docker info` and pass, naming the daemon's version, when it succeeds. Otherwise it SHALL warn that Docker's daemon isn't running, with its output and a fix for the operating system: on macOS and Windows, start Docker Desktop or the Docker engine in use; on Linux, start the `docker` service (`sudo systemctl start docker`) or Docker Desktop.

#### Scenario: Daemon stopped on macOS
- **WHEN** on macOS `docker info` fails with `Cannot connect to the Docker daemon at unix:///var/run/docker.sock`
- **THEN** the Docker daemon check warns with that output and the fix to start Docker Desktop or the Docker engine in use, and `shogun doctor` still exits with 0 if nothing else failed

#### Scenario: Daemon stopped on Linux
- **WHEN** on Linux `docker info` fails
- **THEN** the fix is to start the `docker` service with `sudo systemctl start docker`, or Docker Desktop

#### Scenario: Daemon running
- **WHEN** `docker info` prints server version `27.3.1`
- **THEN** the Docker daemon check passes, naming 27.3.1
