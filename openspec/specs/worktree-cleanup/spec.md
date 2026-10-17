# worktree-cleanup Specification

## Purpose
Lets the user remove stale worktrees of a project in one action (merged and clean, or whose folder no longer exists) while protecting worktrees that are locked or still have work in them.

## Requirements

### Requirement: Flag locked and missing worktrees
The system SHALL show in the worktree list which worktrees are locked and which ones have a folder that no longer exists, as reported by Git. A missing worktree SHALL be labeled as such instead of showing its status as unavailable.

#### Scenario: Locked worktree
- **WHEN** Git reports a worktree as locked, for example because an agent session is working in it
- **THEN** its row shows a "locked" label

#### Scenario: Missing folder
- **WHEN** Git reports a worktree as prunable because its folder no longer exists
- **THEN** its row shows a "folder missing" label instead of "status unavailable"

### Requirement: Select worktrees to clean up
The system SHALL consider for cleanup every non-main worktree that is merged into the main branch and has no uncommitted changes, plus every worktree whose folder is missing. Locked worktrees, the main worktree, worktrees with uncommitted changes, and worktrees whose status isn't loaded SHALL never be selected.

#### Scenario: Merged and clean worktree
- **WHEN** a non-main, unlocked worktree is merged into the main branch and has no uncommitted changes
- **THEN** it is selected for cleanup

#### Scenario: Merged worktree with uncommitted changes
- **WHEN** a merged worktree has uncommitted changes
- **THEN** it is not selected for cleanup

#### Scenario: Locked worktree
- **WHEN** a merged and clean worktree is locked
- **THEN** it is not selected for cleanup

#### Scenario: Worktree still loading or unmerged
- **WHEN** a worktree's status is still loading, unavailable, or not merged
- **THEN** it is not selected for cleanup

#### Scenario: Missing folder
- **WHEN** a worktree's folder is missing and it isn't locked
- **THEN** it is selected for cleanup

### Requirement: Confirm before cleaning up
The system SHALL offer a "Clean up" action on the worktree screen, enabled only when at least one worktree is selected for cleanup. Activating it SHALL show a confirmation dialog listing the selected worktrees, with an option to also delete their local branches that is off by default. No Git command SHALL run until the user confirms.

#### Scenario: Nothing to clean up
- **WHEN** no worktree is selected for cleanup
- **THEN** the "Clean up" action is disabled

#### Scenario: Confirmation dialog
- **WHEN** the user activates "Clean up"
- **THEN** a dialog lists the selected worktrees with an unchecked "also delete their local branches" option, and nothing is removed until the user confirms

#### Scenario: User cancels
- **WHEN** the user cancels the cleanup dialog
- **THEN** no Git command runs and the list is unchanged

### Requirement: Clean up the selected worktrees
On confirmation, the system SHALL remove each selected worktree whose folder exists without forcing, and prune the records of missing ones. When branch deletion was chosen, a worktree's branch SHALL be deleted only if it is still contained in the main branch at that moment. One failure SHALL NOT stop the rest. The screen SHALL then report the outcome and reload the list.

#### Scenario: All worktrees removed
- **WHEN** the user confirms and every selected worktree is removed
- **THEN** the screen reports how many worktrees were removed and reloads the list

#### Scenario: Missing folders are pruned
- **WHEN** at least one selected worktree has a missing folder
- **THEN** the cleanup prunes Git's worktree records so those entries disappear from the list

#### Scenario: Branches deleted
- **WHEN** the user chose to delete branches and a removed worktree's branch is contained in the main branch
- **THEN** that local branch is deleted, even if the base repository's checked-out branch doesn't contain it

#### Scenario: Branch no longer merged
- **WHEN** the user chose to delete branches but a branch is no longer contained in the main branch when the cleanup runs
- **THEN** its worktree is removed, the branch is kept, and the report lists it as kept

#### Scenario: One removal fails
- **WHEN** removing one of the selected worktrees fails, for example because it gained uncommitted changes in the meantime
- **THEN** the other worktrees are still removed, and the report lists the failed one with Git's error

#### Scenario: Cleanup in progress
- **WHEN** the cleanup is running
- **THEN** the dialog shows a progress indicator and its actions are disabled until it finishes

