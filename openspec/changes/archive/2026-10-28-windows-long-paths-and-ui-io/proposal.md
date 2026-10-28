# Proposal

## Why

A diagnostic report from a Windows 11 user on 0.9.3 shows three bugs that are still on `develop`. Removing a worktree fails about forty times with `error: failed to delete '<path>': Filename too long`, leaving it half-removed, because Git for Windows can't delete paths longer than 260 characters unless `core.longpaths` is on, and build outputs (`build/intermediates`, `node_modules`) easily go past it. The interface froze for 22 seconds because the machine section's summary refresh reads the settings file and runs the Gradle daemon trim on the interface thread. And every error shown in a cleanup report is written twice to the diagnostics log, which makes the log harder to read and fills it faster.

## What Changes

- On Windows, every Git command runs with long path support turned on, so Git can create, read and delete files whose path is longer than 260 characters inside a worktree. Other systems are unchanged.
- When removing a worktree still fails because a path is too long, ShogunAi deletes the leftover folder itself and prunes Git's records, with the same safety checks as for a half-removed worktree (never the base repository, never a folder with its own `.git` directory), and reports the worktree as removed.
- The machine section reads its use cases (daemon lists, pools, trims, device states, DerivedData, Compose projects, the desktop turn) off the interface thread and only writes the results back on it, so a slow disk or a slow command never freezes the window.
- An error shown to the user is recorded once in the diagnostics log, however many times the notice that shows it is composed again (scrolling, reloading the list, the same error shown in two places).

## Capabilities

### New Capabilities

### Modified Capabilities
- `command-execution`: Git commands run with long path support on Windows.
- `worktree-cleanup`: a removal that fails because a path is too long finishes by deleting the leftover folder.
- `machine-resources`: reading the machine resources never blocks the interface.
- `local-diagnostics`: each shown error is recorded once.

## Impact

- `core`: `ProcessBuilderShellCommandExecutor` (Git arguments on Windows), `WorktreeFolderRemoval` (the "Filename too long" fallback).
- `desktopApp`: `MachineResources` (use cases run on an IO dispatcher), `AppContainer` (the desktop turn reads off the interface thread, the shown-error log), `ErrorNotice.kt` and `CleanupDialog.kt` (record through a log that remembers what it already recorded), `main.kt` (provides it).
- Tests: executor argument tests, `WorktreeFolderRemoval`/removal and cleanup use case tests with a fake executor, `MachineResourcesTest`, a shown-error log test and the existing diagnostics UI test.
- Docs: `docs/architecture/infrastructure.md`, `domain.md`, `ui.md` and their Spanish translations.
