# Spec Delta

## ADDED Requirements

### Requirement: Stopping a device shows progress
While a worktree's emulator or simulator is stopping, its Stop action SHALL show that it is in progress and SHALL ignore further clicks until it ends.

#### Scenario: Double click on Stop
- **WHEN** the user clicks Stop twice on a running emulator
- **THEN** one stop is sent and the action shows progress until the emulator is stopped

### Requirement: Copying a path is confirmed
Copying a worktree's path SHALL show the same transient confirmation as copying the launch command.

#### Scenario: Copy path
- **WHEN** the user copies a worktree's path from its detail
- **THEN** a confirmation says the path was copied
