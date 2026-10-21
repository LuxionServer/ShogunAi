# Spec Delta

## ADDED Requirements

### Requirement: Leases protect a worktree's device for a limited time
Each worktree that holds an emulator or a simulator SHALL have a lease on it. Starting the device and every moment the worktree's agent is working SHALL renew the lease. A lease SHALL expire after the lease duration without renewal, and only a device whose lease has expired MAY be released to make room for another worktree. A reservation with no recorded lease SHALL be treated as expired.

#### Scenario: Start renews the lease
- **WHEN** a worktree starts its emulator
- **THEN** its lease runs for the full lease duration from that moment

#### Scenario: Working agent renews the lease
- **WHEN** a worktree's agent is working and the lease would otherwise have expired
- **THEN** the lease is renewed and the device is not releasable

#### Scenario: Lease expires
- **WHEN** a worktree's emulator was last started 25 minutes ago, its agent has done nothing since and the lease duration is 20 minutes
- **THEN** the lease has expired and the emulator can be released

#### Scenario: Reservation from before leases
- **WHEN** a worktree reserved its emulator before leases were recorded
- **THEN** its lease counts as expired

### Requirement: Lease duration as a machine setting
The machine settings SHALL include the lease duration, a positive whole number of minutes, 20 by default. It SHALL apply to every pool, be applied by the desktop app and the `shogun` command alike, SHALL NOT be read from `.shogunai.json`, and changing it SHALL NOT stop or release anything by itself.

#### Scenario: Default
- **WHEN** the app settings have never stored a lease duration
- **THEN** the lease duration is 20 minutes

#### Scenario: Repository can't set the duration
- **WHEN** `.shogunai.json` contains a field trying to set a lease duration
- **THEN** the lease duration is not changed

#### Scenario: Invalid value
- **WHEN** the user types `0`, a negative number or text as the lease duration
- **THEN** the value is rejected and the stored duration is unchanged

### Requirement: Edit the lease duration in Preferences
Preferences > Resources SHALL offer the lease duration in minutes, save the change immediately, and have its labels available in English and Spanish.

#### Scenario: Change the duration
- **WHEN** the user enters 30
- **THEN** 30 minutes is stored and shown the next time Preferences opens

### Requirement: Record why a worktree lost its device
When the system releases a worktree's emulator or simulator to make room for another, it SHALL record on the released worktree that it was released, when, and for which worktree. The record SHALL be cleared when the worktree acquires that kind of device again.

#### Scenario: Released worktree explains itself
- **WHEN** `TASK-1`'s emulator is released to make room for `TASK-2`
- **THEN** `TASK-1`'s state records the release, its time and `TASK-2` as the beneficiary

#### Scenario: Acquired again
- **WHEN** `TASK-1` starts its emulator again
- **THEN** the release record is cleared

## MODIFIED Requirements

### Requirement: Release the least-recently-used idle resource when full
When a worktree acquires a resource and its pool is at its limit, the system SHALL release one releasable resource of that kind and give the freed capacity to the requesting worktree. Only resources whose lease has expired are releasable, and the choice SHALL follow this order, least recently used first within each step: a stopped or shut-down device whose worktree has no agent session; a stopped or shut-down device whose agent is done or idle; a running or booted device, which SHALL be stopped before being released; a device whose agent is blocked. A device whose agent is working or whose lease is valid SHALL never be released. A booting device is never releasable. The release SHALL be reported to whoever acquired.

#### Scenario: Reclaim a stopped emulator
- **WHEN** the emulator limit is 1, worktree `TASK-1` holds a stopped emulator with an expired lease and no agent session, and the user starts the emulator of `TASK-2`
- **THEN** `TASK-1`'s reservation is released, `TASK-2`'s emulator starts, and the result says `TASK-1`'s emulator was released

#### Scenario: Least recently used first
- **WHEN** the pool is full and two stopped emulators with expired leases and no agent session were last started on Monday and on Tuesday
- **THEN** Monday's is released

#### Scenario: Agent state decides the order
- **WHEN** the pool is full and the expired leases are a stopped emulator whose agent is done, a running emulator and a stopped emulator with no session
- **THEN** the one with no session is released first, then the one whose agent is done, then the running one

#### Scenario: Running emulator with an expired lease
- **WHEN** the pool is full and the only releasable emulator is running with an expired lease
- **THEN** it is stopped and then released, and the requesting worktree's emulator starts

#### Scenario: Blocked agent last
- **WHEN** the pool is full and the expired leases are a running emulator and a stopped emulator whose agent is blocked
- **THEN** the running emulator is released before the blocked one's

#### Scenario: Stopped but leased
- **WHEN** a worktree stopped its emulator two minutes ago and the lease duration is 20 minutes
- **THEN** its emulator is not released by another worktree's acquisition

#### Scenario: Working agent
- **WHEN** a worktree's agent is working
- **THEN** its device is never released, whatever its state

### Requirement: Fail clearly when nothing is idle
When a pool is at its limit and none of its resources is releasable, acquiring SHALL fail without stopping anything, with an error that states the limit and names, for each resource in use, the project and worktree that holds it, its state, and when its lease expires or that its agent is working, and says that waiting, stopping one or raising the limit in Preferences frees capacity.

#### Scenario: All emulators running
- **WHEN** the emulator limit is 2, the emulators of `api` · `TASK-1` and `main-repo` · `TASK-2` are running with valid leases and the user starts a third
- **THEN** the start fails, nothing is stopped, and the error lists `api` · `TASK-1` and `main-repo` · `TASK-2` as running with their lease expiry and points to waiting, stopping one or to Preferences

#### Scenario: Stopped but leased
- **WHEN** the limit is 1 and the only emulator is stopped with a valid lease
- **THEN** the start fails and the error says when that lease expires
