# Spec Delta

## ADDED Requirements

### Requirement: Stop the emulators of removed worktrees
When the repository declares the emulator provider, the cleanup dialog SHALL offer an option, checked by default, to also stop the running emulators of the worktrees it removes. Stopping runs after the removals, and an emulator that can't be stopped SHALL NOT turn the cleanup into a failure.

#### Scenario: Option shown
- **WHEN** the repository declares the emulator provider and the user activates "Clean up"
- **THEN** the dialog shows a checked "also stop their emulators" option

#### Scenario: Emulator stopped
- **WHEN** the option is checked and a removed worktree's emulator is running
- **THEN** it's stopped and the report says how many emulators were stopped

#### Scenario: Repository without the provider
- **WHEN** the repository doesn't declare the emulator provider
- **THEN** the dialog doesn't show the option
