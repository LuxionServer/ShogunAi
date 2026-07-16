## Why

Right now `CreateWorktreeUseCase` only knows how to create a worktree for a brand-new branch (`git worktree add <path> -b <branch>`). When work has already started on a branch that lives only in the base repository (e.g. someone committed directly there instead of opening a worktree first, or picked up a teammate's branch), there's no way to give that branch its own worktree — the user would have to fall back to running `git worktree add` by hand outside the app. This change closes that gap so any existing local branch can be moved into a dedicated worktree from the UI.

## What Changes

- Add a use case that creates a worktree for an existing local branch (`git worktree add <path> <branch>`, no `-b`), reusing the same secret-file copy and Git LFS rollback behavior as `CreateWorktreeUseCase`.
- Add a use case to list local branches eligible to become a worktree (i.e. not already checked out in another worktree, including the base repository itself).
- Add typed errors for the two new failure modes: the requested branch doesn't exist, and the requested branch is already checked out somewhere (Git only allows one worktree per branch).
- Derive the new worktree's directory name from the branch name (slashes replaced with hyphens) instead of a task id, since existing branches don't necessarily follow the `feature/<task-id>` convention.
- Extend the worktree management screen with a way to pick "existing branch" as an alternative to "new branch" when creating a worktree, backed by the branch list above.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `worktree-workspace`: adds a requirement to create a worktree from an existing local branch (instead of only creating new branches), and a requirement to list which local branches are eligible for that flow.

## Impact

- `desktopApp/src/main/kotlin/app/luxion/shogunai/domain/usecase/CreateWorktreeUseCase.kt`: extract the shared "git worktree add + copy secrets + rollback" logic so it can be reused by the new use case without duplicating the Git LFS / rollback handling.
- `desktopApp/src/main/kotlin/app/luxion/shogunai/domain/usecase/`: new `CreateWorktreeFromBranchUseCase` and `ListEligibleBranchesUseCase`.
- `desktopApp/src/main/kotlin/app/luxion/shogunai/domain/model/WorktreeError.kt`: new `BranchNotFound` and `BranchAlreadyCheckedOut` variants.
- `desktopApp/src/main/kotlin/app/luxion/shogunai/domain/model/ProjectConfig.kt`: new path-derivation helper for an arbitrary branch name (as opposed to `worktreePathFor(taskId)`).
- `desktopApp/src/main/kotlin/app/luxion/shogunai/ui/worktree/`: `WorktreeScreen.kt` and `WorktreeViewModel.kt` gain the existing-branch creation flow.
- `WorktreeUseCases` (dependency wiring) gains the two new use cases.
