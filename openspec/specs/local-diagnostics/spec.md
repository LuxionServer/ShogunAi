## Purpose

Lets the desktop app keep a local record of its errors and freezes, and lets the user copy a diagnostic report to attach to a bug report, without the app ever sending anything off the machine.

## Requirements

### Requirement: Local diagnostics log
The desktop app SHALL record in a diagnostics log inside the user's ShogunAi folder every unexpected error that reaches the app without being handled, every error shown to the user, and every freeze of the user interface. Each entry SHALL have a timestamp, a kind and a message, and SHALL include the stack trace when there is one.

#### Scenario: Unhandled error
- **WHEN** an error is thrown in the app and nothing handles it
- **THEN** an entry with its kind, message and stack trace is added to the diagnostics log

#### Scenario: Error shown to the user
- **WHEN** reloading a project fails and the error is shown in its group
- **THEN** an entry with the error's summary and technical details is added to the diagnostics log

### Requirement: Freezes are detected
The desktop app SHALL detect when the user interface hasn't responded for 2 seconds or longer and SHALL record one entry per freeze with how long it lasted and what the user interface thread was doing when it was detected.

#### Scenario: Interface blocked
- **WHEN** the user interface thread is busy for 5 seconds
- **THEN** a single freeze entry is recorded with a duration of about 5 seconds and the stack of the user interface thread

#### Scenario: Short pause
- **WHEN** the user interface thread is busy for half a second
- **THEN** nothing is recorded

### Requirement: The log is bounded
The diagnostics log SHALL rotate when its current file reaches 1 MB, keeping at most 3 files, so it never takes more than about 3 MB.

#### Scenario: Log full
- **WHEN** a new entry would make the current file exceed 1 MB and 3 files already exist
- **THEN** the oldest file is deleted, the others are shifted and the entry is written to a new current file

### Requirement: No personal data in the log
Before an entry is written, the app SHALL replace every absolute file path with a placeholder, the user's name and home folder with a placeholder, and the name of every saved project and of every branch of their worktrees with a placeholder. Class and method names in stack traces SHALL be kept.

#### Scenario: Path and project in an error
- **WHEN** an error says `Couldn't read /Users/jane/projects/main-repo/.shogunai.json` and `main-repo` is a saved project
- **THEN** the entry contains neither `/Users/jane`, `jane` nor `main-repo`: the path is replaced by a placeholder and the rest of the message is kept

#### Scenario: Branch name
- **WHEN** an error mentions the branch `feature/TASK-123` of a worktree
- **THEN** the entry doesn't contain `TASK-123`

### Requirement: Copy diagnostic report
The Help menu SHALL offer "Copy Diagnostic Report", which copies to the clipboard a plain-text report with the app version, the operating system with its version and architecture, the Java runtime version, the results of the installation check and the most recent entries of the diagnostics log, and confirms that it was copied. The report SHALL follow the same redaction as the log.

#### Scenario: Copy the report
- **WHEN** the user chooses "Copy Diagnostic Report"
- **THEN** the clipboard holds the report with the version, operating system, architecture, Java runtime, installation check results and recent log entries, and the app says it was copied

#### Scenario: Empty log
- **WHEN** the user chooses "Copy Diagnostic Report" and nothing has been recorded
- **THEN** the report holds the version, system information and installation check results, and says that no errors were recorded

#### Scenario: Installation problem in the report
- **WHEN** the hooks call a `shogun` that doesn't exist and the user copies the diagnostic report
- **THEN** the report's "Installation" section has the failed hooks check with its fix, in English

#### Scenario: Paths in the installation results
- **WHEN** a project's missing local file is at `/Users/jane/projects/main-repo/local.properties` and the report is copied
- **THEN** the report's installation section contains neither `/Users/jane` nor `main-repo`

#### Scenario: Check can't run
- **WHEN** the installation check throws while the report is built
- **THEN** the report is still copied, its installation section saying the check couldn't run and why

### Requirement: Nothing leaves the machine
The app SHALL NOT send the diagnostics log or the report anywhere; the report SHALL only reach the clipboard when the user asks for it.

#### Scenario: Error recorded
- **WHEN** an error is recorded in the diagnostics log
- **THEN** no network request is made because of it

### Requirement: Each shown error is recorded once
An error shown to the user SHALL be recorded in the diagnostics log once, when it is first shown, however many times the notice showing it is drawn again (scrolling it out of view and back, reloading the list, showing the same error in two places). A new occurrence of a failure SHALL be recorded again, even with the same message.

#### Scenario: Cleanup report shown again
- **WHEN** a cleanup report lists two failed removals and the list is scrolled away from the report and back
- **THEN** the diagnostics log has exactly one entry for each of the two failures

#### Scenario: Same failure later
- **WHEN** removing a worktree fails, the user dismisses the error, and a later removal fails again with the same message
- **THEN** the diagnostics log has one entry for each of the two failures
