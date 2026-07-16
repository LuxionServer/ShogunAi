## Why

ShogunAI loads a project's worktree list and the project catalog once, when a screen's `ViewModel` is first created, and never re-queries Git or disk afterward. When a worktree or its branch is deleted outside the app — e.g. a branch removed after merging a PR, or a worktree deleted from SourceTree or another Git client — the app keeps showing stale data with no way for the user to bring it back in sync short of restarting the app.

## What Changes

- Add a manual "Reload" action to the worktree management screen, wired to the existing `WorktreeViewModel.refresh()`, so the user can force a fresh `ListWorktreesUseCase` query at any time.
- Add the equivalent manual "Reload" action to the project list screen, wired to the existing `ProjectListViewModel.refresh()`.
- Make the worktree management screen automatically re-run `refresh()` every time it becomes the active screen for a project (not only on first `ViewModel` construction), so navigating back from the project list picks up changes made externally while the user was away.
- Automatically re-run `refresh()` on the active screen (project list or worktree management) whenever the OS window regains focus, so switching back from another app (SourceTree, a browser after merging a PR, etc.) picks up external changes even without navigating between screens.
- Surface the existing `isLoading` state during a reload (manual or automatic) so the user has visible feedback while the list is being refetched.

## Capabilities

### New Capabilities

(none — this extends the existing worktree and project-catalog capabilities rather than introducing a new one)

### Modified Capabilities

- `worktree-workspace`: adds a requirement to manually, automatically (on screen re-entry), and automatically (on window focus regain) reload the worktree list so externally-made changes (branch/worktree deletion via another Git client or a merged PR) are reflected without restarting the app.
- `project-catalog`: adds a requirement to manually and automatically (on window focus regain) reload the project list from persisted storage.

## Impact

- `ui/worktree/WorktreeViewModel.kt`, `ui/worktree/WorktreeScreen.kt`: expose the reload action and loading indicator.
- `ui/projectlist/ProjectListViewModel.kt`, `ui/projectlist/ProjectListScreen.kt`: expose the reload action.
- `ui/AppRoot.kt`: trigger `WorktreeViewModel.refresh()` when the worktree management screen becomes active for a project, not just on `ViewModel` construction; add a `LocalWindowInfo`-based effect in both the `Screen.ProjectList` and `Screen.WorktreeManagement` branches to reload the active screen when the window regains focus.
- No changes to domain use cases (`ListWorktreesUseCase`, `ProjectRepository`) — both already perform a fresh read on every call.
