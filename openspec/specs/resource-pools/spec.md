# resource-pools Specification

## Purpose
Defines the pools of limited-capacity machine resources that worktrees share (Android emulators, iOS simulators and Gradle daemons): the capacity profiles and limits chosen per machine, how a worktree acquires and releases a resource, and what happens when a pool is full.

## Requirements

### Requirement: One pool per resource kind
The system SHALL keep one pool per resource kind on the machine: Android emulators, iOS simulators and Gradle daemons, shared by every repository and worktree. A worktree holds an emulator while it has a console port reserved, a simulator while it has one recorded, and a Gradle daemon is held by the worktree of its last build. Kotlin daemons SHALL NOT count against any pool.

#### Scenario: Shared across repositories
- **WHEN** a worktree of `main-repo` and a worktree of `api` each hold an emulator
- **THEN** the emulator pool counts two emulators in use

#### Scenario: Kotlin daemons
- **WHEN** three Gradle daemons and two Kotlin daemons are running
- **THEN** the Gradle daemon pool counts three

### Requirement: Limits come from the machine, not the repository
Pool limits SHALL be machine settings stored with the app's settings and SHALL NOT be read from `.shogunai.json`. The desktop app and the `shogun` command SHALL apply the same limits. A repository SHALL only declare which providers it needs.

#### Scenario: Same limits from the app and the command line
- **WHEN** the machine's emulator limit is 2 and an agent runs `shogun emulator start`
- **THEN** the command applies the limit of 2, as the app would

#### Scenario: Repository can't raise a limit
- **WHEN** `.shogunai.json` contains a field trying to set a pool limit
- **THEN** the limit is not changed

### Requirement: Capacity profiles
The limits SHALL follow one capacity profile: Automatic (the default), Light (1 emulator, 1 simulator, 2 Gradle daemons), Balanced (2, 2 and 4), One per worktree (no limit) or Custom (a limit per kind, a positive number or no limit). Changing the profile SHALL apply to the next acquisition without stopping anything already running.

#### Scenario: Default profile
- **WHEN** the app settings have never stored a profile
- **THEN** the Automatic profile is used

#### Scenario: Custom limits
- **WHEN** the user chooses Custom with 3 emulators, no limit for simulators and 2 Gradle daemons
- **THEN** those are the limits of each pool

#### Scenario: Lowering a limit
- **WHEN** three emulators are running and the user switches to Light
- **THEN** none is stopped, and the next emulator acquisition finds the pool full

### Requirement: Automatic limits from the machine
The Automatic profile SHALL derive each limit from the machine's physical memory and processor cores: devices (emulators, and simulators separately) one per 8 GB above the first 8 GB, at most one per 4 cores, between 1 and 4; Gradle daemons one per 8 GB, between 2 and 8. When memory or cores can't be read, it SHALL use the Light limits.

#### Scenario: 16 GB laptop
- **WHEN** the machine has 16 GB and 8 cores
- **THEN** the Automatic limits are 1 emulator, 1 simulator and 2 Gradle daemons

#### Scenario: 32 GB workstation
- **WHEN** the machine has 32 GB and 12 cores
- **THEN** the Automatic limits are 3 emulators, 3 simulators and 4 Gradle daemons

#### Scenario: Machine information unavailable
- **WHEN** the machine's memory can't be read
- **THEN** the Light limits are used

### Requirement: The platform's own bounds still apply
A limit of "no limit" SHALL still be bounded by what the platform allows: emulators by the 16 console port pairs between 5554 and 5585. The pool SHALL report the effective limit.

#### Scenario: One per worktree with many worktrees
- **WHEN** the profile is One per worktree and 16 worktrees hold an emulator
- **THEN** the emulator pool reports 16 of 16 in use

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
When a pool is at its limit and none of its resources is releasable, acquiring SHALL fail without stopping anything, with an error that states the limit and names, for each resource in use, the project and worktree that holds it, its state, and when its lease expires or that its agent is working, says how many worktrees are waiting for the pool when any are, and says that waiting with `--wait`, stopping one or raising the limit in Preferences frees capacity.

#### Scenario: All emulators running
- **WHEN** the emulator limit is 2, the emulators of `api` · `TASK-1` and `main-repo` · `TASK-2` are running with valid leases and the user starts a third
- **THEN** the start fails, nothing is stopped, and the error lists `api` · `TASK-1` and `main-repo` · `TASK-2` as running with their lease expiry and points to `--wait`, stopping one or to Preferences

#### Scenario: Stopped but leased
- **WHEN** the limit is 1 and the only emulator is stopped with a valid lease
- **THEN** the start fails and the error says when that lease expires

