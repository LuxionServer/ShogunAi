# Spec Delta

## ADDED Requirements

### Requirement: Show and control each worktree's emulator
For a repository that declares the emulator provider, each worktree's row SHALL show the state of its emulator (stopped, booting or running) once its emulator port is reserved, with a Start action when stopped and a Stop action otherwise, and refresh the state with the screen. Showing the row SHALL NOT reserve a port or start anything.

#### Scenario: Running emulator
- **WHEN** a worktree's `emulator-5556` is online and booted
- **THEN** its row shows the emulator as running with a Stop action

#### Scenario: Start from the row
- **WHEN** the user activates Start on a stopped worktree emulator
- **THEN** the emulator starts and the row shows it booting, then running

#### Scenario: Port not reserved yet
- **WHEN** a worktree has never had its environment resolved
- **THEN** its row shows a Start action, and starting reserves the port first

#### Scenario: Repository without the provider
- **WHEN** the repository doesn't declare the emulator provider
- **THEN** rows show no emulator information
