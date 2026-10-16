# Spec Delta

## ADDED Requirements

### Requirement: Manage a worktree's emulator from the command line
`shogun emulator start [<path>] [--avd <name>] [--no-window]`, `shogun emulator stop [<path>]` and `shogun emulator status [<path>]` SHALL act on the emulator of the worktree containing the path or the current directory, printing its serial and state. In a repository that doesn't declare the emulator provider they SHALL fail with exit code 1 and say how to declare it.

#### Scenario: Start
- **WHEN** the user runs `shogun emulator start` in a worktree whose serial is `emulator-5556`
- **THEN** the emulator boots, the command prints `emulator-5556 running` and exits with code 0

#### Scenario: Status
- **WHEN** the user runs `shogun emulator status` and the emulator is stopped
- **THEN** it prints `emulator-5556 stopped` and exits with code 0

#### Scenario: Repository without the provider
- **WHEN** the user runs `shogun emulator start` in a repository without the provider
- **THEN** an error on standard error explains how to declare `"providers": { "emulator": {} }` and the command exits with code 1

### Requirement: Stop emulators when cleaning up from the command line
With `--yes`, `shogun clean --stop-emulators` SHALL also stop the running emulators of the removed worktrees, and without `--yes` list them. Without the option no emulator SHALL be stopped. A failure to stop one SHALL be reported on standard error without changing the exit code.

#### Scenario: Emulator of a removed worktree
- **WHEN** the user runs `shogun clean --yes --stop-emulators` and a removed worktree's emulator is running
- **THEN** it's stopped and the output says so

#### Scenario: Without the option
- **WHEN** the user runs `shogun clean --yes`
- **THEN** no emulator is stopped

### Requirement: Tell Claude how to start its emulator
When the repository declares the emulator provider, the context line printed by `shogun hook session-start` SHALL also say that the worktree's emulator is `ANDROID_SERIAL` and that `shogun emulator start` starts it.

#### Scenario: Context with the emulator
- **WHEN** a Claude Code session starts in a worktree whose serial is `emulator-5556`
- **THEN** the context line includes `ANDROID_SERIAL=emulator-5556` and mentions `shogun emulator start`
