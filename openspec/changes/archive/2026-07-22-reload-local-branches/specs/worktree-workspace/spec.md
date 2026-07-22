## MODIFIED Requirements

### Requirement: List local branches eligible for a new worktree
The system SHALL let the user see all local branches, using `ListLocalBranchesUseCase`, which returns every local branch paired with whether it is already checked out in a worktree (including the base repository itself).

#### Scenario: Local branches loaded successfully
- **WHEN** the user switches the create-worktree dialog to "Existing branch" mode
- **THEN** the app invokes `ListLocalBranchesUseCase` and displays every returned branch, each flagged with whether it is already checked out

#### Scenario: A branch already checked out is shown but disabled
- **WHEN** a local branch is currently checked out in the base repository or in another worktree
- **THEN** the branch still appears in the dropdown, rendered disabled with a note that it already has a worktree, instead of being omitted

#### Scenario: Listing local branches fails
- **WHEN** `ListLocalBranchesUseCase` returns a failed `Result` (e.g. `WorktreeError.GitCommandFailed`)
- **THEN** the screen shows an error message derived from the `WorktreeError` instead of a branch list

### Requirement: Reload the worktree list
The system SHALL let the user manually trigger a fresh read of the active project's worktree list via a visible "Reload" action on the worktree management screen, SHALL automatically re-run this read every time the user navigates back to the worktree management screen for a project, and SHALL automatically re-run this read whenever the OS window regains focus while the worktree management screen is active, so that worktrees or branches deleted outside the app (e.g. via another Git client, or a branch deleted after merging a pull request) are reflected without restarting the app. Whenever the reload is triggered while the create-worktree form is in "Existing branch" mode, the system SHALL also reload the local branch list, so branches created or updated outside the app are reflected without restarting it.

#### Scenario: User manually reloads the worktree list
- **WHEN** the user activates the "Reload" action on the worktree management screen
- **THEN** the app invokes `ListWorktreesUseCase` again and updates the displayed list to match its result

#### Scenario: Manual reload also refreshes local branches in "Existing branch" mode
- **WHEN** the user activates the "Reload" action while the create-worktree form is in "Existing branch" mode
- **THEN** the app also invokes `ListLocalBranchesUseCase` again and updates the displayed branch options to match its result

#### Scenario: Manual reload does not fetch branches in "New branch" mode
- **WHEN** the user activates the "Reload" action while the create-worktree form is in "New branch" mode
- **THEN** the app does not invoke `ListLocalBranchesUseCase`

#### Scenario: Worktree screen re-entered after an external change
- **WHEN** the user navigates away from a project's worktree management screen and back to it again
- **THEN** the app invokes `ListWorktreesUseCase` again, so a worktree or branch removed externally while the user was away no longer appears in the displayed list

#### Scenario: Application window regains focus while the worktree screen is active
- **WHEN** the worktree management screen is the active screen and the application window transitions from unfocused to focused (e.g. the user switches back from another app)
- **THEN** the app invokes `ListWorktreesUseCase` again, so a worktree or branch removed externally while the app was unfocused no longer appears in the displayed list

#### Scenario: Reload is in progress
- **WHEN** a reload (manual or triggered by re-entering the screen) is in progress
- **THEN** the screen shows a loading indicator and disables the manual "Reload" action until the reload completes

#### Scenario: Reload fails
- **WHEN** `ListWorktreesUseCase` returns a failed `Result` during a reload
- **THEN** the screen shows an error message derived from the failure, consistent with the initial load's error handling

### Requirement: Create a worktree from an existing branch
The system SHALL let the user create a new worktree for a branch that already exists locally, using `CreateWorktreeFromBranchUseCase`, instead of only being able to create worktrees for brand-new branches. The worktree's directory is derived from the branch name: if the branch starts with a known `BranchType` prefix (`feature/` or `fix/`), that prefix is stripped first; the remaining string then has every `/` replaced with `-`.

#### Scenario: Successful creation
- **WHEN** the user selects "Existing branch" mode, picks a branch not already checked out from the list, and confirms
- **THEN** the app invokes `CreateWorktreeFromBranchUseCase(branch)`, which runs `git worktree add <path> <branch>` (without creating a new branch), copies the project's secret files into the new worktree, and on success adds the returned `Worktree` to the displayed list

#### Scenario: Directory matches the new-branch flow for a known branch type prefix
- **WHEN** the requested branch starts with a known `BranchType` prefix, e.g. `feature/TASK-123`
- **THEN** the worktree directory is `<worktreesRoot>/TASK-123` (the prefix stripped), the same directory `CreateWorktreeUseCase` would have produced for that task id and branch type, instead of `<worktreesRoot>/feature-TASK-123`

#### Scenario: Creation fails because the branch doesn't exist
- **WHEN** the requested branch does not resolve to an existing local ref
- **THEN** `CreateWorktreeFromBranchUseCase` returns a failed `Result` with `WorktreeError.BranchNotFound` and no worktree is created

#### Scenario: Creation fails because the branch is already checked out elsewhere
- **WHEN** `git worktree add` fails because the requested branch is already checked out in another worktree or in the base repository
- **THEN** `CreateWorktreeFromBranchUseCase` returns a failed `Result` with `WorktreeError.BranchAlreadyCheckedOut` instead of `WorktreeError.GitCommandFailed`, and the screen shows an error message describing the conflict

#### Scenario: Creation fails because the destination directory already exists
- **WHEN** the derived branch name maps to a worktree directory that already exists
- **THEN** `CreateWorktreeFromBranchUseCase` returns a failed `Result` with `WorktreeError.WorktreeAlreadyExists` without touching Git

#### Scenario: Creation fails because Git LFS is required but not installed
- **WHEN** `git worktree add` fails and its stderr indicates the repository's `post-checkout` hook could not find `git-lfs` on the user's `PATH`
- **THEN** `CreateWorktreeFromBranchUseCase` removes the partially created worktree (`git worktree remove --force`), best-effort, and returns a failed `Result` with `WorktreeError.GitLfsNotFound` instead of `WorktreeError.GitCommandFailed`

#### Scenario: Creation fails while copying secrets
- **WHEN** copying a secret file into the new worktree fails after `git worktree add` succeeded
- **THEN** `CreateWorktreeFromBranchUseCase` removes the partially created worktree (`git worktree remove --force`), best-effort, and returns a failed `Result` with `WorktreeError.SecretCopyFailed`
