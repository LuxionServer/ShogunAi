# Spec Delta

## MODIFIED Requirements

### Requirement: Delete the branch of worktrees removed from Herdr
For Herdr's `worktree.removed` event, `shogun hook herdr` SHALL read the removed worktree's `data.worktree.path` and `data.worktree.branch`, find its repository from `data.workspace.worktree.repo_root` or else from the ShogunAi project whose worktrees folder contains the path, and, when that repository has a ShogunAi project, delete the branch only if it is merged into the project's main branch, keeping it otherwise.

#### Scenario: Merged branch
- **WHEN** Herdr removes a worktree of a repository with a ShogunAi project and its branch is merged into the main branch
- **THEN** the branch is deleted and the command exits with code 0

#### Scenario: Unmerged branch
- **WHEN** the removed worktree's branch has commits that aren't merged into the main branch, even if it is merged into its upstream
- **THEN** the branch is kept, a note saying so goes to standard error, and the command exits with code 0

#### Scenario: Repository without a ShogunAi project
- **WHEN** the removed worktree's repository has no ShogunAi project
- **THEN** no branch is deleted and the command exits with code 0

#### Scenario: Detached worktree
- **WHEN** the event has no `data.worktree.branch`
- **THEN** nothing is deleted and the command exits with code 0

#### Scenario: Repository can't be found
- **WHEN** the event has no `data.workspace.worktree.repo_root` and no ShogunAi project's worktrees folder contains the path
- **THEN** a note goes to standard error and the command exits with code 0

#### Scenario: Missing path
- **WHEN** the `worktree.removed` event has no `data.worktree.path`
- **THEN** the command prints an error on standard error and exits with code 2

#### Scenario: Removal started from ShogunAi
- **WHEN** ShogunAi removes a worktree and closes its Herdr workspace
- **THEN** Herdr emits no `worktree.removed` event and the hook doesn't run
