# installation-check Specification

## Purpose
Lets a user, or someone helping them, check in one step that ShogunAi's installation and the tools it relies on are usable on this machine, and learn how to fix each problem, from the CLI and from the desktop app.

## Requirements

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

### Requirement: Every check has a state and a fix
Each check SHALL report its state: passed, warning, failed, or not found for an optional tool. A check that didn't pass SHALL say what is wrong and the fix to apply. A failure SHALL be something that stops ShogunAi from working; a warning or a missing optional tool SHALL NOT.

#### Scenario: Passed check
- **WHEN** Git 2.45 is found
- **THEN** the Git check passes, naming the version and where Git was found, with no fix

#### Scenario: Failed check
- **WHEN** a check fails
- **THEN** its result says what is wrong and how to fix it

### Requirement: Check Git
The check SHALL find Git on the host's `PATH` and read its version. It SHALL fail when Git isn't found, when its version can't be read, or when it is older than 2.29, the oldest version with every Git command ShogunAi runs; the fix SHALL be to install or update Git.

#### Scenario: Git too old
- **WHEN** `git --version` prints `git version 2.25.1`
- **THEN** the Git check fails, naming 2.25.1 and 2.29, with the fix to update Git

#### Scenario: Git missing
- **WHEN** no `git` is found on the `PATH`
- **THEN** the Git check fails with the fix to install Git

#### Scenario: Platform suffix
- **WHEN** `git --version` prints `git version 2.39.5 (Apple Git-154)` or `git version 2.45.1.windows.1`
- **THEN** the Git check passes with version 2.39.5 or 2.45.1

### Requirement: Check where the PATH comes from
The check SHALL say where the `PATH` used to find tools comes from: the login shell (passes), the process that started ShogunAi (passes), or a fallback because the login shell couldn't be read (warning, naming the shell, with the fix to make the shell start without errors or prompts within 5 seconds).

#### Scenario: Login shell unreadable
- **WHEN** the desktop app couldn't read the login shell's `PATH`
- **THEN** the check warns that the usual install folders were used instead, naming the shell

#### Scenario: Command line
- **WHEN** `shogun doctor` runs from a terminal
- **THEN** the check passes, saying the `PATH` is the one of the process that started it

### Requirement: Check the shogun command
The check SHALL look for the `shogun` command. It SHALL pass when it is found in a folder of the `PATH`, fail when it isn't found (fix: install it from the desktop app's Settings or the installation guide), fail when it is a launcher installed by the app whose target no longer exists (fix: install it again from Settings), and warn when it was found outside the `PATH` (fix: add its folder to the `PATH`, or restart the terminal on Windows when the folder was just added).

#### Scenario: Not on the PATH
- **WHEN** `shogun` exists in `~/.local/bin` and that folder isn't on the `PATH`
- **THEN** the check warns, naming the folder, with the fix to add it to the `PATH`

#### Scenario: Broken launcher
- **WHEN** the app's launcher link for `shogun` points to an app that was deleted
- **THEN** the check fails with the fix to install the command again from Settings