### Requirement: Stop the Gradle daemons of removed worktrees
When the repository declares the Gradle provider, the cleanup dialog SHALL offer an option, checked by default, to also stop the idle Gradle daemons whose last build was in one of the worktrees it removes. Stopping follows the Gradle provider's rules for idle daemons, runs after the removals, and a daemon that can't be stopped SHALL NOT turn the cleanup into a failure.

#### Scenario: Option shown
- **WHEN** the repository declares the Gradle provider and the user activates "Clean up"
- **THEN** the dialog shows a checked "also stop their idle Gradle daemons" option

#### Scenario: Repository without the provider
- **WHEN** the repository doesn't declare the Gradle provider
- **THEN** the dialog doesn't show the option and no daemon is stopped

#### Scenario: Daemons stopped
- **WHEN** the option is checked and an idle Gradle daemon's last build was in a removed worktree
- **THEN** that daemon is stopped and the report says how many daemons were stopped

#### Scenario: Daemon of a kept worktree
- **WHEN** an idle Gradle daemon's last build was in a worktree that isn't removed, or whose removal failed
- **THEN** that daemon isn't stopped

#### Scenario: Busy daemon
- **WHEN** a daemon of a removed worktree is busy when the cleanup runs
- **THEN** it isn't stopped, the worktrees are still reported as removed, and the report says the daemon was kept

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

### Requirement: Delete the simulators of removed worktrees
When the repository declares the iOS simulator provider, the cleanup dialog SHALL offer an option, checked by default, to also delete the simulators of the worktrees it removes and the orphaned ShogunAi simulators, listing the orphans. Deleting runs after the removals, and a simulator that can't be deleted SHALL NOT turn the cleanup into a failure.

#### Scenario: Option shown
- **WHEN** the repository declares the provider and the user activates "Clean up"
- **THEN** the dialog shows a checked "also delete their iOS simulators" option, with any orphaned simulators listed

#### Scenario: Simulators deleted
- **WHEN** the option is checked and a removed worktree had a simulator
- **THEN** the simulator is deleted and the report says how many were deleted

#### Scenario: Cleanup without candidates but with orphans
- **WHEN** no worktree qualifies for cleanup but orphaned ShogunAi simulators exist
- **THEN** "Clean up" is enabled and the dialog offers to delete the orphans

### Requirement: Delete orphaned DerivedData when cleaning up
When the repository declares the Xcode provider, the cleanup dialog SHALL offer an option, checked by default, to also delete orphaned DerivedData, with the number and size of the orphans already found. Orphans SHALL be looked for again after the removals, so the folders of the removed worktrees are included, and a folder that can't be deleted SHALL NOT turn the cleanup into a failure.

#### Scenario: Option shown
- **WHEN** the repository declares the Xcode provider and the user activates "Clean up"
- **THEN** the dialog shows a checked "also delete orphaned Xcode DerivedData" option

#### Scenario: Removed worktree's DerivedData
- **WHEN** the option is checked and a removed worktree had DerivedData
- **THEN** that folder is deleted after the removal and the report includes the space freed

#### Scenario: Cleanup without candidates but with orphans
- **WHEN** no worktree qualifies for cleanup but orphaned DerivedData exists
- **THEN** "Clean up" is enabled and the dialog offers to delete the orphans

#### Scenario: Repository without the provider
- **WHEN** the repository doesn't declare the Xcode provider
- **THEN** the dialog doesn't show the option and no DerivedData is deleted

### Requirement: Provider options state their scope
The cleanup dialog's options to stop Gradle daemons, stop emulators and delete simulators SHALL say that they only affect the worktrees being removed, so the user doesn't expect the emulators, simulators or daemons of kept worktrees to be touched. The simulator option SHALL also say that it deletes orphaned ShogunAi simulators.

#### Scenario: Emulator option wording
- **WHEN** the dialog shows the emulator option
- **THEN** its label says it stops the emulators of the worktrees being removed

#### Scenario: Gradle option wording
- **WHEN** the dialog shows the Gradle daemon option
- **THEN** its label says it stops the idle daemons of the worktrees being removed

#### Scenario: Simulator option wording
- **WHEN** the dialog shows the simulator option
- **THEN** its label says it deletes the simulators of the worktrees being removed and the orphaned ShogunAi simulators
