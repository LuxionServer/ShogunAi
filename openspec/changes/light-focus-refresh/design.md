# Design

## Context

`AppShell` collects `windowInfo.isWindowFocused` and, on every gain, calls `ShellViewModel.refresh()` (re-reads the catalog, cheap) and `WorktreeBoard.refreshShown()`, which calls `ProjectWorktrees.refresh()` for each shown project. `refresh()` lists the worktrees and `applyListResult` → `loadStatuses` sets every status to `WorktreeStatusState.Loading`, then reads each one (already bounded by `MAX_PARALLEL_STATUS_READS`), and `loadOverview` sets `sizes = emptyMap()` and resubmits every worktree to `SizeQueue`, detects agents and reloads ports, emulators, simulators, Gradle and Xcode sections.

While statuses are `Loading`, `worktreeActivity` returns `Unknown`, which isn't active, so in "Active" those rows leave their groups (unless pinned) until the status comes back, and `isReadingActivity` shows the "reading statuses" line.

The 3-second watcher (`startWatching`/`checkProjects`) already reloads a project when `ReadGitMetadataFingerprintUseCase` changes (base repository `HEAD`, `index`, `packed-refs`, `FETCH_HEAD`, `refs/`, `worktrees/`), and for unchanged projects detects agents and refreshes devices. Edits to working files that aren't staged change none of those, so the focus gain is the only trigger that catches a new uncommitted change count.

## Goals / Non-Goals

**Goals:**
- A focus gain costs one `git status` per listed worktree and one agent detection, nothing more.
- No visible flicker or regrouping on any reload when nothing changed.
- Disk usage isn't measured again for every worktree on every automatic reload.

**Non-Goals:**
- Watching working-tree files (file system events) to replace the focus trigger.
- Changing the 3-second watcher's interval or fingerprint.
- Machine views (`MachineResources`), which already have their own reload.

## Decisions

**Light focus refresh.** Add `ProjectWorktrees.refreshStatuses()`: with the current `worktrees`, re-read each status (same permits, same main branch unless unresolved) and detect agents, updating entries as results arrive. It doesn't list worktrees again; a worktree added or removed from a terminal changes the Git metadata and is picked up by the watcher. `WorktreeBoard.refreshOnFocus()` calls it for each shown holder that isn't busy and whose last load or refresh started more than `FOCUS_REFRESH_MIN_INTERVAL` (5 s) ago, and `AppShell` calls it instead of `refreshShown()`. The catalog re-read (`ShellViewModel.refresh()`) stays: it's a file read. Alternative: keep `refreshShown()` with a debounce; rejected, the full reload is the cost, not its frequency.

**Keep previous values (stale-while-revalidate).** `loadStatuses` keeps the existing state for paths still listed and sets `Loading` only for new paths; paths no longer listed are dropped. A status read that fails still sets `Unavailable`. `sizes` is filtered to listed paths instead of cleared, and a new size replaces the old one when delivered. `isReadingActivity` then only reflects first loads, so the "reading statuses" line and the regrouping disappear on reloads. Results from an older generation are still dropped as today.

**Sizes on automatic reloads.** `loadOverview` takes whether every size must be measured again. A manual reload (`refreshShown()` from the button, View > Reload and the palette) measures all; a reload from the watcher, a create or a repair measures only worktrees without a size. Alternative: measure again every N minutes; left out to keep the change small, the manual reload covers it.

**Agents on focus.** The focus refresh detects agents once for all refreshed projects, like the watcher's `checkProjects`, instead of once per project.

## Risks / Trade-offs

- [A status that changed while the app was unfocused is shown stale for the duration of one `git status`] → Acceptable; it's the previous behavior minus the flicker.
- [Sizes can drift after builds until a manual reload] → Sizes are informative only (detail and project summary); documented in the guide.
- [A worktree created outside the app between two watcher ticks isn't listed by the focus refresh] → The watcher lists it within 3 seconds.
