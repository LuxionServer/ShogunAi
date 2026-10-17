# Spec Delta

## ADDED Requirements

### Requirement: Providers keep resources with the worktree
A provider SHALL be able to keep a per-worktree resource other than a port (such as a simulator's UDID) with the worktree's reservations, created the first time the environment is resolved with reservation, checked on later resolutions and recreated when it no longer exists, and readable without creating it. The resource SHALL be stored with the worktree's ports, so removing the worktree's Git administrative data drops it.

#### Scenario: Created once
- **WHEN** a worktree's environment is resolved twice with reservation
- **THEN** the provider's resource is created the first time and reused the second

#### Scenario: Read without creating
- **WHEN** the worktree list reads a worktree's environment without reservation and the resource doesn't exist yet
- **THEN** nothing is created and no variable for it is returned

#### Scenario: Resource gone
- **WHEN** the recorded resource no longer exists
- **THEN** the next resolution with reservation creates a new one and records it
