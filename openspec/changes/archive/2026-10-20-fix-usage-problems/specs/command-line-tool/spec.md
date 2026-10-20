# Spec Delta

## MODIFIED Requirements

### Requirement: Remove a worktree from the command line
`shogun remove <target>` SHALL remove the linked worktree identified by a task id, a path or a branch name. It SHALL refuse a worktree with uncommitted or untracked changes, or with a running agent, unless `--force` is given; it SHALL never remove the main worktree. With `--delete-branch` it SHALL delete the branch only when merged into the main branch, and keep it otherwise, saying why; with `--force-delete-branch` it SHALL delete the branch even if unmerged. A kept branch SHALL NOT make the command fail. With `--json`, `branchDeleted` SHALL name the deleted branch or be `null`, and `branchKeptReason` SHALL say why a requested deletion didn't happen or be `null`. It SHALL stop the resources the repository's providers keep for that worktree, as cleanup does.

#### Scenario: Remove by task id
- **WHEN** the user runs `shogun remove TASK-123` and the worktree is clean
- **THEN** the worktree is removed, its emulator and simulator are stopped if the providers are on, its branch is kept, and the command exits with code 0

#### Scenario: Changes without force
- **WHEN** the worktree has uncommitted changes and the user runs `shogun remove TASK-123`
- **THEN** nothing is removed, the error says the worktree has changes and suggests `--force`, and the command exits with code 1

#### Scenario: Delete a merged branch
- **WHEN** the user runs `shogun remove TASK-123 --delete-branch` and the branch is merged into the main branch
- **THEN** the worktree and its branch are removed

#### Scenario: Unmerged branch kept
- **WHEN** the user runs `shogun remove TASK-123 --delete-branch` and the branch isn't merged into the main branch
- **THEN** the worktree is removed, the branch is kept with a message saying it isn't merged, and the command exits with code 0

#### Scenario: Force-delete an unmerged branch
- **WHEN** the user runs `shogun remove TASK-123 --force-delete-branch` and the branch isn't merged into the main branch
- **THEN** the worktree and the branch are removed

#### Scenario: Kept branch in JSON
- **WHEN** the user runs `shogun remove TASK-123 --delete-branch --json` and the branch is kept
- **THEN** the object has `branchDeleted: null` and `branchKeptReason` with the reason

#### Scenario: Ambiguous or unknown target
- **WHEN** the target matches no worktree, or matches more than one
- **THEN** nothing is removed, the error lists the candidates if any, and the command exits with code 1

#### Scenario: Main worktree
- **WHEN** the target is the main worktree
- **THEN** nothing is removed and the command exits with code 1
