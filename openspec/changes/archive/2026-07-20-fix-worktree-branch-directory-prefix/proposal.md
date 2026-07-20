## Why

Attaching an existing branch that follows the app's own `feature/<id>` or `fix/<id>` convention (e.g. `feature/TASK-123`) creates a worktree directory named `feature-TASK-123`, even though the new-branch flow for the exact same branch would have created a directory named `TASK-123`. This inconsistency is visible to the user as an unexpected `feature-`/`fix-` prefix in the worktree path and breaks the expectation that re-attaching a branch the app created earlier reproduces the same directory.

## What Changes

- `ProjectConfig.worktreePathForBranch` now strips a leading `feature/` or `fix/` prefix (as defined by `BranchType`) from the branch name before deriving the worktree directory, so `feature/TASK-123` maps to `TASK-123` instead of `feature-TASK-123`.
- Branches with an unrecognized or foreign prefix (e.g. `hotfix/security-patch`, `develop`) keep the existing `/` → `-` sanitization behavior, unchanged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `worktree-workspace`: the "Create a worktree from an existing branch" requirement's directory-derivation rule changes from an unconditional `/` → `-` replacement to first stripping a known `BranchType` prefix, then applying `/` → `-` to whatever remains.

## Impact

- Code: `desktopApp/src/main/kotlin/app/luxion/shogunai/domain/model/ProjectConfig.kt` (`worktreePathForBranch`).
- Tests: `desktopApp/src/test/kotlin/app/luxion/shogunai/domain/usecase/CreateWorktreeFromBranchUseCaseTest.kt` (new case covering prefix stripping).
- No API, dependency, or migration impact — existing worktrees on disk are unaffected; this only changes the directory chosen for worktrees created after the fix.
