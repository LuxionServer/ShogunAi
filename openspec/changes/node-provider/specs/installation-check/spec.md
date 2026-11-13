# Spec Delta

## MODIFIED Requirements

### Requirement: Check the tools of each project's providers
For each registered project whose base folder exists, the check SHALL read the providers its repository declares and report one check per tool they need: the Android SDK and an AVD for `emulator`, the simulators and an iOS runtime for `ios-simulator`, Xcode for `xcode` and `pod` when it declares CocoaPods, `flutter` (or `fvm`) for `flutter`, and `node` and the package manager of its lockfile for `node`. The tools of `gradle`, `maven` and `docker-compose` are checked as their own requirements say.

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

#### Scenario: Node project
- **WHEN** a project declares `node` and its main folder has `package-lock.json`
- **THEN** checks for `node` and `npm` are reported for it

#### Scenario: Plain Gradle build
- **WHEN** a project declares `gradle`, its wrapper is found, and its repository has no daemon JVM criteria, no `org.gradle.java.home`, no toolchain and no Android Gradle Plugin
- **THEN** only the Gradle check and the Java check for Gradle are reported for it
