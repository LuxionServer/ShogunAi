# Tasks

## 1. Core

- [ ] 1.1 Regression tests first, seen failing: a removal refused because the worktree is locked returns `WorktreeError.WorktreeLocked` with the reason (and `null` without one); a forced removal of a locked worktree retries with `--force --force` and succeeds; a forced removal of an unlocked worktree never doubles `--force`.
- [ ] 1.2 Add `WorktreeError.WorktreeLocked`, the lock failure matcher and reason parser in `GitFailureCause.kt`, and the retry in `WorktreeFolderRemoval`.

## 2. CLI

- [ ] 2.1 `shogun remove` without `--force` on a locked worktree says it is locked, with its reason, suggests `--force` and exits 1; with `--force` it removes it. Unit test plus an end-to-end test with a real locked worktree.
- [ ] 2.2 Mention locked worktrees in `shogun remove`'s help for `--force`.

## 3. Desktop app

- [ ] 3.1 `ProjectWorktrees` escalates to the force state on `WorktreeLocked`, keeping the lock reason. Verify: `ProjectWorktreesTest`.
- [ ] 3.2 The force dialog says the worktree is locked and shows the reason; English and Spanish strings, errors and `ErrorDescription` details. Verify: string tests.

## 4. Validation

- [ ] 4.1 `./gradlew test koverVerify` passes.
- [ ] 4.2 Check in the real app (`shogun headless start`) that the tour's locked worktree is offered the force retry and is removed.
