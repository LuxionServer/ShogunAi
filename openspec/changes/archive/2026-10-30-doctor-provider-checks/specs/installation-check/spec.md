# Spec Delta

## MODIFIED Requirements

### Requirement: Run the installation check without changing anything
The system SHALL offer an installation check, as `shogun doctor` in the CLI and as "Check Installation…" in the desktop app's Help menu. It SHALL run every check in this specification, in the order listed, and SHALL NOT write, create or delete any file, install anything or start any program other than to read a version or list what is installed (Android Virtual Devices, simulator runtimes, a repository's worktrees).

#### Scenario: Nothing changes
- **WHEN** the installation check runs on a machine where the hooks are outdated and a project's local file is missing
- **THEN** both are reported and no settings file, project, worktree or local file is modified

#### Scenario: Missing multirepo folder isn't forgotten
- **WHEN** a known multirepo's folder no longer has its declaration
- **THEN** the check reports it and the multirepo stays in ShogunAi's list of known multirepos

## ADDED Requirements

### Requirement: Check the tools of each project's providers
For each registered project whose base folder exists, the check SHALL read the providers its repository declares and report one check per tool they need: the Gradle wrapper or `gradle` for `gradle`, the Maven wrapper or `mvn` for `maven`, the Android SDK and an AVD for `emulator`, the simulators and an iOS runtime for `ios-simulator`, Xcode for `xcode` and `pod` when it declares CocoaPods, `docker compose` for `docker-compose`, and `flutter` (or `fvm`) for `flutter`.

#### Scenario: Tools found
- **WHEN** a project declares `docker-compose` and `docker compose version` runs
- **THEN** a check for that project's Docker Compose passes, naming where `docker` was found

#### Scenario: No providers
- **WHEN** a project's `.shogunai.json` declares no providers, or the repository has none
- **THEN** no provider tool check is reported for it

#### Scenario: Wrapper in the repository
- **WHEN** a project declares `gradle`, its base folder has `gradlew` and `gradle` isn't on the `PATH`
- **THEN** the Gradle check passes, naming the wrapper

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
- **THEN** its check fails, naming the file and the problem, with the fix to correct it

#### Scenario: Declaration gone
- **WHEN** a known multirepo's folder no longer has `.shogunai-multirepo.json`
- **THEN** its check warns that ShogunAi will forget it, with the fix to restore the folder or ignore the warning

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
