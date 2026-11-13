## MODIFIED Requirements

### Requirement: Release the least-recently-used idle resource when full
When a worktree acquires a resource and its pool is at its limit, the system SHALL release one releasable resource of that kind and give the freed capacity to the requesting worktree. Only resources whose lease has expired are releasable. A device whose agent is working or whose lease is valid SHALL never be released. A booting device is never releasable. The release SHALL be reported to whoever acquired.

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

## ADDED Requirements

### Requirement: Order of release
Among the releasable resources of a full pool, the choice SHALL follow this order, least recently used first within each step: a stopped or shut-down device whose worktree has no agent session; a stopped or shut-down device whose agent is done or idle; a running or booted device, which SHALL be stopped before being released; a device whose agent is blocked.

#### Scenario: By agent state
- **WHEN** the pool is full and the expired leases are a stopped emulator whose agent is done, a running emulator and a stopped emulator with no session
- **THEN** the one with no session is released first, then the one whose agent is done, then the running one

#### Scenario: Least recently used within a step
- **WHEN** the pool is full and two stopped emulators with expired leases and no agent session were last started on Monday and on Tuesday
- **THEN** Monday's is released

#### Scenario: Running device stopped first
- **WHEN** the pool is full and the only releasable emulator is running with an expired lease
- **THEN** it is stopped and then released, and the requesting worktree's emulator starts

#### Scenario: Blocked agent after a running device
- **WHEN** the pool is full and the expired leases are a running emulator and a stopped emulator whose agent is blocked
- **THEN** the running emulator is released before the blocked one's
