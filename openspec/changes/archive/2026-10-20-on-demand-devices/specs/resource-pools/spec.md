## Purpose

Defines the pools of limited-capacity machine resources that worktrees share (Android emulators, iOS simulators and Gradle daemons): the capacity profiles and limits chosen per machine, how a worktree acquires and releases a resource, and what happens when a pool is full.

## ADDED Requirements

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
When a worktree acquires a resource and its pool is at its limit, the system SHALL release the idle resource of that kind that was least recently used and give the freed capacity to the requesting worktree. A stopped emulator and a shut-down simulator are idle; a running or booting one is not. The release SHALL be reported to whoever acquired.

#### Scenario: Reclaim a stopped emulator
- **WHEN** the emulator limit is 1, worktree `TASK-1` holds a stopped emulator and the user starts the emulator of `TASK-2`
- **THEN** `TASK-1`'s reservation is released, `TASK-2`'s emulator starts, and the result says `TASK-1`'s emulator was released

#### Scenario: Least recently used first
- **WHEN** the pool is full and two idle emulators were last started on Monday and on Tuesday
- **THEN** Monday's is released

### Requirement: Fail clearly when nothing is idle
When a pool is at its limit and none of its resources is idle, acquiring SHALL fail without stopping anything, with an error that states the limit and names, for each resource in use, the project and worktree that holds it and its state, and says that stopping one or raising the limit in Preferences frees capacity.

#### Scenario: All emulators running
- **WHEN** the emulator limit is 2, the emulators of `api` · `TASK-1` and `main-repo` · `TASK-2` are running and the user starts a third
- **THEN** the start fails, nothing is stopped, and the error lists `api` · `TASK-1` and `main-repo` · `TASK-2` as running and points to stopping one or to Preferences

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
