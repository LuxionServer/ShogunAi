# Design

## Context

`git worktree remove` checks the lock before anything else: with fewer than two `--force` it dies with `cannot remove a locked working tree, lock reason: <reason>` (or `cannot remove a locked working tree;` when the lock has no reason), followed by `use 'remove -f -f' to override or unlock first`. A single `--force` only overrides local changes.

`WorktreeFolderRemoval.removeFolder` builds `git worktree remove [--force] <path>`, recognizes the "not a working tree", "filename too long" and "folder in use" failures, and throws `GitCommandFailed` for anything else. The app's `suggestsForceRetry` only matches a `GitCommandFailed` whose output mentions `--force`.

## Decisions

### Second `--force` only after Git refuses the lock

Options considered:

1. Always pass `--force --force` when forcing. Simpler, but every forced removal (the `WorktreeRemove` hook, the cleanup of a failed creation) would also override locks without anyone having seen them, and every existing forced command changes.
2. **Chosen**: keep `--force` once, and when a forced attempt fails because the worktree is locked, retry with `--force --force`. The lock is only overridden when Git reported it and the caller asked to force, and the rest of the removal (folder in use, leftovers) continues with the doubled command.

Without force, the lock failure becomes `WorktreeError.WorktreeLocked(path, reason, command, exitCode, errorOutput)`. The reason is parsed from Git's message (`null` when the lock has none), so it doesn't depend on `git worktree list` having been read.

### The app's force state names the lock

`PendingRemoval` keeps the lock reason when the failure was `WorktreeLocked`. The dialog's force state then says the worktree is locked by another program, shows the reason when there is one, and that forcing removes it anyway; the "unsaved changes" message stays for the local changes case. A forced retry that still fails (for example local changes are not the issue but the folder is in use) shows the error in the dialog as today.

### Cleanup is unchanged

`CleanUpWorktreesUseCase`, `shogun clean` and the groups' cleanup already leave locked worktrees out and remove with `force = false`, so a lock found during cleanup surfaces as `WorktreeLocked` and is never overridden.

## Risks

- Git's message could change between versions. The match uses the stable part (`cannot remove a locked working tree`); if it stops matching, the failure falls back to `GitCommandFailed`, as today.
