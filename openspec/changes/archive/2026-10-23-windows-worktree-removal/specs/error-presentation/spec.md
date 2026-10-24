## ADDED Requirements

### Requirement: Folder in use by another program is explained
When a worktree's folder can't be deleted because another program is using it, the error SHALL say in plain words, in the selected language, that a program (a terminal, an editor, a sync client) is using the folder, and suggest closing it and removing the worktree again. When Git had already unregistered the worktree, it SHALL add that removing it again will finish the job. The technical details SHALL keep Git's command and output.

#### Scenario: Windows lock
- **WHEN** removing a worktree fails with `failed to delete '…': Permission denied` and closing Herdr didn't free the folder
- **THEN** the error says a program is using the folder, suggests closing it and trying again, and the technical details hold Git's output

#### Scenario: Unregistered worktree
- **WHEN** the same failure leaves the worktree unregistered in Git
- **THEN** the error also says that removing the worktree again finishes the removal
