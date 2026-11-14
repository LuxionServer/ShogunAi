# Spec Delta

## MODIFIED Requirements

### Requirement: Detect the stacks of a repository
The system SHALL inspect the repository's main folder and its direct subfolders, reading only file names and, for Gradle build and settings files and the main folder's `pubspec.yaml`, their text, and report the stacks it finds with the files that revealed each. The stacks are Gradle, Android, Xcode, Maven, Docker Compose, Flutter and Node. Node SHALL be detected from a `package.json` in the main folder, with the main folder's lockfiles as further evidence. Folders such as `build`, `node_modules` and hidden folders SHALL NOT be inspected.

#### Scenario: Gradle repository
- **WHEN** the repository has `gradlew` and `settings.gradle.kts` in its main folder
- **THEN** the Gradle stack is detected and the evidence names those files

#### Scenario: Gradle build in a subfolder
- **WHEN** the main folder has no Gradle files and `android/settings.gradle.kts` exists
- **THEN** the Gradle stack is detected and the evidence names `android/settings.gradle.kts`

#### Scenario: Nested build next to a root build
- **WHEN** the main folder has `settings.gradle.kts` and `build-logic/settings.gradle.kts` exists
- **THEN** the Gradle stack's evidence names only the main folder's files

#### Scenario: Android in a module
- **WHEN** `app/build.gradle.kts` applies the plugin `com.android.application`
- **THEN** the Android stack is detected and the evidence names `app/build.gradle.kts`

#### Scenario: Android in a settings file
- **WHEN** `android/settings.gradle.kts` declares `id("com.android.application") version "8.7.0" apply false`
- **THEN** the Android stack is detected and the evidence names `android/settings.gradle.kts`

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

#### Scenario: Flutter app
- **WHEN** the main folder's `pubspec.yaml` lists `flutter` with `sdk: flutter` among its dependencies, next to `android/settings.gradle.kts` and `ios/Runner.xcodeproj`
- **THEN** the Flutter, Gradle, Android and Xcode stacks are detected, and the Flutter evidence names `pubspec.yaml`

#### Scenario: Dart package
- **WHEN** the main folder's `pubspec.yaml` doesn't mention `sdk: flutter`
- **THEN** the Flutter stack isn't detected

#### Scenario: Node repository
- **WHEN** the main folder has `package.json` and `pnpm-lock.yaml`
- **THEN** the Node stack is detected and the evidence names `package.json` and `pnpm-lock.yaml`

#### Scenario: Node project in a subfolder only
- **WHEN** only `web/package.json` exists
- **THEN** the Node stack isn't detected

#### Scenario: Nothing recognizable
- **WHEN** the repository has none of those files
- **THEN** no stack is detected and nothing fails

#### Scenario: Unreadable file
- **WHEN** a build file can't be read
- **THEN** the stacks that depend on its text aren't detected and the others are not affected

### Requirement: Suggest providers for the detected stacks
The system SHALL map each detected stack to a provider: Gradle to `gradle`, Android to `emulator`, Xcode to `ios-simulator` and `xcode`, Maven to `maven`, Docker Compose to `docker-compose`, Flutter to `flutter`, Node to `node`. It SHALL NOT suggest a provider the repository already declares, nor one the operating system doesn't support, and a suggestion SHALL NEVER be applied without the user's action.

#### Scenario: Suggestions for an Android and Gradle repository
- **WHEN** a repository is detected as Gradle and Android and declares no provider
- **THEN** `gradle` and `emulator` are suggested, each with its evidence

#### Scenario: Suggestions for a Flutter repository
- **WHEN** a repository is detected as Flutter, Gradle, Android and Xcode, declares no provider, and ShogunAi runs on macOS
- **THEN** `flutter`, `gradle`, `emulator`, `ios-simulator` and `xcode` are suggested, each with its evidence

#### Scenario: Suggestion for a Node repository
- **WHEN** a repository is detected as Node and Docker Compose, declares no provider, and ShogunAi runs on Linux
- **THEN** `node` and `docker-compose` are suggested, each with its evidence

#### Scenario: Already declared
- **WHEN** the repository declares the `gradle` provider
- **THEN** `gradle` isn't suggested

#### Scenario: Unsupported platform
- **WHEN** the repository has an Xcode project and ShogunAi runs on Windows
- **THEN** `ios-simulator` and `xcode` aren't suggested

#### Scenario: Detection changes nothing
- **WHEN** providers are suggested
- **THEN** `.shogunai.json` is neither created nor changed until the user saves a change
