## ADDED Requirements

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
