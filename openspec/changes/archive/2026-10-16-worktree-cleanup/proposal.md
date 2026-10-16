# Proposal

## Why

The worktree list now shows which worktrees are merged into the main branch, but removing them still means going one by one through the remove dialog. Worktrees whose folder was deleted by hand also linger in Git's records and show up as unavailable. With several agents creating worktrees every day, cleaning up is the next thing the control tower needs.

## What Changes

- A "Clean up" action on the worktree screen removes, in one go, every worktree that is merged into the main branch and has no uncommitted changes, after a confirmation dialog that lists them. The dialog offers to also delete their local branches (off by default).
- Worktrees whose folder no longer exists are detected from Git, shown as "folder missing" instead of "status unavailable", and pruned (`git worktree prune`) by the same action.
- Locked worktrees (for example the one an agent session is working in) are shown as locked and never cleaned up.
- After cleaning up, the screen reports how many worktrees were removed and lists any that failed or whose branch was kept, and reloads the list.

## Capabilities

### New Capabilities
- `worktree-cleanup`: selecting and removing stale worktrees (merged and clean, or with a missing folder) in one action, and flagging locked and missing worktrees in the list.

### Modified Capabilities

## Impact

- Domain: `Worktree` gains `isLocked` and `isPrunable` from `git worktree list --porcelain`; new selection function and `CleanUpWorktreesUseCase`.
- UI: `WorktreeViewModel` gains the cleanup state and action; `WorktreeScreen` gets the "Clean up" button, its dialog and the locked/missing labels; new strings in English and Spanish.
- Branch deletion during cleanup uses `git branch -D` only after re-checking that the branch is contained in the main branch, so branches merged on the remote are deleted even when the base repository's checked-out branch doesn't contain them.
- Docs: domain and UI architecture pages in both languages; the roadmap's cleanup item is removed when the change is archived.
