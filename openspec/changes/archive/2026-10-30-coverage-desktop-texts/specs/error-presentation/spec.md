# Spec Delta

## ADDED Requirements

### Requirement: An error only points to technical details it has
An error whose next step tells the user to check its technical details SHALL show those details; an error that carries no detail SHALL give a next step that doesn't depend on them.

#### Scenario: Background app that fails to launch
- **WHEN** launching a worktree's desktop app in the background fails and the error is shown
- **THEN** its technical details show the launch log's path and its last lines

#### Scenario: Failure without a reason
- **WHEN** stopping a worktree's background app fails without a reason, or a repository configuration can't be read without a detail
- **THEN** the next step says what to try instead of pointing to technical details

#### Scenario: Unreadable local file
- **WHEN** a local file can't be read and the system gives a reason
- **THEN** the reason is shown in the error's technical details
