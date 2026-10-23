# Proposal

## Why

On Windows a folder can't be deleted while a process has it as its current directory. ShogunAi removes a worktree with `git worktree remove` and only afterwards closes its Herdr workspace, so the shells Herdr keeps open in the worktree hold it locked. A Windows user got `error: failed to delete '…/test-3': Permission denied` (exit 255); Git had already unregistered the worktree, leaving an empty, locked folder that a retry can't remove ("not a working tree"). On Unix the order doesn't matter, so this only shows on Windows.

## What Changes

- When `git worktree remove` fails because the folder is locked (permission denied, busy, used by another process) and Herdr holds the worktree open, ShogunAi closes the worktree's Herdr workspace and the panes whose directory is inside the worktree, then retries the removal once. Herdr is never closed up front: a removal that fails for another reason (uncommitted changes without force) leaves the user's session untouched.
- A worktree that Git already unregistered but whose folder remains is finished off: Git's records are pruned and the leftover folder is deleted. A later removal of such a folder succeeds instead of failing with "not a working tree".
- When the folder is still locked after that (another terminal, a sync client), the removal fails with a typed error that says a process is using the folder and what to do, instead of Git's raw output (English and Spanish).
- The Herdr workspace closed during a retry isn't closed again (and doesn't produce a spurious warning) once the removal succeeds.
- The same behaviour applies to the desktop app's remove action, the CLI (`shogun remove`, `shogun clean --yes`, `shogun hook worktree-remove`) and the "Clean up" action, which share one removal path.

## Capabilities

### New Capabilities

### Modified Capabilities
- `herdr-integration`: the removal paths close the worktree's Herdr workspace and panes early, and retry, when a locked folder blocks the removal.
- `worktree-cleanup`: removing and cleaning up recover a half-removed worktree (unregistered, folder left behind) and report a still-locked folder clearly.
- `error-presentation`: a folder in use by another process is recognized and explained specifically.

## Impact

- `core`: `RemoveWorktreeUseCase`, `CleanUpWorktreesUseCase`, `CloseHerdrWorkspacesUseCase`, `GitFailureCause`, `WorktreeError` (new `WorktreeFolderInUse`), `FileManager` (recursive delete) and its implementations and fakes.
- `desktopApp`: English and Spanish error texts; wiring of the new `FileManager` dependency in `AppContainer`.
- `cli`: wiring in `ShogunCli`.
- `docs/` (English and Spanish) page on Herdr/removal; no roadmap items affected.
