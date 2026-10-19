# Spec Delta

## ADDED Requirements

### Requirement: Refresh device states at a slower pace
While a worktree view is shown, the system SHALL refresh the emulator and simulator states of a checked project at most every 10 seconds rather than on every 3-second check. It SHALL refresh them right away after the user starts or stops a device from ShogunAi and whenever the project's worktree list reloads.

#### Scenario: Nothing changes
- **WHEN** a project that declares the emulator provider is checked every 3 seconds for 30 seconds without changes
- **THEN** its emulator states are refreshed at most 3 times in that period

#### Scenario: Emulator stopped outside ShogunAi
- **WHEN** a worktree's emulator is closed from its own window
- **THEN** its row and detail show it as stopped within about 10 seconds

#### Scenario: Start from ShogunAi
- **WHEN** the user starts a worktree's emulator and it finishes booting
- **THEN** its row and detail show it as running as soon as the start completes, without waiting for the next device refresh

#### Scenario: Worktree list reloads
- **WHEN** a project's Git metadata changes and its worktree list reloads
- **THEN** its device states are refreshed with that reload