### Requirement: Check Claude Code's hooks
The check SHALL read ShogunAi's hooks in Claude Code's user settings (`CLAUDE_CONFIG_DIR` or `~/.claude`). It SHALL pass when they are installed and the `shogun` they call is usable, fail when they are installed but `shogun` is missing or a broken launcher (fix: the `shogun` command's fix), warn when they are outdated or missing (fix: `shogun hooks install`), and fail when the settings file can't be parsed (fix: correct the file).

#### Scenario: Hooks call a missing launcher
- **WHEN** the hooks are installed and `shogun` isn't found
- **THEN** the hooks check fails, saying the hooks call a `shogun` that doesn't exist

#### Scenario: Hooks not installed
- **WHEN** the user settings have no ShogunAi hooks
- **THEN** the hooks check warns with the fix `shogun hooks install`

#### Scenario: Invalid settings
- **WHEN** the user settings file isn't valid JSON
- **THEN** the hooks check fails, naming the file and the parse error

### Requirement: Check the agent tools
The check SHALL look for Claude Code, Herdr and Orca where ShogunAi looks for them when it uses them. A tool that is found SHALL pass, naming where; a tool that isn't found SHALL be reported as not found, with the fix to install it, and SHALL NOT fail the check.

#### Scenario: Orca not installed
- **WHEN** Orca isn't installed
- **THEN** Orca is reported as not found with where to get it, and the check doesn't fail because of it

### Requirement: Check each project's local files
For each registered project, the check SHALL fail when its base folder doesn't exist (fix: restore it or remove the project), and otherwise SHALL check the local files that creating a worktree would copy or link, with the same reasons and fixes as the check before creating a worktree. A project whose files are all available SHALL pass, naming how many files were checked.

#### Scenario: Broken link in a project
- **WHEN** a project's `.shogunai.json` copies `*.properties` and its `keys.properties` is a link to a file that doesn't exist
- **THEN** that project's check fails, naming `keys.properties`, the link's missing target, and the fix to restore it, replace the link or stop matching it

#### Scenario: No projects
- **WHEN** no project is registered
- **THEN** no project check is reported

### Requirement: shogun doctor output
`shogun doctor` SHALL print one line per check with its state and summary and, under a check that didn't pass, its fix. With `--json` it SHALL print every check with its id, state, summary and fix. It SHALL exit with 1 when any check failed and with 0 otherwise.

#### Scenario: Warnings only
- **WHEN** the only problem is that the hooks aren't installed
- **THEN** `shogun doctor` prints the warning with its fix and exits with 0

#### Scenario: A failure
- **WHEN** Git isn't found
- **THEN** `shogun doctor` exits with 1

#### Scenario: JSON
- **WHEN** `shogun doctor --json` runs
- **THEN** it prints an object with a `checks` array, each with `id`, `state`, `summary` and `fix`, and `failed` true when any check failed

### Requirement: Check Installation in the desktop app
The desktop app's "Check Installation…" SHALL open a window that runs the check, shows each result with its state, summary and fix in the selected language, offers to run it again, and offers to copy the diagnostic report.

#### Scenario: Open the window
- **WHEN** the user chooses Help › Check Installation… with Spanish selected
- **THEN** a window shows each check's state, summary and fix in Spanish

#### Scenario: Run again
- **WHEN** the user fixes the hooks and chooses to check again
- **THEN** the hooks check passes in the window

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

#### Scenario: Plain Gradle build
- **WHEN** a project declares `gradle`, its wrapper is found, and its repository has no daemon JVM criteria, no `org.gradle.java.home`, no toolchain and no Android Gradle Plugin
- **THEN** only the Gradle check and the Java check for Gradle are reported for it

### Requirement: A missing provider tool is a warning with its fix
A provider tool that is missing or unusable SHALL be a warning, not a failure, naming the project, the tool and what is wrong, with a fix to install or set it up. The check of a project whose providers can't be read SHALL warn with the reason and the fix to correct its `.shogunai.json`.

#### Scenario: Android SDK not found
- **WHEN** a project declares `emulator` and no Android SDK is found
- **THEN** the check warns, naming the places searched, with the fix to install the SDK with Android Studio or set `ANDROID_HOME`, and `shogun doctor` still exits with 0 if nothing else failed

#### Scenario: No Android Virtual Device
- **WHEN** the Android SDK is found and it has no AVD
- **THEN** the check warns, with the fix to create one in Android Studio's Device Manager

#### Scenario: Only the Command Line Tools
- **WHEN** a project declares `xcode` and `xcodebuild -version` fails because only the Command Line Tools are installed
- **THEN** the check warns that Xcode isn't usable, with its output, and the fix to install Xcode and select it with `xcode-select`

#### Scenario: No iOS runtime
- **WHEN** a project declares `ios-simulator` and the simulators have no iOS runtime
- **THEN** the check warns, with the fix to install one from Xcode's Settings › Components

#### Scenario: Not macOS
- **WHEN** a project declares `xcode` or `ios-simulator` on Linux or Windows
- **THEN** the check warns that the provider needs macOS with Xcode

#### Scenario: Docker not running
- **WHEN** a project declares `docker-compose` and `docker` isn't found or `docker compose version` fails
- **THEN** the check warns, with the fix to install Docker with its Compose plugin

### Requirement: Check each multirepo
For each multirepo ShogunAi knows, the check SHALL report one check that passes, naming its repositories and how many groups it has, or that fails when its declaration is missing or invalid or a declared repository isn't a Git repository, and warns when it isn't trusted on this machine. Each problem SHALL be listed with its fix.

#### Scenario: Healthy multirepo
- **WHEN** a trusted multirepo declares `api` and `app`, both Git repositories, with one group
- **THEN** its check passes, naming both repositories and one group

#### Scenario: Repository moved
- **WHEN** a multirepo declares `api` at a folder that doesn't exist
- **THEN** its check fails, naming `api` and the folder, with the fix to restore it or point the alias at its new folder with `shogun multirepo add-repo`

#### Scenario: Invalid declaration
- **WHEN** a multirepo's `.shogunai-multirepo.json` isn't valid
- **THEN** its check fails, naming the file and the problem, with the fix to correct it or, in `shogun doctor`, to forget the multirepo with `shogun multirepo trust <folder> --revoke`

#### Scenario: Declaration gone
- **WHEN** a known multirepo's folder no longer has `.shogunai-multirepo.json`
- **THEN** its check warns that the declaration is gone, with the fix to restore the folder or, in `shogun doctor`, to forget the multirepo with `shogun multirepo trust <folder> --revoke`

#### Scenario: Not trusted
- **WHEN** a multirepo's declaration changed since it was trusted on this machine
- **THEN** its check warns, with the fix `shogun multirepo trust <folder>`

#### Scenario: No multirepos
- **WHEN** ShogunAi knows no multirepo
- **THEN** no multirepo check is reported

### Requirement: Check each multirepo's groups
For each group of a multirepo whose repositories can be read, the check SHALL warn when the group's worktree in one of its repositories points to a folder that no longer exists, naming the group, the repository and the folder, with the fix to restore the folder or run `git worktree prune` in that repository.

#### Scenario: Group worktree folder deleted
- **WHEN** the group `feature/TASK-123` has worktrees in `api` and `app`, and the `app` worktree's folder was deleted
- **THEN** the multirepo's check warns, naming `feature/TASK-123`, `app` and the folder, with the fix

#### Scenario: Repository can't be listed
- **WHEN** listing the worktrees of one of the multirepo's repositories fails
- **THEN** the multirepo's check warns, naming the repository and the error, and the groups of the other repositories are still checked

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

### Requirement: Check the build tools and the Java they use
For a project that declares `gradle`, the check SHALL report the Gradle wrapper or `gradle`, followed by the Java runtime Gradle uses and the Gradle checks below that its build needs. For one that declares `maven`, it SHALL report the Maven wrapper or `mvn`, followed by the Java runtime Maven uses. The checks that follow a wrapper or command SHALL be reported only when it was found.

#### Scenario: Gradle wrapper and its Java
- **WHEN** a project declares `gradle`, its base folder has `gradlew` and `gradle` isn't on the `PATH`
- **THEN** the Gradle check passes, naming the wrapper, and is followed by a check of the Java runtime Gradle uses

#### Scenario: Plain Gradle build
- **WHEN** a project declares `gradle`, its wrapper is found, and its repository has no daemon JVM criteria, no `org.gradle.java.home`, no toolchain and no Android Gradle Plugin
- **THEN** only the Gradle check and the Java check for Gradle are reported for it

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

### Requirement: Find the Java the build chooses for Gradle's daemon
The check of the Java for Gradle's daemon SHALL follow Gradle's precedence: the daemon JVM criteria's `toolchainVersion` in `gradle/gradle-daemon-jvm.properties` when the file exists and the project's Gradle is 8.8 or later or its version is unknown; else `org.gradle.java.home` from `gradle.properties` in `GRADLE_USER_HOME` (default `~/.gradle`), else from the repository's `gradle.properties`. With none of them, the check SHALL NOT be reported.

#### Scenario: Criteria take precedence
- **WHEN** a project uses Gradle 8.10.2, its `gradle/gradle-daemon-jvm.properties` has `toolchainVersion=21` and its `gradle.properties` sets `org.gradle.java.home`
- **THEN** the check of the daemon's Java judges Java 21 from the criteria and ignores `org.gradle.java.home`

#### Scenario: Criteria ignored by an old Gradle
- **WHEN** a project uses Gradle 8.5 and has `gradle/gradle-daemon-jvm.properties`, and no `org.gradle.java.home` is set
- **THEN** no check of the daemon's Java is reported

#### Scenario: User's gradle.properties wins
- **WHEN** `GRADLE_USER_HOME/gradle.properties` and the repository's `gradle.properties` both set `org.gradle.java.home`
- **THEN** the check uses the one in `GRADLE_USER_HOME`

### Requirement: Daemon JVM criteria need an installed or downloadable Java
For daemon JVM criteria, the check SHALL pass, naming the Java version and where it was found, when a Java of exactly `toolchainVersion` is installed where Gradle looks; else pass, saying Gradle downloads it, when the file has `toolchainUrl.` entries and downloads aren't turned off. Otherwise it SHALL warn, naming the version and the places searched, with the fix to install that JDK or let Gradle download it. A missing or non-numeric `toolchainVersion` SHALL warn that the criteria can't be read.

#### Scenario: Requested Java installed
- **WHEN** the criteria ask for Java 21 and a JDK whose `release` file says `JAVA_VERSION="21.0.5"` is in `/Library/Java/JavaVirtualMachines` on macOS
- **THEN** the check of the daemon's Java passes, naming Java 21 and that JDK's folder

#### Scenario: Requested Java downloadable
- **WHEN** the criteria ask for Java 21, no Java 21 is installed and the file has `toolchainUrl.MAC_OS.AARCH64`
- **THEN** the check passes, saying Gradle downloads Java 21

#### Scenario: Requested Java missing
- **WHEN** the criteria ask for Java 21, no Java 21 is installed and the file has no `toolchainUrl.` entry
- **THEN** the check warns that Java 21 wasn't found, naming the places searched, with the fix to install a Java 21 JDK or add a toolchain download repository and run `./gradlew updateDaemonJvm`

#### Scenario: Downloads turned off
- **WHEN** the criteria ask for Java 21, no Java 21 is installed, the file has `toolchainUrl.` entries and `org.gradle.java.installations.auto-download=false` is set
- **THEN** the check warns that Java 21 wasn't found

#### Scenario: Unreadable criteria
- **WHEN** `gradle/gradle-daemon-jvm.properties` has no `toolchainVersion`
- **THEN** the check warns that the daemon JVM criteria can't be read, with the fix to regenerate them with `./gradlew updateDaemonJvm`

### Requirement: Check the Java set by org.gradle.java.home
When the daemon's Java comes from `org.gradle.java.home`, the check SHALL run `bin/java` (`bin\java.exe` on Windows) under it with `-version` and pass, naming the major version and where it was found. It SHALL warn, naming the property, its value and the file that sets it, when that `java` doesn't exist, and warn with its output when `java -version` fails.

#### Scenario: Property points at a JDK
- **WHEN** the repository's `gradle.properties` sets `org.gradle.java.home=/opt/jdk-17` and `/opt/jdk-17/bin/java -version` prints `openjdk version "17.0.12"`
- **THEN** the check of the daemon's Java passes, naming Java 17 and `/opt/jdk-17/bin/java`

#### Scenario: Property points nowhere
- **WHEN** `org.gradle.java.home` is `/opt/jdk-gone` and `/opt/jdk-gone/bin/java` doesn't exist
- **THEN** the check warns, naming `org.gradle.java.home`, `/opt/jdk-gone` and the `gradle.properties` that sets it, with the fix to correct or remove the property

### Requirement: The daemon's Java suits the Gradle version
The check of the daemon's Java SHALL judge its Java version against the project's Gradle version with the same tables as the Java check for Gradle: warn when Java is older than the Gradle version needs, or when Gradle is older than the first one that runs on that Java. The fix SHALL name the setting that chose that Java.

#### Scenario: Criteria too new for the Gradle version
- **WHEN** a project uses Gradle 8.10 and its criteria ask for Java 25
- **THEN** the check of the daemon's Java warns that Java 25 needs Gradle 9.1, with the fix to update the wrapper or lower `toolchainVersion` in `gradle/gradle-daemon-jvm.properties`

#### Scenario: Property Java too old for Gradle 9
- **WHEN** a project uses Gradle 9.1.0 and `org.gradle.java.home` points at a Java 11
- **THEN** the check warns that Gradle 9.1.0 needs Java 17, with the fix to point `org.gradle.java.home` at Java 17 or newer

### Requirement: Check the build's Java toolchains
When the root build script, a build script of a direct subfolder, or a file under `buildSrc` or `build-logic` declares a Java toolchain (`jvmToolchain(N)` or `JavaLanguageVersion.of(N)`), a check of the Java toolchains SHALL pass, naming the versions, when each is installed where Gradle looks or the settings script applies the `org.gradle.toolchains.foojay-resolver` plugin and downloads aren't turned off. Otherwise it SHALL warn, naming the missing versions and the places searched.

#### Scenario: Toolchain installed
- **WHEN** `app/build.gradle.kts` declares `jvmToolchain(17)` and a Java 17 JDK is in `~/.sdkman/candidates/java`
- **THEN** the Java toolchains check passes, naming Java 17

#### Scenario: Toolchain downloadable
- **WHEN** a build declares `JavaLanguageVersion.of(21)`, no Java 21 is installed and `settings.gradle.kts` applies `org.gradle.toolchains.foojay-resolver-convention`
- **THEN** the Java toolchains check passes, saying Gradle downloads Java 21

#### Scenario: Toolchain missing
- **WHEN** a build declares `jvmToolchain(17)` and `jvmToolchain(21)`, Java 21 is installed, Java 17 isn't and no resolver plugin is applied
- **THEN** the Java toolchains check warns that Java 17 wasn't found, with the fix to install a Java 17 JDK or apply the `foojay-resolver-convention` plugin in the settings script

#### Scenario: No toolchain
- **WHEN** no build script declares a toolchain
- **THEN** no Java toolchains check is reported

### Requirement: Where installed Java is searched
Installed Java SHALL be searched where Gradle detects it: `JAVA_HOME`, the Java that runs Gradle, `org.gradle.java.installations.paths`, `GRADLE_USER_HOME/jdks`, `~/.jdks`, SDKMAN!'s and asdf's Java folders, and the system's JDK folders. A JDK's version SHALL be read from its `release` file. With `org.gradle.java.installations.auto-detect=false`, only `org.gradle.java.installations.paths` and the Java that runs Gradle SHALL count.

#### Scenario: System JDK folders
- **WHEN** the check looks for installed Java
- **THEN** it searches `/Library/Java/JavaVirtualMachines` and `~/Library/Java/JavaVirtualMachines` on macOS, `/usr/lib/jvm` on Linux, and `C:\Program Files\Java` and `C:\Program Files\Eclipse Adoptium` on Windows

#### Scenario: JDK downloaded by Gradle
- **WHEN** the criteria ask for Java 17 and `~/.gradle/jdks/eclipse_adoptium-17-aarch64-os_x/jdk-17.0.12+7/Contents/Home/release` says `JAVA_VERSION="17.0.12"`
- **THEN** Java 17 is found there

#### Scenario: Auto-detection turned off
- **WHEN** `org.gradle.java.installations.auto-detect=false` is set, the only Java 21 is in `/usr/lib/jvm` on Linux and the criteria ask for Java 21
- **THEN** the check warns that Java 21 wasn't found

### Requirement: Check the Java the Android Gradle Plugin needs
When the version catalog (`gradle/libs.versions.toml`) or a build script uses the Android Gradle Plugin with a version, a check SHALL judge the Java the daemon runs (the criteria's version, else `org.gradle.java.home`'s, else the Java for Gradle's) against it: AGP 8 or later needs Java 17, AGP 7 Java 11, older AGP Java 8. It SHALL pass naming both, or warn with the fix to raise that Java where it was chosen. When that Java is unknown, the check SHALL NOT be reported.

#### Scenario: AGP 8 on Java 17
- **WHEN** `gradle/libs.versions.toml` has `agp = "8.5.2"` referenced by the `com.android.application` plugin and Gradle's Java is 17
- **THEN** the Android Gradle Plugin check passes, naming AGP 8.5.2 and Java 17

#### Scenario: AGP 8 on Java 11
- **WHEN** the root `build.gradle` has `classpath 'com.android.tools.build:gradle:8.2.0'`, the daemon has no criteria nor `org.gradle.java.home`, and Gradle's Java is 11
- **THEN** the check warns that the Android Gradle Plugin 8.2.0 needs Java 17, with the fix to point `JAVA_HOME` at Java 17 or newer

#### Scenario: Criteria choose the daemon's Java
- **WHEN** the build uses AGP 8.7.0, `JAVA_HOME` is a Java 11 and the daemon JVM criteria ask for Java 21
- **THEN** the Android Gradle Plugin check passes with Java 21

#### Scenario: No Android Gradle Plugin
- **WHEN** no build script nor the version catalog uses the Android Gradle Plugin
- **THEN** no Android Gradle Plugin check is reported
