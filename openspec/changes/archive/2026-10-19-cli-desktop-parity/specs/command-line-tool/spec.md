# Spec Delta

## ADDED Requirements

### Requirement: Remove a worktree from the command line
`shogun remove <target>` SHALL remove the linked worktree identified by a task id, a path or a branch name. It SHALL refuse a worktree with uncommitted or untracked changes, or with a running agent, unless `--force` is given; it SHALL never remove the main worktree. With `--delete-branch` it SHALL delete the branch only when merged into the main branch. It SHALL stop the resources the repository's providers keep for that worktree, as cleanup does.

#### Scenario: Remove by task id
- **WHEN** the user runs `shogun remove TASK-123` and the worktree is clean
- **THEN** the worktree is removed, its emulator and simulator are stopped if the providers are on, its branch is kept, and the command exits with code 0

#### Scenario: Changes without force
- **WHEN** the worktree has uncommitted changes and the user runs `shogun remove TASK-123`
- **THEN** nothing is removed, the error says the worktree has changes and suggests `--force`, and the command exits with code 1

#### Scenario: Delete a merged branch
- **WHEN** the user runs `shogun remove TASK-123 --delete-branch` and the branch is merged into the main branch
- **THEN** the worktree and its branch are removed

#### Scenario: Ambiguous or unknown target
- **WHEN** the target matches no worktree, or matches more than one
- **THEN** nothing is removed, the error lists the candidates if any, and the command exits with code 1

#### Scenario: Main worktree
- **WHEN** the target is the main worktree
- **THEN** nothing is removed and the command exits with code 1

### Requirement: Repair worktrees from the command line
`shogun repair` SHALL run the same repair the desktop app runs after a repository or its worktrees folder moves, and report the worktrees it re-linked.

#### Scenario: Repository moved
- **WHEN** the repository was moved and the user runs `shogun repair` in it
- **THEN** the worktrees found at their old relative location under the worktrees folder are re-linked and listed, and the command exits with code 0
