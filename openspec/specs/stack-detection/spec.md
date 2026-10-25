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

### Requirement: Detect the mobile secrets of a repository
The system SHALL find, at any depth of the base repository, the files Git doesn't track named `local.properties`, `keystore.properties`, `key.properties`, `google-services.json` or `GoogleService-Info.plist`, or ending in `.jks` or `.keystore`, each with its kind: local properties, signing properties, keystore or Firebase configuration. Files under `build`, `.gradle`, `node_modules`, `Pods` or `DerivedData` folders SHALL NOT be reported.

#### Scenario: Android secrets
- **WHEN** the base repository has the ignored files `local.properties`, `app/google-services.json` and `keys/release.jks`
- **THEN** the three are reported, as local properties, Firebase configuration and keystore

#### Scenario: Firebase file deep in a module
- **WHEN** the ignored file `app/src/debug/google-services.json` exists
- **THEN** it's reported as Firebase configuration

#### Scenario: Tracked file
- **WHEN** `GoogleService-Info.plist` is tracked by Git
- **THEN** it isn't reported

#### Scenario: Build output
- **WHEN** an untracked `app/build/intermediates/local.properties` exists
- **THEN** it isn't reported

#### Scenario: Nothing found
- **WHEN** the repository has none of those files
- **THEN** nothing is reported and nothing fails

### Requirement: Report keystore references that won't resolve from a worktree
In each detected properties file, the system SHALL read every key whose name, lowercased and without `.`, `_` and `-`, ends with `storefile`. A relative value SHALL be resolved against the file's folder and against the repository root: when one result is an untracked file inside the repository, that file SHALL be reported as a keystore; when every result is outside the repository, a warning SHALL name the file, the key and the value. Absolute values SHALL NOT be reported.

#### Scenario: Keystore outside the repository
- **WHEN** `keystore.properties` at the repository root contains `storeFile=../signing/release.jks`
- **THEN** a warning names `keystore.properties`, `storeFile` and `../signing/release.jks`

#### Scenario: Keystore inside the repository
- **WHEN** `local.properties` contains `RELEASE_STORE_FILE=signing/upload.ks` and `signing/upload.ks` is an untracked file of the repository
- **THEN** `signing/upload.ks` is reported as a keystore and no warning is given

#### Scenario: Absolute path
- **WHEN** `key.properties` contains `storeFile=/opt/keys/release.jks`
- **THEN** neither a keystore nor a warning is reported for it

#### Scenario: Missing keystore inside the repository
- **WHEN** the value resolves inside the repository to a file that doesn't exist
- **THEN** nothing is reported for it

### Requirement: Suggest linking the mobile secrets
The system SHALL suggest, for each detected mobile secret that the current provisioning (local files, `copy`, `link` and `.worktreeinclude`) doesn't already provide, the `link` pattern anchored to the repository root that provisions it. A suggestion SHALL NEVER be applied without the user's action.

#### Scenario: Undeclared secret
- **WHEN** `app/google-services.json` is detected and nothing provisions it
- **THEN** the pattern `/app/google-services.json` in `link` is suggested

#### Scenario: Already provisioned
- **WHEN** `local.properties` is detected and `.shogunai.json` declares `local.properties` in `copy`
- **THEN** it isn't suggested

#### Scenario: Provided by a local entry
- **WHEN** `keys/release.jks` is detected and the project's local files include the folder `keys`
- **THEN** it isn't suggested

#### Scenario: Detection changes nothing
- **WHEN** mobile secrets are suggested
- **THEN** `.shogunai.json` is neither created nor changed until the user saves a change
