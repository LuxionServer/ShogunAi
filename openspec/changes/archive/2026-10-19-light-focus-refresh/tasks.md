# Tasks

## 1. Keep previous values while reloading

- [x] 1.1 In `ProjectWorktrees.loadStatuses`, keep the states of paths still listed and set `Loading` only for new paths; drop paths no longer listed; add a `ProjectWorktreesTest` case (reload keeps `Loaded` until the new status arrives, a new worktree starts as `Loading`) and verify it passes
- [x] 1.2 Filter `sizes` to the listed paths instead of clearing them, replacing each size when the new one is delivered; add a test and verify it passes
- [x] 1.3 Verify with a `WorktreeBoardTest` case that "Active" keeps a worktree in its group and `isReadingActivity` stays false during a reload of an already loaded project

## 2. Sizes on automatic reloads

- [x] 2.1 Measure every size again only on a manual reload, and only worktrees without a size on reloads from the watcher, a create or a repair; add tests for both paths and verify they pass

## 3. Light refresh on focus

- [x] 3.1 Add `ProjectWorktrees.refreshStatuses()` that re-reads the statuses of the current worktrees without listing them again; add a test (no list call, statuses updated, no `Loading`) and verify it passes
- [x] 3.2 Add `WorktreeBoard.refreshOnFocus()` that refreshes the shown, non-busy projects not loaded or refreshed in the last 5 seconds and detects agents once for all of them; add tests (busy project skipped, recent project skipped, one agent detection) and verify they pass
- [x] 3.3 Call `refreshOnFocus()` from the focus handler in `AppShell` instead of `refreshShown()`, keeping the catalog re-read
- [x] 3.4 Validate in the running app, on the secondary display, with a project of 30+ worktrees: focusing the window doesn't flicker rows or regroup "Active", an unstaged edit shows up after refocusing, and "Reload" still measures sizes again

## 4. Docs

- [x] 4.1 Update the focus and reload descriptions in `docs/architecture/ui.md`/`ui.es.md` and `docs/guide/desktop-app.md`/`desktop-app.es.md`, and verify `mkdocs build --strict` passes
- [x] 4.2 Run `./gradlew test` and verify it passes
