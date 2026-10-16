# Spec Delta

## MODIFIED Requirements

### Requirement: Reserve ports per worktree
The system SHALL reserve each port the first time a worktree needs it, choosing the lowest port in the requested range that no worktree on the machine has reserved, in any repository whose reservations ShogunAi has stored, and that is free on the machine at that moment. Variable ports use the range 20000 to 29999. Once reserved, a port SHALL stay the worktree's own, even if it is in use when read again. Running out of ports SHALL fail with an error.

#### Scenario: First reservation
- **WHEN** a worktree needs a port and no worktree has reserved one
- **THEN** it gets the lowest free port from 20000 up

#### Scenario: Unique among worktrees
- **WHEN** another worktree of the repository already reserved 20000
- **THEN** the new worktree gets a different port

#### Scenario: Unique across repositories
- **WHEN** a worktree of another repository on the same machine already reserved 20000, even if nothing listens on it
- **THEN** the new worktree gets a different port

#### Scenario: Removed worktree of another repository
- **WHEN** a worktree of another repository that had reserved 20000 has been removed
- **THEN** 20000 can be reserved again

#### Scenario: Port busy on the machine
- **WHEN** 20000 isn't reserved by any worktree but another program is listening on it
- **THEN** it's skipped and the next free port is reserved

#### Scenario: Stable reservation
- **WHEN** a worktree's environment is resolved again, even while its dev server is listening on its reserved port
- **THEN** it gets the same port as before

## ADDED Requirements

### Requirement: Providers add variables to the environment
A provider declared by the repository SHALL be able to reserve ports in its own range and add variables to every worktree's environment, delivered the same way as declared variables. A variable with the same name declared in `env` SHALL take precedence over the provider's.

#### Scenario: Provider variable
- **WHEN** the repository declares the emulator provider and no `env`
- **THEN** each worktree's environment contains the provider's `ANDROID_SERIAL`

#### Scenario: Declared variable wins
- **WHEN** the repository declares the emulator provider and `env` sets `ANDROID_SERIAL` to `emulator-5600`
- **THEN** the worktree's environment has `ANDROID_SERIAL=emulator-5600`
