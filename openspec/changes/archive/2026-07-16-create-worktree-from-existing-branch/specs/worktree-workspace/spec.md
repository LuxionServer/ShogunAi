## ADDED Requirements

### Requirement: List local branches eligible for a new worktree
The system SHALL let the user see which local branches can become a new worktree, using `ListEligibleBranchesUseCase`, which returns local branches that are not already checked out in any worktree (including the base repository itself).

#### Scenario: Eligible branches loaded successfully
- **WHEN** the user switches the create-worktree dialog to "Existing branch" mode
- **THEN** the app invokes `ListEligibleBranchesUseCase` and displays the returned branch names for selection

#### Scenario: A branch already checked out is excluded
- **WHEN** a local branch is currently checked out in the base repository or in another worktree
- **THEN** `ListEligibleBranchesUseCase` does not include that branch in its result

#### Scenario: Listing eligible branches fails
- **WHEN** `ListEligibleBranchesUseCase` returns a failed `Result` (e.g. `WorktreeError.GitCommandFailed`)
- **THEN** the screen shows an error message derived from the `WorktreeError` instead of a branch list

### Requirement: Create a worktree from an existing branch
The system SHALL let the user create a new worktree for a branch that already exists locally, using `CreateWorktreeFromBranchUseCase`, instead of only being able to create worktrees for brand-new branches. The worktree's directory is derived from the branch name, with `/` replaced by `-`.

#### Scenario: Successful creation
- **WHEN** the user selects "Existing branch" mode, picks a branch from the eligible list, and confirms
- **THEN** the app invokes `CreateWorktreeFromBranchUseCase(branch)`, which runs `git worktree add <path> <branch>` (without creating a new branch), copies the project's secret files into the new worktree, and on success adds the returned `Worktree` to the displayed list

#### Scenario: Creation fails because the branch doesn't exist
- **WHEN** the requested branch does not resolve to an existing local ref
- **THEN** `CreateWorktreeFromBranchUseCase` returns a failed `Result` with `WorktreeError.BranchNotFound` and no worktree is created

#### Scenario: Creation fails because the branch is already checked out elsewhere
- **WHEN** `git worktree add` fails because the requested branch is already checked out in another worktree or in the base repository
- **THEN** `CreateWorktreeFromBranchUseCase` returns a failed `Result` with `WorktreeError.BranchAlreadyCheckedOut` instead of `WorktreeError.GitCommandFailed`, and the screen shows an error message describing the conflict

#### Scenario: Creation fails because the destination directory already exists
- **WHEN** the sanitized branch name maps to a worktree directory that already exists
- **THEN** `CreateWorktreeFromBranchUseCase` returns a failed `Result` with `WorktreeError.WorktreeAlreadyExists` without touching Git

#### Scenario: Creation fails because Git LFS is required but not installed
- **WHEN** `git worktree add` fails and its stderr indicates the repository's `post-checkout` hook could not find `git-lfs` on the user's `PATH`
- **THEN** `CreateWorktreeFromBranchUseCase` removes the partially created worktree (`git worktree remove --force`), best-effort, and returns a failed `Result` with `WorktreeError.GitLfsNotFound` instead of `WorktreeError.GitCommandFailed`

#### Scenario: Creation fails while copying secrets
- **WHEN** copying a secret file into the new worktree fails after `git worktree add` succeeded
- **THEN** `CreateWorktreeFromBranchUseCase` removes the partially created worktree (`git worktree remove --force`), best-effort, and returns a failed `Result` with `WorktreeError.SecretCopyFailed`
