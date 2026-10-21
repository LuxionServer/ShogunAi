# Spec Delta

## ADDED Requirements

### Requirement: Tell the agent to start the emulator before using it
The `SessionStart` hint for a repository that declares the provider SHALL state that the agent must run `shogun emulator start` before using the device, every time, because the device may have been released to another worktree since the session began, and that the command prints the serial to use.

#### Scenario: Hint after a session starts
- **WHEN** a session starts in a worktree of a repository with the provider
- **THEN** the hint says to run `shogun emulator start` before installing the app or running instrumented tests, and that the device may have been released

#### Scenario: Start after a release
- **WHEN** an agent's device was released and another worktree took its port, and the agent runs `shogun emulator start`
- **THEN** the worktree acquires a device again and the output names the serial to use

### Requirement: Explain a released emulator
Reporting a worktree's emulator that was released to make room for another SHALL say that it was released, when, and for which worktree, until the worktree acquires an emulator again.

#### Scenario: Status after a release
- **WHEN** `TASK-1`'s emulator was released for `TASK-2` and `shogun emulator status` runs in `TASK-1`
- **THEN** it reports the emulator as not acquired and says it was released for `TASK-2` and when

## MODIFIED Requirements

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
