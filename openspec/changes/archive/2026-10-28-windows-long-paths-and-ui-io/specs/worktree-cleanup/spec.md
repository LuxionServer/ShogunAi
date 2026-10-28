## ADDED Requirements

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
