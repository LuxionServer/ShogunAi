# Spec Delta

## Purpose

Defines the emulator provider: an opt-in Android provider that gives each worktree its own emulator, with the AVD and window chosen per machine, a reserved console port delivered as `ANDROID_SERIAL`, and on-demand start, stop and status, without affecting repositories that don't declare it.

## ADDED Requirements

### Requirement: Opt-in emulator provider
Everything the emulator provider does SHALL apply only to repositories whose `.shogunai.json` declares `"providers": { "emulator": { … } }`. For any other repository the system SHALL NOT look for an Android SDK, list AVDs, query `adb`, reserve emulator ports, add `ANDROID_SERIAL`, nor show emulator settings, states or actions.

#### Scenario: Repository without the provider
- **WHEN** a repository doesn't declare the emulator provider and its worktree screen, configuration or environment is used
- **THEN** no emulator information or action appears, no Android tool runs, and the environment has no `ANDROID_SERIAL`

#### Scenario: Machine without an Android SDK
- **WHEN** the machine has no Android SDK and the repository doesn't declare the provider
- **THEN** ShogunAi works as before with no error or warning about Android

### Requirement: Choose the AVD
The system SHALL choose the AVD for a worktree's emulator in this order: the AVD given for that start, the project's machine setting, the repository's preferred `avd` if it exists on the machine, and the first AVD the SDK lists. No AVD on the machine SHALL fail the start with an error that says how to create one.

#### Scenario: Machine setting wins over the repository's preference
- **WHEN** the repository prefers `Pixel_8_API_35` and the project's machine setting is `Medium_Phone_API_35`
- **THEN** the emulator starts `Medium_Phone_API_35`

#### Scenario: Preferred AVD missing on this machine
- **WHEN** the repository prefers an AVD that this machine doesn't have and no machine setting is set
- **THEN** the first listed AVD is used

#### Scenario: No AVD
- **WHEN** the SDK lists no AVD
- **THEN** starting fails with an error that suggests creating one in Android Studio's Device Manager

### Requirement: Find the Android SDK
The system SHALL locate the Android SDK, only when the emulator is needed, from `ANDROID_HOME`, then `ANDROID_SDK_ROOT`, then `sdk.dir` in the repository's `local.properties`, then the platform's default location (`~/Library/Android/sdk` on macOS, `~/Android/Sdk` on Linux, `%LOCALAPPDATA%\Android\Sdk` on Windows), using its `emulator` and `platform-tools/adb` executables. A missing SDK SHALL fail with an error naming the places checked.

#### Scenario: SDK from local.properties
- **WHEN** neither variable is set and `local.properties` contains `sdk.dir=/opt/android-sdk`
- **THEN** `/opt/android-sdk/emulator/emulator` is used

#### Scenario: SDK not found
- **WHEN** no location contains the SDK and the user starts an emulator
- **THEN** the start fails with an error listing the variables and paths checked

### Requirement: Reserve an emulator port and export the serial
For a repository that declares the provider, the system SHALL reserve for each worktree an even console port between 5554 and 5584 whose next port is also free, with the same stability and uniqueness rules as other reserved ports, and add `ANDROID_SERIAL=emulator-<port>` to the worktree's environment. A variable named `ANDROID_SERIAL` declared in `env` SHALL take precedence.

#### Scenario: Serial in the environment
- **WHEN** a worktree reserves console port 5556
- **THEN** its environment contains `ANDROID_SERIAL=emulator-5556`, delivered by `shogun env`, the `SessionStart` hook and the environment file

#### Scenario: Two worktrees
- **WHEN** two worktrees of the repository resolve their environment
- **THEN** they get different console ports and different serials

#### Scenario: Ports exhausted
- **WHEN** all 16 console ports are reserved or busy
- **THEN** resolving the environment fails with an error saying no emulator port is left

### Requirement: Start a worktree's emulator
Starting SHALL launch the chosen AVD on the worktree's console port in read-only mode, so the same AVD can run once per worktree, without a window when the machine setting or the start asks for it, detached from the caller, and wait until the device reports it has booted or a timeout passes. Starting an emulator that is already running SHALL succeed without launching another.

#### Scenario: Start and wait
- **WHEN** the user starts the emulator of a worktree whose serial is `emulator-5556`
- **THEN** the AVD runs on port 5556 in read-only mode and the start returns once `emulator-5556` has booted

#### Scenario: Already running
- **WHEN** `emulator-5556` is already online
- **THEN** starting reports it as running and launches nothing

#### Scenario: Without a window
- **WHEN** the project's machine setting asks for no window
- **THEN** the emulator is started with no window

#### Scenario: Boot timeout
- **WHEN** the device doesn't report a completed boot within the timeout
- **THEN** the start fails with an error, leaving the emulator running so it can finish booting

### Requirement: Stop and report a worktree's emulator
The system SHALL report a worktree's emulator as stopped, booting or running from `adb`'s device list and boot property, and stop it by asking that emulator to exit through `adb -s <serial> emu kill`. Stopping an emulator that isn't running SHALL succeed and change nothing.

#### Scenario: Status
- **WHEN** `adb` lists `emulator-5556` as a device whose boot has completed
- **THEN** the worktree's emulator is reported as running

#### Scenario: Stop
- **WHEN** the user stops a running worktree emulator
- **THEN** that emulator exits and the others keep running

#### Scenario: Stop when stopped
- **WHEN** the worktree's emulator isn't running
- **THEN** stopping succeeds and nothing else happens
