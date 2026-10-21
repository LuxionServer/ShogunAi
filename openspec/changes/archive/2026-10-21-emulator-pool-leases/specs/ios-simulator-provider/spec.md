# Spec Delta

## ADDED Requirements

### Requirement: Simulators follow the pool's leases
The simulator pool SHALL apply the same leases, release order and release record as the emulator pool: every simulator start renews the worktree's lease, only simulators with an expired lease are released, in the order of agent state defined by the pool, and a booted simulator is shut down before it is released.

#### Scenario: Start renews the lease
- **WHEN** a worktree starts its simulator
- **THEN** its lease runs for the full lease duration from that moment

#### Scenario: Booted simulator with an expired lease
- **WHEN** the simulator pool is full and the only releasable simulator is booted with an expired lease
- **THEN** it is shut down, deleted and released, and the requesting worktree's simulator is created and booted

#### Scenario: Shut down but leased
- **WHEN** the limit is 1 and the only simulator is shut down with a valid lease
- **THEN** starting another worktree's simulator fails with the pool's error

#### Scenario: Status after a release
- **WHEN** `TASK-1`'s simulator was released for `TASK-2`
- **THEN** its state is reported as not acquired and says it was released for `TASK-2` and when

## MODIFIED Requirements

### Requirement: Released simulators are deleted
When the simulator pool releases a worktree's simulator to make room for another worktree, the system SHALL delete that simulator and remove its UDID from the worktree's reservations, so the worktree acquires a new one the next time it starts its simulator.

#### Scenario: Released to make room
- **WHEN** the simulator limit is 1, `TASK-1` holds a shut-down simulator with an expired lease and the user starts the simulator of `TASK-2`
- **THEN** `TASK-1`'s simulator is deleted, `TASK-2`'s is created and booted, and `TASK-1`'s environment no longer contains `IOS_SIMULATOR_UDID`
