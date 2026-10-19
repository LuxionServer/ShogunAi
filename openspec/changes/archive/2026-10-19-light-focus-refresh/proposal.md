# Proposal

## Why

Used daily on projects with dozens of worktrees, the app feels heavy every time its window regains focus. Each focus gain reloads every shown project in full: the worktree list, every status, agents, ports, devices, Gradle and Xcode sections, and the disk usage of every worktree, which is the most expensive part. Before the new values arrive it also resets every status to "loading" and clears every size, so rows flicker and, in "Active", worktrees whose status is being read drop out of their groups and come back. Most of that work is redundant: the 3-second watcher already reloads a project when its Git metadata changes and refreshes agents and devices on every check. The only thing a focus gain adds is noticing file edits that haven't touched the index.

## What Changes

- Regaining focus runs a light refresh of the shown projects: it reads the statuses of the listed worktrees and the running agents again, and nothing else. It is skipped for a project that was reloaded or refreshed a few seconds earlier, or that is busy.
- Every reload keeps the previous statuses and sizes on screen until the new ones arrive. Only worktrees seen for the first time show a loading state, so rows don't flicker and "Active" doesn't regroup while statuses are read again.
- Automatic reloads (Git metadata changed) measure disk usage only for worktrees without a known size; "Reload" (button, View > Reload, palette) measures every worktree again.
- Docs describing the focus reload are updated (both languages).

## Capabilities

### New Capabilities

### Modified Capabilities
- `worktree-auto-refresh`: light refresh when the window regains focus.
- `worktree-status`: statuses already read stay shown while they are read again.
- `worktree-overview`: the overview isn't reloaded on focus; sizes stay shown and are only measured again on a manual reload or for new worktrees.

## Impact

- `desktopApp`: `AppShell.kt` (focus handler), `WorktreeBoard.kt` (`refreshShown`, new focus refresh), `ProjectWorktrees.kt` (`refresh`, `loadStatuses`, `loadOverview`, sizes), `SizeQueue.kt` if needed.
- `core`: none expected.
- Tests: `ProjectWorktreesTest`, `WorktreeBoardTest`.
- Docs: `docs/architecture/ui.md`/`ui.es.md`, `docs/guide/desktop-app.md`/`desktop-app.es.md`.
