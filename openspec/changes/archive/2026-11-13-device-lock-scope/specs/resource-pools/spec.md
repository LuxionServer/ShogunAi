# Spec Delta

## ADDED Requirements

### Requirement: Device commands run outside the machine-wide lock
Acquiring an emulator or a simulator SHALL hold the machine-wide state lock only to decide and record reservations. Listing devices, stopping, shutting down or deleting released devices and creating a simulator SHALL run outside it, so another process waiting for the lock waits only for state files to be read and written.

#### Scenario: Releasing a running emulator
- **WHEN** the emulator pool is full, the only releasable emulator is running and another worktree starts its emulator
- **THEN** the released emulator is stopped after the lock is released, and no `adb` command runs while the lock is held

#### Scenario: Creating a simulator
- **WHEN** a worktree that holds no simulator starts its simulator
- **THEN** the simulator list is read and the simulator is created while the lock is not held

### Requirement: Concurrent acquisitions keep reservations unique and within the limit
Acquisitions running at the same time in different processes SHALL never reserve the same console port or simulator for two worktrees, and the reservations of a pool SHALL never exceed its limit. Devices listed before the lock was taken SHALL only be used to order releasable devices and to find a recorded simulator that no longer exists; a simulator recorded after the listing SHALL NOT be judged missing.

#### Scenario: Two worktrees race for the last emulator
- **WHEN** the emulator limit is 2, one emulator is held, and two other worktrees acquire an emulator at the same time
- **THEN** one of them gets the emulator and the other fails with the pool's error, or waits when it asked to

#### Scenario: Pool filled while the simulator was being created
- **WHEN** a worktree created its simulator because the pool had room, and another worktree took the last place before the simulator was recorded
- **THEN** the created simulator is deleted, nothing is recorded and the acquisition fails with the pool's error

#### Scenario: Simulator created by another process after the listing
- **WHEN** a worktree's simulator was recorded by another process after this acquisition listed the simulators
- **THEN** the acquisition keeps that simulator instead of creating another

### Requirement: An acquisition that fails after reserving leaves no reservation
When stopping or deleting a released device or creating the simulator fails, or the acquisition is cancelled after its reservation was recorded, the acquisition SHALL remove its own reservation and delete the simulator it created before failing. Devices it released stay released.

#### Scenario: A released emulator can't be stopped
- **WHEN** an acquisition released a running emulator to make room and stopping it fails
- **THEN** the acquisition fails with that error and the requesting worktree holds no console port

#### Scenario: Cancelled while releasing a simulator
- **WHEN** an acquisition is cancelled while shutting down the simulator it released
- **THEN** the acquisition ends as cancelled, the requesting worktree holds no simulator and the simulator created for it is deleted

### Requirement: Free a console port only with a stopped emulator
When every console port is reserved or busy, acquiring SHALL release only a releasable emulator whose port pair is already free, and SHALL NOT stop a running emulator to look for a port.

#### Scenario: Only running emulators could free a port
- **WHEN** every console port is reserved or busy and the only releasable emulators are running
- **THEN** acquiring fails saying no emulator port is left, and no emulator is stopped
