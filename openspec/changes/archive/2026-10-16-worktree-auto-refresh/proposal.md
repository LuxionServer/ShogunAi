# Proposal

## Why

The worktree list is reloaded when the window regains focus or when the user presses "Reload". With agents working in parallel, the useful moment is exactly when the user isn't touching the app: a worktree appears, an agent commits, a session ends. Keeping the window open next to the terminals should be enough to see it happen. This is the last item of the control tower layer in the roadmap.

## What Changes

- While the worktree screen is open, the app checks the repository's Git metadata every few seconds (worktree records, branches and remote branches, each worktree's index and HEAD) and reloads the list, statuses and overview when it changes: a worktree created or removed by any tool, locked or unlocked, a commit, a checkout, a fetch.
- Running agents are re-detected on every check, so an agent starting or ending in a worktree shows up without a full reload.
- Checks are skipped while the list is already loading or an action (create, remove, clean up) is running, and stop when the user leaves the screen.
- Status reads stop writing to the repository: `git status` runs with `--no-optional-locks`, so reading statuses never changes the metadata being watched and can't trigger a reload loop.

## Capabilities

### New Capabilities
- `worktree-auto-refresh`: keeping the worktree screen up to date with changes made outside the app while it is open.

### Modified Capabilities
- `worktree-status`: reading a worktree's status must not write to the repository.

## Impact

- Domain: `FileManager` gains `modificationStamps(path)`; new `ReadGitMetadataFingerprintUseCase`; `ReadWorktreeStatusUseCase` uses `git --no-optional-locks status --porcelain`.
- UI: `WorktreeViewModel` gains `startWatching`/`stopWatching` with a periodic check; `WorktreeScreen` starts and stops it with its lifecycle.
- Docs: domain, infrastructure and UI architecture pages in both languages; the roadmap item is removed when the change is archived.
