## ADDED Requirements

### Requirement: Recognize squash and rebase merges
A branch SHALL read as merged into its base or into the main branch when it is an ancestor of that target, or when the target already holds its changes: every commit of the branch has a commit with the same changes in the target (a rebase merge), or the branch's whole change since it forked has one (a squash merge). A branch whose changes the target doesn't hold SHALL NOT read as merged. The rule SHALL apply to the merged state, cleanup selection and branch deletion.

#### Scenario: Squash merge
- **WHEN** a worktree's branch was merged into `develop` with a squash commit and `develop` gained more commits since
- **THEN** the branch reads as merged into `develop`

#### Scenario: Rebase merge
- **WHEN** a worktree's branch was merged into `develop` by replaying its commits with new hashes
- **THEN** the branch reads as merged into `develop`

#### Scenario: Squash merge into the recorded base
- **WHEN** a worktree's branch was squash-merged into its recorded base `epic/redesign`
- **THEN** the branch reads as merged into `epic/redesign`

#### Scenario: Changes not in the target
- **WHEN** a branch has a commit whose changes no commit of `develop` or of its base holds
- **THEN** it doesn't read as merged

#### Scenario: Commits that cancel out
- **WHEN** a branch adds a file and removes it again, and `develop` has moved on
- **THEN** it doesn't read as merged

## MODIFIED Requirements

### Requirement: Select worktrees to clean up
The system SHALL consider for cleanup every non-main worktree that is merged into its base or into the main branch, including by a squash or rebase merge, and has no uncommitted changes, plus every worktree whose folder is missing. Locked worktrees, the main worktree, worktrees with uncommitted changes, and worktrees whose status isn't loaded SHALL never be selected.

#### Scenario: Merged and clean worktree
- **WHEN** a non-main, unlocked worktree is merged into the main branch and has no uncommitted changes
- **THEN** it is selected for cleanup

#### Scenario: Squash-merged and clean worktree
- **WHEN** a non-main, unlocked worktree's branch was squash-merged into the main branch and it has no uncommitted changes
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

#### Scenario: Merged into its recorded base
- **WHEN** a clean, unlocked worktree's branch is merged into its recorded base `epic/redesign`, which isn't merged into the main branch
- **THEN** it is selected for cleanup

### Requirement: Clean up the selected worktrees
On confirmation, the system SHALL remove each selected worktree whose folder exists without forcing, and prune the records of missing ones. When branch deletion was chosen, a worktree's branch SHALL be deleted only if it is still merged into its base or into the main branch at that moment, including by a squash or rebase merge. One failure SHALL NOT stop the rest. The screen SHALL then report the outcome and reload the list.

#### Scenario: All worktrees removed
- **WHEN** the user confirms and every selected worktree is removed
- **THEN** the screen reports how many worktrees were removed and reloads the list

#### Scenario: Missing folders are pruned
- **WHEN** at least one selected worktree has a missing folder
- **THEN** the cleanup prunes Git's worktree records so those entries disappear from the list

#### Scenario: Branches deleted
- **WHEN** the user chose to delete branches and a removed worktree's branch is contained in the main branch
- **THEN** that local branch is deleted, even if the base repository's checked-out branch doesn't contain it

#### Scenario: Squash-merged branch deleted
- **WHEN** the user chose to delete branches and a removed worktree's branch was squash-merged into the main branch
- **THEN** that local branch is deleted

#### Scenario: Branch no longer merged
- **WHEN** the user chose to delete branches but a branch is no longer merged into the main branch when the cleanup runs
- **THEN** its worktree is removed, the branch is kept, and the report lists it as kept

#### Scenario: One removal fails
- **WHEN** removing one of the selected worktrees fails, for example because it gained uncommitted changes in the meantime
- **THEN** the other worktrees are still removed, and the report lists the failed one with Git's error

#### Scenario: Cleanup in progress
- **WHEN** the cleanup is running
- **THEN** the dialog shows a progress indicator and its actions are disabled until it finishes

#### Scenario: Branch contained in its recorded base
- **WHEN** the user chose to delete branches and a removed worktree's branch is contained in its recorded base `epic/redesign` but not in the main branch
- **THEN** that local branch is deleted
