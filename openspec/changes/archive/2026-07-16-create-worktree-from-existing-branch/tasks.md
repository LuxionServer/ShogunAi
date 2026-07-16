## 1. Domain errors and config

- [x] 1.1 Add `WorktreeError.BranchNotFound(branch: String)` to `WorktreeError.kt`
- [x] 1.2 Add `WorktreeError.BranchAlreadyCheckedOut(branch: String, path: String?)` to `WorktreeError.kt`
- [x] 1.3 Add `ProjectConfig.worktreePathForBranch(branch: String)` that sanitizes `/` to `-` and delegates to `worktreePathFor`

## 2. Shared worktree-creation logic

- [x] 2.1 Extract the `git worktree add` + Git LFS rollback + secret copy + rollback logic out of `CreateWorktreeUseCase` into an internal, reusable function parameterized by the full `git worktree add` argv and the branch to roll back on LFS failure
- [x] 2.2 Update `CreateWorktreeUseCase` to call the extracted function, verifying existing tests in `CreateWorktreeUseCaseTest.kt` still pass unchanged

## 3. List eligible branches

- [x] 3.1 Implement `ListEligibleBranchesUseCase`: run `git for-each-ref --format=%(refname:short) refs/heads`, run `git worktree list --porcelain` (reusing `parseWorktreeList`), and return local branches minus those already checked out anywhere
- [x] 3.2 Map a failing `git for-each-ref` or `git worktree list` invocation to `WorktreeError.GitCommandFailed`
- [x] 3.3 Add `ListEligibleBranchesUseCaseTest.kt` covering: all-eligible, some-checked-out-excluded, base-repo-branch-excluded, and command-failure cases

## 4. Create worktree from an existing branch

- [x] 4.1 Implement `CreateWorktreeFromBranchUseCase(branch: String): Result<Worktree>`: validate base repo exists, resolve the destination path via `worktreePathForBranch`, validate destination is free (`WorktreeAlreadyExists`), validate the branch exists locally (`BranchNotFound` otherwise), validate secrets are present, then run `git worktree add <path> <branch>` via the shared helper from task 2.1
- [x] 4.2 Detect the "already checked out" Git error marker in `git worktree add` stderr and map it to `WorktreeError.BranchAlreadyCheckedOut`, extracting the conflicting path when the message shape allows it
- [x] 4.3 Add `CreateWorktreeFromBranchUseCaseTest.kt` covering: success, branch not found, branch already checked out elsewhere, destination already exists, missing secrets, Git LFS failure with rollback, and secret-copy failure with rollback

## 5. Wiring

- [x] 5.1 Add `ListEligibleBranchesUseCase` and `CreateWorktreeFromBranchUseCase` to `WorktreeUseCases` in `AppContainer.kt`

## 6. UI

- [x] 6.1 Add a "New branch" / "Existing branch" mode toggle to the create-worktree dialog in `WorktreeScreen.kt`
- [x] 6.2 In "Existing branch" mode, load and display the eligible branch list on first selection, with a loading/error state consistent with the existing worktree list
- [x] 6.3 Add `WorktreeViewModel.createFromBranch(branch: String)`, mirroring `create()`'s success/failure handling, and wire the dialog's confirm action to it in "Existing branch" mode
- [x] 6.4 Update `WorktreeViewModelTest.kt` with coverage for `createFromBranch` success and failure
