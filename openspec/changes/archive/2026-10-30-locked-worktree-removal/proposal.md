# Proposal

## Why

Removing a locked worktree fails with a raw Git error. Orca leaves worktrees under `.orca-preparing/` locked with `orca-create-preparation:…` when the process creating them dies, and Git refuses to remove them:

```
git worktree remove <path>  →  exit 128
fatal: cannot remove a locked working tree, lock reason: orca-create-preparation:v1:…
use 'remove -f -f' to override or unlock first
```

`WorktreeFolderRemoval` doesn't recognize this failure, so it surfaces as `GitCommandFailed`. The desktop app only offers its force retry when Git's output mentions `--force` (local changes), so the user has no way out from the app. Even a forced removal fails: Git needs `--force` twice to remove a locked worktree, and Shogun passes it once, so `shogun remove --force` fails too.

## What Changes

- A typed `WorktreeError.WorktreeLocked(path, reason)` when `git worktree remove` fails because the worktree is locked, with the lock reason Git gives, in English and Spanish, with its technical details.
- A forced removal of a locked worktree retries with `--force --force` after Git refuses the lock, so the app's "Force deletion" and `shogun remove --force` remove it. Unlocked worktrees keep a single `--force`.
- The removal dialog escalates to its force state when the worktree is locked, saying it is locked and showing the lock reason instead of the "unsaved changes" message.
- `shogun remove` without `--force` says the worktree is locked, with its reason, and suggests `--force`.
- Cleanup (`shogun clean` and the app's cleanup) keeps skipping locked worktrees and never forces them: no change there.

## Capabilities

### Modified Capabilities

- `worktree-workspace`: removing a locked worktree offers the force retry, which removes it.
- `command-line-tool`: `shogun remove` refuses a locked worktree unless `--force` is given, and removes it with `--force`.

## Impact

- Core: `WorktreeError`, `GitFailureCause.kt`, `WorktreeFolderRemoval`.
- CLI: `shogun remove`'s error for a locked worktree and its help text for `--force`.
- Desktop app: `ProjectWorktrees` (force retry and lock reason), `RemoveWorktreeDialog`, English and Spanish strings and errors, `ErrorDescription`.
