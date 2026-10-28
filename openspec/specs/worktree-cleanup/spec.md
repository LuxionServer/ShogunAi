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
The system SHALL offer a "Clean up" action in the worktree view's header, covering the projects shown in the view, and in each project group's header, covering that project. Each action SHALL be enabled only when at least one worktree in its scope is selected for cleanup. Activating it SHALL show a confirmation dialog listing the selected worktrees grouped by project, with an option to also delete their local branches that is off by default. No Git command SHALL run until the user confirms.

#### Scenario: Nothing to clean up
- **WHEN** no worktree in the action's scope is selected for cleanup
- **THEN** that "Clean up" action is disabled

#### Scenario: Confirmation dialog
- **WHEN** the user activates "Clean up" in "All worktrees" with candidates in `api` and `main-repo`
- **THEN** a dialog lists the candidates under an `api` heading and a `main-repo` heading, with an unchecked "also delete their local branches" option, and nothing is removed until the user confirms

#### Scenario: User cancels
- **WHEN** the user cancels the cleanup dialog
- **THEN** no Git command runs and the list is unchanged

#### Scenario: One project from its group
- **WHEN** the user activates "Clean up" on the `api` group's header
- **THEN** the dialog only lists `api`'s candidates

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

### Requirement: Provider options follow the projects in scope
The cleanup dialog SHALL show each provider option (stop Gradle daemons, stop emulators, delete simulators, delete DerivedData) when at least one project in its scope declares that provider, and SHALL apply it only to the worktrees of projects that declare it.

#### Scenario: Mixed projects
- **WHEN** the cleanup covers `api`, which declares the Gradle provider, and `web`, which doesn't
- **THEN** the dialog offers stopping Gradle daemons, and only daemons of `api`'s removed worktrees are stopped

### Requirement: Clean up from the merged group
When a worktree view is grouped by state, the "Merged" group's header SHALL offer "Clean up", covering the cleanup candidates of the projects in the view, with the same confirmation as the view's "Clean up".

#### Scenario: Clean up merged worktrees
- **WHEN** "All worktrees" is grouped by state and the user activates "Clean up" on the "Merged" group
- **THEN** the cleanup confirmation lists the candidates grouped by project, as the view's "Clean up" does

### Requirement: Keep worktrees with a running agent
Cleanup SHALL never select or remove a worktree where an agent is running, from the desktop app or from `shogun clean`, and SHALL say which worktrees were kept for that reason. When processes can't be inspected on the platform, cleanup SHALL say that running agents couldn't be checked.

#### Scenario: Agent started on a fresh worktree
- **WHEN** a worktree with no commits of its own qualifies as merged and Claude is running in it
- **THEN** it isn't selected or removed, and cleanup says it was kept because an agent is running

#### Scenario: Command line
- **WHEN** the user runs `shogun clean --yes` and an agent runs in one of the qualifying worktrees
- **THEN** the other worktrees are removed and that one is listed as kept, with the agent's name

### Requirement: Take down the Compose projects of removed worktrees
When the repository declares the Docker Compose provider, the cleanup dialog SHALL offer an option, checked by default, to also take down the Compose projects of the worktrees it removes and remove the networks and built images they leave, and a second option, unchecked by default and available only with the first, to remove their volumes too. They run after the removals, and a project or resource that can't be removed SHALL NOT turn the cleanup into a failure.

#### Scenario: Option shown
- **WHEN** the repository declares the Docker Compose provider and the user activates "Clean up"
- **THEN** the dialog shows a checked "also take down their Compose projects" option and an unchecked "also remove their volumes" option

#### Scenario: Repository without the provider
- **WHEN** the repository doesn't declare the provider
- **THEN** the dialog doesn't show the options and Docker isn't run

#### Scenario: Projects taken down
- **WHEN** the first option is checked and a removed worktree had a Compose project with a built image and a volume
- **THEN** that project is taken down, its image is removed, its volume is kept and the report says how many projects were taken down and resources removed

#### Scenario: Volumes removed
- **WHEN** both options are checked
- **THEN** the volumes of the removed worktrees' projects are removed too

#### Scenario: Volumes option depends on the first
- **WHEN** the user unchecks "also take down their Compose projects"
- **THEN** "also remove their volumes" is unchecked and disabled

#### Scenario: Docker unavailable
- **WHEN** Docker isn't reachable when the cleanup runs
- **THEN** the worktrees are still reported as removed and the report says the projects were kept

