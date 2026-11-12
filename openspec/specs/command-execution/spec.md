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

### Requirement: Arguments reach the program intact on Windows
The system SHALL pass every argument of an external command to the program exactly as it was built, on Windows too: spaces, embedded double quotes and trailing backslashes in an argument MUST NOT split it, merge it with another or alter it.

#### Scenario: Argument with spaces and embedded quotes
- **WHEN** a command is run with the argument `GRADLE_OPTS=-Dorg.gradle.jvmargs="-Xmx4g -Dfile.encoding=UTF-8" -Dorg.gradle.daemon.idletimeout=1800000`
- **THEN** the program receives it as a single argument with that exact value

#### Scenario: Path with spaces ending in a backslash
- **WHEN** a command is run with the argument `C:\Program Files\My App\`
- **THEN** the program receives it as a single argument with that exact value

#### Scenario: Empty argument
- **WHEN** a command is run with an empty argument between two others
- **THEN** the program receives three arguments, the middle one empty

#### Scenario: Other systems are not affected
- **WHEN** a command is run on macOS or Linux
- **THEN** its arguments are passed unchanged

### Requirement: Batch launchers keep their arguments as given
The system SHALL NOT rewrite the arguments of a command whose program is a `.cmd` or `.bat` file, because `cmd.exe` does not interpret quotes and backslashes like other programs.

#### Scenario: Editor launcher script
- **WHEN** an editor is opened through a `.cmd` launcher with a path argument
- **THEN** the arguments are handed to the launcher as they were built, without added escaping

### Requirement: Git handles long paths on Windows
On Windows, the system SHALL run every Git command with Git's long path support turned on, so Git can create, read and delete files inside a worktree whose full path is longer than 260 characters. On other systems Git commands SHALL run unchanged.

#### Scenario: Removing a worktree with deep build outputs
- **WHEN** on Windows a worktree holds a file whose full path is longer than 260 characters and the user removes the worktree
- **THEN** Git deletes it instead of failing with "Filename too long"

#### Scenario: Other systems are not affected
- **WHEN** a Git command runs on macOS or Linux
- **THEN** its arguments are passed unchanged

#### Scenario: Other programs are not affected
- **WHEN** on Windows a command whose program isn't Git runs
- **THEN** its arguments are passed unchanged

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
