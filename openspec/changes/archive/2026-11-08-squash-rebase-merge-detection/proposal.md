## Why

A branch merged through a pull request with "Squash and merge" or "Rebase and merge" never becomes an ancestor of its base: its commits land there with new hashes. ShogunAi only recognizes a branch as merged when its tip is an ancestor of its base or of the main branch, so these branches read as unmerged forever. Cleanup doesn't offer their worktrees, the "Merged" state never shows, they get flagged as inactive, and `--delete-branch`/`--delete-branches` keep them. Squash and rebase are the usual merge methods on GitHub, so most merged worktrees are affected (roadmap, point 7, first item).

## What Changes

- A branch with commits of its own that its base or the main branch is ahead of also reads as merged into that branch when the target already holds its changes: every one of its commits has a patch-equivalent commit there (rebase merge), or the branch's whole change since the fork point has one (squash merge).
- Everything built on "merged" follows: the worktree's merged state in the app and in `shogun status`, cleanup candidates in the app and in `shogun clean`, the inactive flag, and deleting branches in cleanup and in `shogun remove --delete-branch`, which now deletes a branch merged this way.
- A branch whose changes the target doesn't hold, including one whose commits cancel each other out, still reads as unmerged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `worktree-cleanup`: "merged" includes branches merged with squash or rebase, both when selecting worktrees and when deciding whether to delete their branches.

## Impact

- `core`: `WorktreeStatus` (merged state), `ReadWorktreeStatusUseCase` (extra Git reads only for a branch that is both ahead of and behind a target) and `DeleteMergedBranchUseCase`'s shared deletion rule.
- No change to the CLI's or app's code: they read `isMerged` and the deletion outcome.
- Docs: `docs/architecture/domain.md` and `.es.md` describe the rule.
