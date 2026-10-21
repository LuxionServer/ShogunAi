# stack-detection Specification

## Purpose
Defines how ShogunAi inspects a repository's files to tell which stacks it uses and which providers fit them, so it can suggest providers without the user having to know them.

## Requirements

### Requirement: Detect the stacks of a repository
The system SHALL inspect the repository's main folder and its direct subfolders, reading only file names and, for Gradle build files, their text, and report the stacks it finds with the files that revealed each. The stacks are Gradle, Android, Xcode, Maven and Docker Compose. Folders such as `build`, `node_modules` and hidden folders SHALL NOT be inspected.

#### Scenario: Gradle repository
- **WHEN** the repository has `gradlew` and `settings.gradle.kts` in its main folder
- **THEN** the Gradle stack is detected and the evidence names those files

#### Scenario: Android in a module
- **WHEN** `app/build.gradle.kts` applies the plugin `com.android.application`
- **THEN** the Android stack is detected and the evidence names `app/build.gradle.kts`

#### Scenario: Xcode project in a subfolder
- **WHEN** the repository has `iosApp/iosApp.xcodeproj`
- **THEN** the Xcode stack is detected and the evidence names that folder

#### Scenario: Swift package
- **WHEN** the repository has `Package.swift` in its main folder
- **THEN** the Xcode stack is detected

#### Scenario: Maven repository
- **WHEN** the repository has `pom.xml` or `mvnw`
- **THEN** the Maven stack is detected

#### Scenario: Compose file
- **WHEN** the repository has `compose.yaml`, `compose.yml`, `docker-compose.yaml` or `docker-compose.yml`
- **THEN** the Docker Compose stack is detected

#### Scenario: Nothing recognizable
- **WHEN** the repository has none of those files
- **THEN** no stack is detected and nothing fails

#### Scenario: Unreadable file
- **WHEN** a build file can't be read
- **THEN** the stacks that depend on its text aren't detected and the others are not affected

### Requirement: Suggest providers for the detected stacks
The system SHALL map each detected stack to a provider: Gradle to `gradle`, Android to `emulator`, Xcode to `ios-simulator` and `xcode`, Maven to `maven`, Docker Compose to `docker-compose`. It SHALL NOT suggest a provider the repository already declares, nor one the operating system doesn't support, and a suggestion SHALL NEVER be applied without the user's action.

#### Scenario: Suggestions for an Android and Gradle repository
- **WHEN** a repository is detected as Gradle and Android and declares no provider
- **THEN** `gradle` and `emulator` are suggested, each with its evidence

#### Scenario: Already declared
- **WHEN** the repository declares the `gradle` provider
- **THEN** `gradle` isn't suggested

#### Scenario: Unsupported platform
- **WHEN** the repository has an Xcode project and ShogunAi runs on Windows
- **THEN** `ios-simulator` and `xcode` aren't suggested

#### Scenario: Detection changes nothing
- **WHEN** providers are suggested
- **THEN** `.shogunai.json` is neither created nor changed until the user saves a change
