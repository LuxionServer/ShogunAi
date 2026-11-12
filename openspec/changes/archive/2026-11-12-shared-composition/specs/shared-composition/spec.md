# Spec Delta

## Purpose

Keeps the command line and the desktop app behaving the same by giving both one wiring of ShogunAi's operations, with every dependency required and the differences between the two surfaces declared in one place.

## ADDED Requirements

### Requirement: Both surfaces run the same wiring
The command line and the desktop app SHALL obtain every operation they share from one composition graph built in `core`, so an operation run from either surface uses the same collaborators and the same rules.

#### Scenario: An operation shared by both surfaces
- **WHEN** the command line and the desktop app run the same operation on the same machine state, such as resolving a worktree's environment or starting its emulator
- **THEN** both use the collaborators the shared graph wired, and neither builds its own copy of that operation

#### Scenario: A new operation
- **WHEN** an operation is added to the shared graph
- **THEN** both surfaces can use it without wiring it again

### Requirement: Every dependency is required
The shared graph SHALL require each of its dependencies explicitly; none SHALL fall back to an inert implementation when a surface forgets to provide it.

#### Scenario: A surface omits a dependency
- **WHEN** a surface builds the shared graph without one of its dependencies, such as the repository trust store
- **THEN** the build fails at compile time instead of running with that dependency disabled

### Requirement: Surface differences are declared
Behaviour that differs between the command line and the desktop app SHALL be chosen through explicit options when the shared graph is built, each with its reason in the change that introduced it.

#### Scenario: The desktop app shares emulator reads and caches Git directories
- **WHEN** the desktop app builds the shared graph
- **THEN** it opts into shared emulator-state reads and the Git directory cache, and the command line, which runs one command per process, doesn't

### Requirement: The real dependencies are chosen once
The real implementation of each dependency of the shared graph SHALL be chosen in one place for a host environment, used by both surfaces.

#### Scenario: Both surfaces start on the same machine
- **WHEN** the command line and the desktop app start with the same host environment
- **THEN** both use the same real implementations, reading the same files under `~/.shogunai`
