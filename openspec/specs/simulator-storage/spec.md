# simulator-storage Specification

## Purpose
Shows the storage the machine's iOS simulators take outside any worktree (simulator runtimes, devices left without a runtime, dyld caches) and lets the user reclaim it without breaking the simulators ShogunAi's projects need.

## Requirements

### Requirement: Simulator storage only on macOS
The system SHALL read and delete simulator storage only on macOS. On other systems it SHALL report that simulator storage doesn't apply, without running any command.

#### Scenario: Linux
- **WHEN** the simulator storage is requested on Linux
- **THEN** the result says it only applies on macOS and no command is run

### Requirement: List the simulator runtimes
The system SHALL list every simulator runtime installed on the machine, of every platform, with its platform, version, build, size, last use and whether Xcode allows deleting it, and the total size of all runtimes. The size SHALL be the one the simulator tooling reports for the runtime, not the logical size of its disk volumes.

#### Scenario: Two runtimes
- **WHEN** iOS 26.5 (8.5 GB, last used today) and iOS 27.0 (8.1 GB, last used two days ago) are installed
- **THEN** both are listed with those sizes and dates, and the total is 16.6 GB

#### Scenario: No runtime
- **WHEN** no simulator runtime is installed
- **THEN** the list is empty and the total is zero

### Requirement: Who uses each runtime
For each runtime the system SHALL name who uses it: the worktrees whose recorded ShogunAi simulator runs on it, the saved projects that declare the iOS simulator provider and whose machine setting or repository preference names it, and whether it is the runtime ShogunAi would create new simulators with by default (the newest iOS runtime).

#### Scenario: Used by a worktree
- **WHEN** worktree `TASK-123` of `main-repo` holds a simulator that runs on iOS 26.5
- **THEN** iOS 26.5 lists `main-repo · TASK-123` as a user

#### Scenario: Named by a project
- **WHEN** a saved project's repository prefers runtime `iOS 26.5`
- **THEN** iOS 26.5 lists that project as a user

#### Scenario: Default runtime
- **WHEN** iOS 26.5 and iOS 27.0 are installed and nothing names either
- **THEN** iOS 27.0 is marked as the default runtime and iOS 26.5 has no users

### Requirement: Runtimes that can't be deleted
The system SHALL refuse to delete a runtime that has a user, that Xcode doesn't allow deleting, or that has a booted simulator, ShogunAi's or not, and SHALL give the reason for each refused runtime.

#### Scenario: Runtime in use
- **WHEN** the user asks to delete iOS 26.5 and a worktree's simulator runs on it
- **THEN** nothing is deleted and the reason names that worktree

#### Scenario: Booted simulator
- **WHEN** the user asks to delete iOS 26.5 and a simulator not created by ShogunAi is booted on it
- **THEN** nothing is deleted and the reason says a simulator on it is booted

#### Scenario: Bundled with Xcode
- **WHEN** a runtime is marked by Xcode as not deletable
- **THEN** it is listed, it can't be deleted, and the reason says it comes with Xcode

### Requirement: Delete a runtime on explicit request
The system SHALL delete a runtime only when the user names that runtime and confirms, never several runtimes chosen by the system and never as part of a worktree cleanup. It SHALL read the runtime's users again just before deleting it and report the space freed.

#### Scenario: Unused runtime
- **WHEN** the user asks to delete iOS 18.5, which has no users, and confirms
- **THEN** iOS 18.5 is deleted and the space freed is its size

#### Scenario: Used in the meantime
- **WHEN** the user confirms deleting iOS 26.5 and a worktree acquired a simulator on it after the list was read
- **THEN** nothing is deleted and the reason names that worktree

#### Scenario: Deletion fails
- **WHEN** the simulator tooling fails to delete the runtime
- **THEN** the failure is reported with the tooling's message and nothing else is attempted

### Requirement: Unavailable simulator devices
The system SHALL list the simulator devices that are no longer available, ShogunAi's or not, with their count and the total size of their data, and SHALL delete all of them when the user asks and confirms.

#### Scenario: Devices of a deleted runtime
- **WHEN** five devices ran on a runtime that was deleted and their data takes 3 GB
- **THEN** the list reports five unavailable devices taking 3 GB, and after the user confirms they are deleted

#### Scenario: Unavailable ShogunAi simulator
- **WHEN** a worktree's recorded simulator is unavailable and the unavailable devices are deleted
- **THEN** it is deleted with them and the worktree gets a new simulator the next time it starts its simulator

#### Scenario: None unavailable
- **WHEN** every device is available
- **THEN** the count is zero and there is nothing to delete

### Requirement: Offer to delete the devices a runtime leaves behind
After deleting a runtime, the system SHALL offer to delete the unavailable simulator devices, naming how many there are and their size.

#### Scenario: After deleting a runtime
- **WHEN** the user deletes iOS 18.5 and three devices ran on it
- **THEN** the result offers to delete the three unavailable devices

### Requirement: Size of the dyld caches
The system SHALL report the size of the CoreSimulator dyld caches per macOS build folder and mark as stale the folders of a build other than the running macOS build. When a folder can't be read, its size SHALL be reported as unknown. When the caches folder doesn't exist, nothing SHALL be reported.

#### Scenario: Cache of a previous macOS build
- **WHEN** the running macOS build is `26B100` and the caches have folders `26A434` (21 GB) and `26B100` (4 GB)
- **THEN** both are listed with their size and `26A434` is marked as stale

#### Scenario: Only the current build
- **WHEN** the only folder is the running macOS build's
- **THEN** it is listed with its size and nothing is marked as stale

### Requirement: Never act as administrator
The system SHALL NOT delete the dyld caches nor run any command with administrator rights. For each stale dyld cache folder it SHALL give the user the command to remove it, to run themselves.

#### Scenario: Stale cache
- **WHEN** the caches have a stale folder `26A434`
- **THEN** the system shows the command `sudo rm -rf /Library/Developer/CoreSimulator/Caches/dyld/26A434` and doesn't run it
