# command-execution Specification

## Purpose
Defines how ShogunAi runs external programs (Git, Gradle, adb, simctl, post-creation commands) so they never outlive the operation that started them and never hang waiting for input.

## Requirements

### Requirement: Cancelled operations stop their commands
When an operation that runs an external command is cancelled, the system SHALL stop that command and the processes it started, and SHALL return control without waiting for the command to finish on its own.

#### Scenario: Cancel a slow post-creation command
- **WHEN** a post-creation command runs `sleep 600` and the user cancels the creation
- **THEN** the `sleep` process and its children are gone within a few seconds and the operation reports it was cancelled

### Requirement: Commands never wait for input
The system SHALL run every external command with no input available, so a command that tries to read input gets end-of-file instead of waiting.

#### Scenario: Interactive installer
- **WHEN** an approved post-creation command asks a question and reads the answer from standard input
- **THEN** it reads end-of-file, and creation finishes or fails with that command's own error instead of hanging

### Requirement: Reads that should be quick have a timeout
Commands that only read state (Git status and listing reads, device listings from adb and simctl, process listings) SHALL fail with a timeout error naming the command when they run longer than their limit. Commands that do work on the user's behalf (creation, post-creation commands, builds) SHALL have no limit.

#### Scenario: Hung device bridge
- **WHEN** `adb devices` doesn't answer within its limit
- **THEN** the device states are reported as unavailable with a timeout error, and the next check tries again
