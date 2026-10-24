# emulator-provider Specification

## Purpose
Defines the emulator provider: an opt-in Android provider that gives each worktree its own emulator, with the AVD and window chosen per machine, a reserved console port delivered as `ANDROID_SERIAL`, and on-demand start, stop and status, without affecting repositories that don't declare it.

## Requirements

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
For a repository that declares the provider, the system SHALL reserve a worktree's console port when the worktree acquires its emulator from the emulator pool, the first time its emulator is started, not when its environment is resolved. The port SHALL be an even console port between 5554 and 5584 whose next port is also free, with the same stability and uniqueness rules as other reserved ports. A worktree that held a port before SHALL get the same one back when it is free. Otherwise the system SHALL prefer a free port that wasn't released recently over any recently released one, and use a recently released port only when no other is free. Once reserved, the worktree's environment SHALL contain `ANDROID_SERIAL=emulator-<port>`; before that it SHALL NOT contain `ANDROID_SERIAL`. A variable named `ANDROID_SERIAL` declared in `env` SHALL take precedence.

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
Starting SHALL first acquire the worktree's emulator from the emulator pool when the worktree doesn't hold one, then launch the chosen AVD on the worktree's console port in read-only mode, so the same AVD can run once per worktree, without a window when the machine setting or the start asks for it, detached from the caller, and wait until the device reports it has booted or a timeout passes. Starting an emulator that is already running SHALL succeed without launching another. Every start SHALL renew the worktree's lease and record the time as the emulator's last use, and SHALL print the serial to use.

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

### Requirement: Stop and report a worktree's emulator
The system SHALL report a worktree's emulator as not acquired when the worktree holds no console port, and otherwise as stopped, booting or running from `adb`'s device list and boot property. It SHALL stop it by asking that emulator to exit through `adb -s <serial> emu kill`, keeping the worktree's reservation. Stopping an emulator that isn't running or isn't acquired SHALL succeed and change nothing.

#### Scenario: Status
- **WHEN** `adb` lists `emulator-5556` as a device whose boot has completed
- **THEN** the worktree's emulator is reported as running

#### Scenario: Not acquired
- **WHEN** a worktree never started its emulator
- **THEN** its emulator is reported as not acquired and no `adb` query is made for it

#### Scenario: Stop
- **WHEN** the user stops a running worktree emulator
- **THEN** that emulator exits, the others keep running, and the worktree keeps its serial

#### Scenario: Stop when stopped
- **WHEN** the worktree's emulator isn't running
- **THEN** stopping succeeds and nothing else happens

### Requirement: Query adb sparingly for emulator states
The desktop app SHALL read the emulator device list with a single `adb devices` call for all the reads made within the same 2 seconds, whichever projects or views ask for it. It SHALL query an emulator's boot property only until it has seen that emulator's boot completed, and again only after that emulator leaves the device list, stops being reported as `device`, or is stopped through ShogunAi.

#### Scenario: Several Android projects checked together
- **WHEN** three projects that declare the emulator provider are checked in the same auto-refresh cycle
- **THEN** `adb devices` runs once for that cycle and all three projects report states from its output

#### Scenario: Booted emulator stays online
- **WHEN** `emulator-5556` has been reported as running and later device lists still show it as `device`
- **THEN** it keeps being reported as running without querying its boot property again

#### Scenario: Emulator restarted
- **WHEN** a running `emulator-5556` disappears from the device list and later shows up again as `device`
- **THEN** its boot property is queried again, and it's reported as booting until the boot completes

#### Scenario: Stopped through ShogunAi
- **WHEN** the user stops `emulator-5556` from ShogunAi and starts it again
- **THEN** its state is read fresh after the stop, and it's reported as booting until its boot property says the boot completed

### Requirement: Fail early when the emulator exits
While waiting for a started emulator to boot, the system SHALL notice when the emulator process exits and fail at once with an error that includes the last lines of its log, instead of waiting for the boot timeout.

#### Scenario: Broken AVD
- **WHEN** the chosen AVD's emulator process exits two seconds after starting
- **THEN** starting fails within a few seconds with the emulator's last log lines, and no timeout is waited

### Requirement: Tell the agent to start the emulator before using it
The `SessionStart` hint for a repository that declares the provider SHALL state that the agent must run `shogun emulator start` before using the device, every time, because the device may have been released to another worktree since the session began, that the command prints the serial to use, and that when the pool is full it can be retried with `--wait`, run in the background.

#### Scenario: Hint after a session starts
- **WHEN** a session starts in a worktree of a repository with the provider
- **THEN** the hint says to run `shogun emulator start` before installing the app or running instrumented tests, that the device may have been released, and that `shogun emulator start --wait` in the background waits for a full pool

#### Scenario: Start after a release
- **WHEN** an agent's device was released and another worktree took its port, and the agent runs `shogun emulator start`
- **THEN** the worktree acquires a device again and the output names the serial to use

### Requirement: Explain a released emulator
Reporting a worktree's emulator that was released to make room for another SHALL say that it was released, when, and for which worktree, until the worktree acquires an emulator again.

#### Scenario: Status after a release
- **WHEN** `TASK-1`'s emulator was released for `TASK-2` and `shogun emulator status` runs in `TASK-1`
- **THEN** it reports the emulator as not acquired and says it was released for `TASK-2` and when
