## MODIFIED Requirements

### Requirement: Providers add variables to the environment
A provider declared by the repository SHALL be able to reserve ports in its own range and add variables to every worktree's environment, delivered the same way as declared variables. A provider's port MAY be on demand: then resolving the environment SHALL only read it, never reserve it, and the provider's variables SHALL appear once it has been acquired. A variable with the same name declared in `env` SHALL take precedence over the provider's.

#### Scenario: Provider variable
- **WHEN** the repository declares the emulator provider and no `env`, and a worktree has started its emulator
- **THEN** that worktree's environment contains the provider's `ANDROID_SERIAL`

#### Scenario: On-demand port not acquired
- **WHEN** the repository declares the emulator provider and a worktree's environment is resolved with reservation before its emulator was ever started
- **THEN** no console port is reserved and the other variables are delivered without `ANDROID_SERIAL`

#### Scenario: Declared variable wins
- **WHEN** the repository declares the emulator provider and `env` sets `ANDROID_SERIAL` to `emulator-5600`
- **THEN** the worktree's environment has `ANDROID_SERIAL=emulator-5600`

### Requirement: Providers keep resources with the worktree
A provider SHALL be able to keep a per-worktree resource other than a port (such as a simulator's UDID) with the worktree's reservations, readable without creating it. An on-demand resource SHALL be created only when it is acquired, checked and recreated when it no longer exists only when it is acquired again, and released by removing it from the reservations. The resource SHALL be stored with the worktree's ports, so removing the worktree's Git administrative data drops it.

#### Scenario: Created once
- **WHEN** a worktree acquires a provider's resource twice
- **THEN** the resource is created the first time and reused the second

#### Scenario: Read without creating
- **WHEN** a worktree's environment is resolved, with or without reservation, and its on-demand resource doesn't exist yet
- **THEN** nothing is created and no variable for it is returned

#### Scenario: Resource gone
- **WHEN** the recorded resource no longer exists
- **THEN** the next acquisition creates a new one and records it, and resolving the environment alone creates nothing

#### Scenario: Released
- **WHEN** a worktree's resource is released
- **THEN** it's removed from the worktree's reservations and the worktree's other ports and resources are kept
