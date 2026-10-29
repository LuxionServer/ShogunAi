## MODIFIED Requirements

### Requirement: Select worktrees to clean up
The system SHALL consider for cleanup every non-main worktree that is merged into its base or into the main branch and has no uncommitted changes, plus every worktree whose folder is missing. Locked worktrees, the main worktree, worktrees with uncommitted changes, and worktrees whose status isn't loaded SHALL never be selected.

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

#### Scenario: Merged into its recorded base
- **WHEN** a clean, unlocked worktree's branch is merged into its recorded base `epic/redesign`, which isn't merged into the main branch
- **THEN** it is selected for cleanup

### Requirement: Clean up the selected worktrees
On confirmation, the system SHALL remove each selected worktree whose folder exists without forcing, and prune the records of missing ones. When branch deletion was chosen, a worktree's branch SHALL be deleted only if it is still contained in its base or in the main branch at that moment. One failure SHALL NOT stop the rest. The screen SHALL then report the outcome and reload the list.

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

#### Scenario: Branch contained in its recorded base
- **WHEN** the user chose to delete branches and a removed worktree's branch is contained in its recorded base `epic/redesign` but not in the main branch
- **THEN** that local branch is deleted
