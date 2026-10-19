# Proposal

## Why

The desktop app keeps polling every 3 seconds while it sits in the background, and each check costs more than it should: it checks the Git metadata of every loaded project instead of only the shown ones, and each device refresh re-reads `.shogunai.json` and runs `git rev-parse` once per worktree. With twenty worktrees in an Android and iOS repository that is tens of processes every few seconds while the user works in another window. Some file work after use cases also runs on the UI thread. `reduce-adb-polling` and `light-focus-refresh` (2026-10-19) already slowed device reads and made the focus refresh light; this change finishes the job.

## What Changes

- The worktree watcher pauses while the main window isn't focused or is minimized, and the existing light refresh on focus catches up when the user comes back.
- Each check reads the Git metadata only of the projects shown in the current view, as the spec already states; agent detection keeps covering every saved project for the sidebar indicator.
- Device state reads reuse what they already know about each worktree (its Git folder and its repository file) until the worktree list or the repository file changes.
- Provisioning file copies and provider reads run off the UI thread.

## Capabilities

### New Capabilities

### Modified Capabilities
- `worktree-auto-refresh`: checks pause while the window isn't focused; only shown projects have their metadata checked.

## Impact

- `desktopApp`: `WorktreeBoard.kt` (watcher loop, check scope), `AppShell.kt` (window focus and minimized state), `ProjectWorktrees.kt` (dispatchers for provider reads and creation).
- `core`: `ResolveWorktreeEnvironmentUseCase` and the emulator and simulator state reads (per-worktree cache), `WorktreeCreation` file steps on the IO dispatcher.
- Tests: `WorktreeBoardTest` and the state read use case tests.
