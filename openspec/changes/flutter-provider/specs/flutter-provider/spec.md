# Spec Delta

## Purpose

Defines the Flutter provider: an opt-in provider that resolves a Flutter repository's packages in every new or provisioned worktree so it builds at once, reports when that step is still needed, and tells agents how to run Flutter on their worktree's own devices.

## ADDED Requirements

### Requirement: Opt-in Flutter provider
Everything the Flutter provider does SHALL apply only to repositories whose `.shogunai.json` declares `"providers": { "flutter": { … } }`. For any other repository the system SHALL NOT run `flutter` or `fvm`, report anything about Flutter packages, nor add Flutter rules to the agent context.

#### Scenario: Repository without the provider
- **WHEN** a repository with a Flutter `pubspec.yaml` doesn't declare the provider and a worktree is created
- **THEN** no `flutter` or `fvm` command runs and the result says nothing about Flutter packages

### Requirement: Resolve packages when a worktree is prepared
For a repository that declares the provider, creating a worktree in every creation flow and provisioning an existing worktree SHALL run `flutter pub get` in the worktree's main folder. It SHALL run after the files are copied and the pods are prepared, and before the post-creation commands. It SHALL run whether or not the repository is approved.

#### Scenario: New worktree
- **WHEN** a worktree of a repository that declares `{ "providers": { "flutter": {} } }` is created and `flutter pub get` succeeds
- **THEN** the command ran in the new worktree before any post-creation command, and the result reports the packages as resolved

#### Scenario: Post-creation command after the packages
- **WHEN** the repository also declares `dart run build_runner build` as an approved post-creation command
- **THEN** it runs after `flutter pub get` has finished

#### Scenario: Repository not approved
- **WHEN** the repository isn't approved and the worktree is created by `shogun hook worktree-create`
- **THEN** `flutter pub get` still runs, while the post-creation commands and declared variables are skipped

#### Scenario: Existing worktree provisioned
- **WHEN** an existing worktree of the repository is provisioned
- **THEN** `flutter pub get` runs in it again

### Requirement: Use the repository's FVM version
When the repository's main folder has `.fvmrc`, the system SHALL run `fvm flutter pub get` instead of `flutter pub get`.

#### Scenario: FVM repository
- **WHEN** the repository has `.fvmrc` and a worktree is created
- **THEN** `fvm flutter pub get` runs in the worktree

#### Scenario: No FVM
- **WHEN** the repository has no `.fvmrc`
- **THEN** `flutter pub get` runs

### Requirement: Packages never fail the worktree
When the command can't be found, exits with an error or fails in any other way, the worktree SHALL be kept and the result SHALL report that `flutter pub get` is still needed, with the reason: the command wasn't found, or its exit code and the last lines of its error output.

#### Scenario: Flutter not installed
- **WHEN** neither `flutter` nor `fvm` can be found and a worktree is created
- **THEN** the worktree is created and the result says `flutter pub get` is needed because `flutter` wasn't found

#### Scenario: Resolution fails
- **WHEN** `flutter pub get` exits with code 1 because a package can't be downloaded
- **THEN** the worktree is created, the post-creation commands still run, and the result says `flutter pub get` is needed with the exit code and the error lines

#### Scenario: Cancelled
- **WHEN** the user cancels the creation while `flutter pub get` runs
- **THEN** the command is stopped like a post-creation command

### Requirement: Packages in the command results
`shogun new`, `shogun provision` and the worktree creation hook SHALL report the outcome of `flutter pub get` when the provider is declared: in text as one line, and in JSON as `flutter.pubGet` with `status` `resolved` or `needed` and, when needed, the `reason`.

#### Scenario: JSON output
- **WHEN** `shogun provision --json` runs in a worktree of a Flutter repository and `flutter pub get` succeeds
- **THEN** the JSON has `flutter.pubGet.status` `resolved`

#### Scenario: Needed in text
- **WHEN** `shogun new TASK-123` creates a worktree and `flutter` wasn't found
- **THEN** the output has a line saying `flutter pub get` is needed in the worktree and why

#### Scenario: Provider not declared
- **WHEN** the repository doesn't declare the provider
- **THEN** the JSON has no `flutter` field and the text has no line about it

### Requirement: Tell the agent how to run Flutter on the worktree's devices
The `SessionStart` context for a repository that declares the provider SHALL say to run `flutter pub get` after `pubspec.yaml` or `pubspec.lock` change, naming `fvm flutter` when the repository has `.fvmrc`. It SHALL say to never launch emulators with `flutter emulators --launch`. It SHALL say to start the device with ShogunAi and pass it with `-d` to `flutter run`, `flutter test` and `flutter drive`, naming only the device providers the repository declares.

#### Scenario: Android and iOS
- **WHEN** a session starts in a worktree of a repository that declares the `flutter`, `emulator` and `ios-simulator` providers
- **THEN** the context says to run `shogun emulator start` and then `flutter run -d "$ANDROID_SERIAL"`, and `shogun simulator start` and then `flutter run -d "$IOS_SIMULATOR_UDID"`

#### Scenario: Only Android
- **WHEN** the repository declares `flutter` and `emulator` but not `ios-simulator`
- **THEN** the context names `ANDROID_SERIAL` and doesn't mention the iOS simulator

#### Scenario: FVM
- **WHEN** the repository has `.fvmrc`
- **THEN** the context says to use `fvm flutter` for Flutter commands

#### Scenario: Guide
- **WHEN** the user runs `shogun guide resources` in a worktree of a repository that declares the provider
- **THEN** the output includes the same Flutter rule
