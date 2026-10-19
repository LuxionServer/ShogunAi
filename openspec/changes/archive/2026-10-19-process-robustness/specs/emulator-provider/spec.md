# Spec Delta

## ADDED Requirements

### Requirement: Fail early when the emulator exits
While waiting for a started emulator to boot, the system SHALL notice when the emulator process exits and fail at once with an error that includes the last lines of its log, instead of waiting for the boot timeout.

#### Scenario: Broken AVD
- **WHEN** the chosen AVD's emulator process exits two seconds after starting
- **THEN** starting fails within a few seconds with the emulator's last log lines, and no timeout is waited
