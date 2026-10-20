## MODIFIED Requirements

### Requirement: Reserve an emulator port and export the serial
For a repository that declares the provider, the system SHALL reserve a worktree's console port when the worktree acquires its emulator from the emulator pool, the first time its emulator is started, not when its environment is resolved. The port SHALL be an even console port between 5554 and 5584 whose next port is also free, with the same stability and uniqueness rules as other reserved ports. Once reserved, the worktree's environment SHALL contain `ANDROID_SERIAL=emulator-<port>`; before that it SHALL NOT contain `ANDROID_SERIAL`. A variable named `ANDROID_SERIAL` declared in `env` SHALL take precedence.

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
- **WHEN** all 16 console ports are reserved or busy and none of the reserving worktrees has an idle emulator
- **THEN** starting fails with an error saying no emulator port is left and naming the worktrees that hold them

#### Scenario: Port released by the pool
- **WHEN** the pool releases a worktree's idle emulator to make room for another
- **THEN** that worktree's console port reservation is removed and its environment no longer contains `ANDROID_SERIAL`

### Requirement: Start a worktree's emulator
Starting SHALL first acquire the worktree's emulator from the emulator pool when the worktree doesn't hold one, then launch the chosen AVD on the worktree's console port in read-only mode, so the same AVD can run once per worktree, without a window when the machine setting or the start asks for it, detached from the caller, and wait until the device reports it has booted or a timeout passes. Starting an emulator that is already running SHALL succeed without launching another. Every start SHALL record the time as the emulator's last use.

#### Scenario: Start and wait
- **WHEN** the user starts the emulator of a worktree whose serial is `emulator-5556`
- **THEN** the AVD runs on port 5556 in read-only mode and the start returns once `emulator-5556` has booted

#### Scenario: First start acquires the port
- **WHEN** the user starts the emulator of a worktree that holds no console port and the pool has room
- **THEN** a console port is reserved, the emulator starts on it and the result names its serial

#### Scenario: Pool full
- **WHEN** the user starts the emulator of a worktree that holds no console port and the emulator pool is full with every emulator running
- **THEN** nothing is reserved or launched and the start fails with the pool's error

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
