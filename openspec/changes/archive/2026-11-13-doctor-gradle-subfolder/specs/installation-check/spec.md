# Spec Delta

## ADDED Requirements

### Requirement: Find the project's Gradle build
The Gradle checks of a project that declares `gradle` SHALL read its Gradle build: the repository's root when it has `gradlew`, `settings.gradle` or `settings.gradle.kts`, else the first direct subfolder in name order with a settings script, skipping hidden folders, `build`, `node_modules` and `Pods`. Its wrapper, wrapper version, `gradle.properties`, daemon JVM criteria, build scripts and version catalog SHALL be read from that folder. With no build anywhere, the root SHALL be read.

#### Scenario: Gradle build in android/
- **WHEN** a Flutter repository declares `gradle`, its root has no `gradlew` or settings script, and `android/` has `settings.gradle.kts`, `gradlew` and a wrapper for Gradle 8.10.2
- **THEN** the Gradle check passes, naming `android/gradlew` and Gradle 8.10.2, and is followed by the Java check for Gradle

#### Scenario: Java settings of the nested build
- **WHEN** a repository's Gradle build is in `android/`, whose `gradle/gradle-daemon-jvm.properties` asks for Java 21 and whose `gradle/libs.versions.toml` uses Android Gradle Plugin 8.7.0
- **THEN** the check of the daemon's Java judges Java 21 from that criteria file and the Android Gradle Plugin check names 8.7.0

#### Scenario: Root build wins
- **WHEN** a repository's root has `settings.gradle.kts` and a subfolder also has one
- **THEN** the Gradle checks read the root

#### Scenario: First nested build in name order
- **WHEN** a repository's root isn't a Gradle build and both `android/` and `server/` have a settings script
- **THEN** the Gradle checks read `android/`

#### Scenario: No Gradle build
- **WHEN** a project declares `gradle`, no folder has a Gradle build and `gradle` isn't on the `PATH`
- **THEN** the Gradle check warns, naming the root's `gradlew` as the place searched
