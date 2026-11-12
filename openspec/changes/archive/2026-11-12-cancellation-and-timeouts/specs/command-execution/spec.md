# Spec Delta

## ADDED Requirements

### Requirement: Every command has a time limit
The system SHALL run every external command with a time limit and, when the command runs longer, SHALL stop it and the processes it started and fail with a timeout error naming the command. The limit SHALL fit the command: about ten seconds for device and process listings, about half a minute for Git reads, about a minute for network requests and quick local tools, about five minutes for local Git writes and simulator boots, and about thirty minutes for installations and builds.

#### Scenario: Hung device bridge
- **WHEN** `adb devices` doesn't answer within its limit
- **THEN** the device states are reported as unavailable with a timeout error, and the next check tries again

#### Scenario: Hung post-creation command
- **WHEN** an approved post-creation command never finishes
- **THEN** after thirty minutes the command and its children are stopped and creation fails with a timeout error naming the command

#### Scenario: Slow installation within its limit
- **WHEN** `pod install` takes ten minutes on a cold cache
- **THEN** it runs to completion and the worktree is prepared as usual

#### Scenario: Hung terminal multiplexer
- **WHEN** a Herdr or Orca command doesn't answer within its limit
- **THEN** the operation fails with an error saying the command timed out, not that the tool is missing

### Requirement: Cancelled operations end as cancelled
When an operation is cancelled while it runs commands or waits on them, the system SHALL end that operation as cancelled: it MUST NOT report the cancellation as an ordinary failure, fall back to a default result or carry on with its next steps.

#### Scenario: Status read cancelled mid-way
- **WHEN** reading a worktree's status is cancelled while a Git command runs
- **THEN** the read ends as cancelled instead of returning a status error, and no further Git command of that read runs

#### Scenario: Device acquisition cancelled while waiting in line
- **WHEN** a worktree waits in line for an emulator and the wait is cancelled
- **THEN** the acquisition ends as cancelled instead of reporting a failure

## REMOVED Requirements

### Requirement: Reads that should be quick have a timeout
**Reason**: Replaced by "Every command has a time limit": commands that do work on the user's behalf (creation, post-creation commands, builds) no longer run without a limit.
**Migration**: None for users. Commands that could hang forever now fail with a timeout error after a generous limit.