### Requirement: Finish a half-removed worktree
When Git has already unregistered a worktree but its folder is still on disk, whether because a removal was interrupted by a locked folder or the folder was left behind, removing that worktree, by any removal path including "Clean up", SHALL prune Git's records and delete the leftover folder, and report the worktree as removed. A leftover folder SHALL NOT be deleted when it is the base repository or contains a `.git` folder of its own.

#### Scenario: Retry after a failed removal
- **WHEN** Git unregistered a worktree but couldn't delete its folder, and the user removes it again after the lock is gone
- **THEN** the leftover folder is deleted and the removal succeeds instead of failing with "not a working tree"

#### Scenario: Folder already gone
- **WHEN** a removal targets a path Git no longer lists and whose folder doesn't exist
- **THEN** the removal succeeds without error

#### Scenario: Folder is a repository
- **WHEN** a removal targets a path Git doesn't list as a worktree but that is the base repository or holds its own `.git` folder
- **THEN** nothing is deleted and the removal fails

### Requirement: Report a worktree folder in use
When the worktree's folder can't be deleted because a process is using it, and closing its Herdr workspace and panes (when there are any) didn't free it, the removal SHALL fail with an error that names the folder and says a program is using it, instead of Git's raw output. If Git had already unregistered the worktree, the error SHALL say so. In "Clean up", that worktree SHALL be reported as failed with this error and the rest SHALL still be removed.

#### Scenario: Still locked after closing Herdr
- **WHEN** the folder is still locked after the Herdr workspace was closed and the removal retried
- **THEN** the removal fails with the folder-in-use error and a later removal can finish it

#### Scenario: Cleanup with one locked folder
- **WHEN** "Clean up" removes three worktrees and the folder of one is used by another program
- **THEN** the other two are removed and the report lists the third with the folder-in-use error

### Requirement: Finish a removal blocked by a long path
When Git fails to remove a worktree because a path inside it is too long, the removal, by any removal path including "Clean up", SHALL delete the leftover folder itself, prune Git's records and report the worktree as removed. The folder SHALL NOT be deleted when it is the base repository or contains a `.git` folder of its own; then, or when deleting it fails, the removal SHALL fail.

#### Scenario: Path too long for Git
- **WHEN** removing a worktree fails with Git's "Filename too long" error
- **THEN** the worktree's folder is deleted, Git's records are pruned and the removal succeeds

#### Scenario: Cleanup with a deep build folder
- **WHEN** "Clean up" removes three worktrees and Git can't delete a too-long path in one of them
- **THEN** all three are reported as removed and the report lists no failure

#### Scenario: Folder is a repository
- **WHEN** Git fails with "Filename too long" for a path that is the base repository or holds its own `.git` folder
- **THEN** nothing is deleted and the removal fails with Git's error

#### Scenario: Folder can't be deleted either
- **WHEN** Git fails with "Filename too long" and deleting the leftover folder also fails because a program is using it
- **THEN** the removal fails with the folder-in-use error saying Git already unregistered the worktree

### Requirement: Stop the Maven daemons of removed worktrees
When the repository declares the Maven provider, the cleanup dialog SHALL offer an option, checked by default, to also stop the idle Maven daemons whose last build was in one of the worktrees it removes. Stopping follows the Maven provider's rules for idle daemons, runs after the removals, and a daemon that can't be stopped SHALL NOT turn the cleanup into a failure.

#### Scenario: Option shown
- **WHEN** the repository declares the Maven provider and the user activates "Clean up"
- **THEN** the dialog shows a checked "also stop their idle Maven daemons" option

#### Scenario: Repository without the provider
- **WHEN** the repository doesn't declare the Maven provider
- **THEN** the dialog doesn't show the option and no Maven daemon is stopped

#### Scenario: Daemons stopped
- **WHEN** the option is checked and an idle Maven daemon's last build was in a removed worktree
- **THEN** that daemon is stopped and the report says how many daemons were stopped

#### Scenario: Daemon of a kept worktree
- **WHEN** an idle Maven daemon's last build was in a worktree that isn't removed, or whose removal failed
- **THEN** that daemon isn't stopped

#### Scenario: Busy daemon
- **WHEN** a Maven daemon of a removed worktree is busy when the cleanup runs
- **THEN** it isn't stopped, the worktrees are still reported as removed, and the report says the daemon was kept