#### Scenario: Others waiting
- **WHEN** the emulator pool is full and two worktrees are waiting for it, and the user starts another worktree's emulator without `--wait`
- **THEN** the start fails and the error says two worktrees are waiting

### Requirement: Remember when a resource was last used
The system SHALL record, with each worktree's reservations, when its emulator or simulator was last started, and use it to order idle resources. A resource with no record SHALL be treated as the least recently used.

#### Scenario: Device held from before this change
- **WHEN** a worktree reserved its emulator port before last-use times were recorded
- **THEN** its emulator counts as acquired and, when idle, is released before any emulator with a recorded start

### Requirement: Keep Gradle daemons within their limit
When more Gradle daemons are running than the Gradle daemon pool's limit, the system SHALL stop idle Gradle daemons, longest idle first, following the existing stop rules, until the count is within the limit or no idle one is left. Busy daemons and daemons of unknown state SHALL never be stopped to honour the limit.

#### Scenario: Over the limit
- **WHEN** the limit is 2 and four Gradle daemons run, two idle since 10:00 and 11:00 and two busy
- **THEN** both idle daemons are stopped and the busy ones keep running

#### Scenario: Only busy daemons
- **WHEN** the limit is 2 and three busy daemons run
- **THEN** nothing is stopped and the pool reports 3 of 2 in use

### Requirement: DerivedData disk quota as a machine setting
The machine settings SHALL include a DerivedData disk quota in one of three modes: Automatic, a fixed positive whole number of gigabytes, or no quota, with Automatic as the default. It SHALL be independent of the capacity profile, applied by the desktop app and the `shogun` command alike, and SHALL NOT be read from `.shogunai.json`. Changing it SHALL NOT delete anything by itself.

#### Scenario: Default
- **WHEN** the app settings have never stored a quota
- **THEN** the DerivedData quota is Automatic

#### Scenario: Settings from before the automatic quota
- **WHEN** the stored settings have no quota mode and a quota of 50 GB
- **THEN** the quota is fixed at 50 GB

#### Scenario: Settings from before the automatic quota without a quota
- **WHEN** the stored settings have no quota mode and no quota
- **THEN** the quota is Automatic

#### Scenario: Profile change keeps the quota
- **WHEN** the quota is 50 GB and the user switches the capacity profile from Balanced to Light
- **THEN** the quota remains 50 GB

#### Scenario: Repository can't set the quota
- **WHEN** `.shogunai.json` contains a field trying to set a DerivedData quota
- **THEN** the quota is not changed

#### Scenario: Invalid value
- **WHEN** the user types `0`, a negative number or text as the quota
- **THEN** the value is rejected and the stored quota is unchanged

### Requirement: Edit the quota in Preferences
Preferences > Resources SHALL offer the DerivedData quota as a choice between Automatic, a fixed size with a gigabytes field, and "No quota", on macOS only, and SHALL save the change immediately. Automatic SHALL explain how its size is computed. The labels SHALL be available in English and Spanish.

#### Scenario: Set a quota
- **WHEN** the user chooses a fixed size and enters 40
- **THEN** the quota 40 GB is stored and shown the next time Preferences opens

#### Scenario: Back to Automatic
- **WHEN** the quota is fixed at 40 GB and the user chooses Automatic
- **THEN** the quota is Automatic, and choosing a fixed size again offers 40 GB

#### Scenario: Choose no quota
- **WHEN** the user chooses "No quota"
- **THEN** no DerivedData quota is applied from then on

#### Scenario: Not macOS
- **WHEN** the app runs on Linux or Windows
- **THEN** Preferences does not show the quota

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

### Requirement: Automatic DerivedData quota from the disk
The Automatic quota SHALL be a quarter of the space DerivedData can use, which is the free space on the volume of the DerivedData location plus the current DerivedData usage, kept between 20 GB and 200 GB. It SHALL be computed each time the quota is applied, once the folder sizes are known. When the free space can't be read, no folder SHALL be released and the quota SHALL be reported as unknown.

#### Scenario: Typical disk
- **WHEN** the volume has 100 GB free and DerivedData uses 60 GB
- **THEN** the Automatic quota is 40 GB

#### Scenario: Nearly full disk
- **WHEN** the volume has 10 GB free and DerivedData uses 30 GB
- **THEN** the Automatic quota is 20 GB, the minimum

#### Scenario: Large free disk
- **WHEN** the volume has 2 TB free
- **THEN** the Automatic quota is 200 GB, the maximum

#### Scenario: Releasing doesn't move the quota
- **WHEN** folders are released while applying an Automatic quota of 40 GB
- **THEN** the quota stays 40 GB during that application, since the space freed moves from usage to free space

#### Scenario: Free space unknown
- **WHEN** the free space of the DerivedData volume can't be read
- **THEN** nothing is released and the quota is shown as unknown
