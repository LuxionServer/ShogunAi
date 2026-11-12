## MODIFIED Requirements

### Requirement: Find the Android SDK
The system SHALL locate the Android SDK, only when the emulator is needed, from `ANDROID_HOME`, then `ANDROID_SDK_ROOT`, then `sdk.dir` in the repository's `local.properties`, then `sdk.dir` in the `local.properties` of the Gradle builds in direct subfolders when the main folder isn't one, then the platform's default location, using its `emulator` and `platform-tools/adb` executables. A missing SDK SHALL fail with an error naming the places checked.

#### Scenario: SDK from local.properties
- **WHEN** neither variable is set and `local.properties` contains `sdk.dir=/opt/android-sdk`
- **THEN** `/opt/android-sdk/emulator/emulator` is used

#### Scenario: SDK from a nested build
- **WHEN** neither variable is set, the main folder has no `local.properties` and `android/local.properties` of the Gradle build `android/` contains `sdk.dir=/opt/android-sdk`
- **THEN** `/opt/android-sdk/emulator/emulator` is used

#### Scenario: SDK in the platform's default location
- **WHEN** neither variable is set and no `local.properties` names `sdk.dir`
- **THEN** the SDK is looked up in `~/Library/Android/sdk` on macOS, `~/Android/Sdk` on Linux and `%LOCALAPPDATA%\Android\Sdk` on Windows

#### Scenario: SDK not found
- **WHEN** no location contains the SDK and the user starts an emulator
- **THEN** the start fails with an error listing the variables and paths checked

### Requirement: Reserve an emulator port and export the serial
For a repository that declares the provider, the system SHALL reserve a worktree's console port when the worktree acquires its emulator from the emulator pool, the first time its emulator is started, not when its environment is resolved. The port SHALL be an even console port between 5554 and 5584 whose next port is also free, with the same stability and uniqueness rules as other reserved ports.

#### Scenario: New worktree
- **WHEN** a worktree of a repository with the provider is created and its environment is resolved
- **THEN** no console port is reserved and its environment has no `ANDROID_SERIAL`

#### Scenario: Serial in the environment
- **WHEN** a worktree acquired console port 5556 by starting its emulator
- **THEN** its environment contains `ANDROID_SERIAL=emulator-5556`, delivered by `shogun env`, the `SessionStart` hook and the environment file

#### Scenario: Two worktrees
- **WHEN** two worktrees of the repository start their emulators
- **THEN** they get different console ports and different serials

#### Scenario: Ports exhausted
- **WHEN** all 16 console ports are reserved or busy and none of the reserving worktrees has a releasable emulator
- **THEN** starting fails with an error saying no emulator port is left and naming the worktrees that hold them

#### Scenario: Port released by the pool
- **WHEN** the pool releases a worktree's emulator to make room for another
- **THEN** that worktree's console port reservation is removed and its environment no longer contains `ANDROID_SERIAL`

#### Scenario: Released port is not reused at once
- **WHEN** port 5554 was just released and ports 5556 and 5558 are free
- **THEN** the next worktree to acquire gets 5556, not 5554

#### Scenario: Only a recently released port is free
- **WHEN** port 5554 was just released and every other port is reserved or busy
- **THEN** the next worktree to acquire gets 5554

#### Scenario: Affinity
- **WHEN** a worktree whose emulator was released starts it again and its previous port is free
- **THEN** it gets its previous port back

### Requirement: Start a worktree's emulator
Starting SHALL first acquire the worktree's emulator from the emulator pool when the worktree doesn't hold one, then launch the chosen AVD on the worktree's console port in read-only mode, so the same AVD can run once per worktree, without a window when the machine setting or the start asks for it, detached from the caller, and wait until the device reports it has booted or a timeout passes. Starting an emulator that is already running SHALL succeed without launching another.

#### Scenario: Start and wait
- **WHEN** the user starts the emulator of a worktree whose serial is `emulator-5556`
- **THEN** the AVD runs on port 5556 in read-only mode and the start returns once `emulator-5556` has booted

#### Scenario: First start acquires the port
- **WHEN** the user starts the emulator of a worktree that holds no console port and the pool has room
- **THEN** a console port is reserved, the emulator starts on it and the result names its serial

#### Scenario: Pool full
- **WHEN** the user starts the emulator of a worktree that holds no console port and no emulator in the full pool is releasable
- **THEN** nothing is reserved or launched and the start fails with the pool's error

#### Scenario: Already running
- **WHEN** `emulator-5556` is already online
- **THEN** starting reports it as running, launches nothing and renews the lease

#### Scenario: Without a window
- **WHEN** the project's machine setting asks for no window
- **THEN** the emulator is started with no window

#### Scenario: Boot timeout
- **WHEN** the device doesn't report a completed boot within the timeout
- **THEN** the start fails with an error, leaving the emulator running so it can finish booting

## ADDED Requirements

### Requirement: Choose which free console port to reserve
A worktree that held a console port before SHALL get the same one back when it is free. Otherwise the system SHALL prefer a free port that wasn't released recently over any recently released one, and use a recently released port only when no other is free.

#### Scenario: Previous port back
- **WHEN** a worktree whose emulator was released starts it again and its previous port is free
- **THEN** it gets its previous port back

#### Scenario: Recently released port avoided
- **WHEN** port 5554 was just released and ports 5556 and 5558 are free
- **THEN** the next worktree to acquire gets 5556, not 5554

#### Scenario: Recently released port as the last option
- **WHEN** port 5554 was just released and every other port is reserved or busy
- **THEN** the next worktree to acquire gets 5554

### Requirement: Export the emulator's serial
Once a worktree's console port is reserved, its environment SHALL contain `ANDROID_SERIAL=emulator-<port>`; before that it SHALL NOT contain `ANDROID_SERIAL`. A variable named `ANDROID_SERIAL` declared in `env` SHALL take precedence.

#### Scenario: Serial after the port is reserved
- **WHEN** a worktree acquired console port 5556 by starting its emulator
- **THEN** its environment contains `ANDROID_SERIAL=emulator-5556`

#### Scenario: No serial before the port is reserved
- **WHEN** a worktree of a repository with the provider is created and its environment is resolved
- **THEN** its environment has no `ANDROID_SERIAL`

#### Scenario: Declared serial wins
- **WHEN** the repository declares `ANDROID_SERIAL` in `env` and the worktree reserved console port 5556
- **THEN** the worktree's environment has the declared value of `ANDROID_SERIAL`

### Requirement: Every emulator start renews the lease
Every start of a worktree's emulator SHALL renew the worktree's lease, record the time as the emulator's last use, and print the serial to use.

#### Scenario: Start of a running emulator
- **WHEN** `emulator-5556` is already online and the user starts it
- **THEN** the lease is renewed, its last use is now and `emulator-5556` is printed

#### Scenario: Start of a stopped emulator
- **WHEN** the user starts the stopped emulator of a worktree whose serial is `emulator-5556`
- **THEN** the lease is renewed, its last use is now and `emulator-5556` is printed
