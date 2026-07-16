## Context

`WorktreeViewModel` (`ui/worktree/WorktreeViewModel.kt:14-38`) and `ProjectListViewModel` (`ui/projectlist/ProjectListViewModel.kt:10-20`) already expose a `refresh()` method that re-runs the read path (`ListWorktreesUseCase` / `ProjectRepository.list()`), which itself always performs a fresh read (`git worktree list --porcelain`, or re-parsing the JSON catalog file). `refresh()` is only invoked from `init { refresh() }` today.

`AppRoot.kt` already re-runs `ProjectListViewModel.refresh()` every time the `Screen.ProjectList` branch re-enters composition, via `LaunchedEffect(Unit) { viewModel.refresh() }` (`ui/AppRoot.kt:53`). The equivalent branch for `Screen.WorktreeManagement` (`ui/AppRoot.kt:73-82`) has no such effect — because `viewModel(key = current.project.id) { ... }` reuses the same cached `WorktreeViewModel` instance for a given project id, navigating away (to the project list) and back to the same project's worktree screen does not trigger any new read, so the list keeps showing whatever it held before the user navigated away — including entries for worktrees/branches that have since been deleted by another Git client (SourceTree, GitHub after a merged PR, etc.).

Neither screen exposes an explicit, user-triggerable "reload" affordance today; `refresh()` exists in both view models but no button calls it.

## Goals / Non-Goals

**Goals:**
- Let the user manually force a fresh read of the worktree list and the project list at any time via a visible action in each screen.
- Automatically re-read the worktree list every time the user navigates back to a project's worktree screen, mirroring the pattern already used for the project list screen.
- Automatically re-read the active screen's data (worktree list or project list) whenever the OS window regains focus, so changes made in another app (SourceTree, a browser after merging a PR, etc.) while ShogunAI stayed on the same screen are picked up without requiring the user to navigate away and back.
- Give visible feedback (loading indicator, disabled reload action) while a reload is in flight.

**Non-Goals:**
- No file-system watcher, Git hook, or polling timer. These remain unnecessary now that window-focus regain covers the "app stayed open on the same screen" case; polling would be redundant with focus- and re-entry-based reload.
- No new domain use case or port. `ListWorktreesUseCase` and `ProjectRepository.list()` already perform a fresh read on every invocation; the gap is purely in when the UI layer calls them.
- No change to how creation/removal optimistically patch the in-memory list (`WorktreeViewModel.create`/`remove`) — those still avoid a full re-list on success, consistent with current behavior.

## Decisions

**Reload on screen re-entry via `LaunchedEffect`, not `ViewModel` re-construction.** Add `LaunchedEffect(current.project.id) { viewModel.refresh() }` inside the `Screen.WorktreeManagement` branch of `AppRoot.kt`, next to the existing `viewModel(key = current.project.id) { ... }` call. `LaunchedEffect` restarts its coroutine whenever the composable leaves and re-enters composition, so this fires on every navigation back to the screen even though the cached `WorktreeViewModel` instance (and its `viewModelScope`) survives across navigation. This exactly mirrors the existing `LaunchedEffect(Unit) { viewModel.refresh() }` pattern already used for `Screen.ProjectList` (`ui/AppRoot.kt:53`), keeping the two screens consistent instead of introducing a second mechanism (e.g., forcing `ViewModel` recreation by changing the `key`, which would also discard `errorMessage`/`pendingForceRemoval` state unnecessarily and re-run `init`).

**Manual reload as a plain button that calls the existing `refresh()`.** Add a "Recargar" `OutlinedButton` to the header row of `WorktreeScreen.kt` (next to the existing "Proyectos" back button) and `ProjectListScreen.kt` (next to "Nuevo proyecto"), both calling `viewModel.refresh()`. No new ViewModel method is needed since `refresh()` already does the right thing; this keeps the change UI-only for the project list and UI-only plus one `AppRoot.kt` effect for worktrees.

**Guard manual reload against overlap with `isLoading`.** For `WorktreeScreen`, disable the reload button while `viewModel.isLoading` is true (same convention as `enabled = taskId.isNotBlank()` already used on the create button), so rapid double-clicks don't launch overlapping coroutines. `ProjectListViewModel.refresh()` is synchronous, so no equivalent guard is needed there.

**Loading feedback surfaces `isLoading`, which already exists.** `WorktreeViewModel.isLoading` is already set around the `refresh()` body (`WorktreeViewModel.kt:32-36`) but nothing in `WorktreeScreen.kt` reads it yet. Show a small loading indicator (e.g., `CircularProgressIndicator` next to the reload button, or replacing its label) bound to this existing property instead of adding a new one.

**Window-focus reload via `LocalWindowInfo.current.isWindowFocused`, one effect per screen branch.** Compose Multiplatform (`composeMultiplatform = "1.11.1"`, already a project dependency) exposes `androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused` as a composition-local `Boolean` backed by the desktop window's native focus state — no AWT `WindowFocusListener` or new dependency needed. In each branch of `AppRoot.kt`'s `when (screen)` (`Screen.ProjectList` and `Screen.WorktreeManagement`), add a second `LaunchedEffect` alongside the existing re-entry effect:
```kotlin
val windowInfo = LocalWindowInfo.current
LaunchedEffect(current.project.id, windowInfo) {
    snapshotFlow { windowInfo.isWindowFocused }
        .drop(1) // skip the initial value so focus already held on screen entry doesn't double-trigger the re-entry reload
        .filter { it }
        .collectLatest { viewModel.refresh() }
}
```
`drop(1)` avoids firing a redundant reload immediately on screen entry (already covered by the re-entry effect), triggering only on an actual `false → true` transition, i.e. the window genuinely regaining focus after having lost it. `collectLatest` avoids overlapping reloads if focus flickers quickly. This is applied to both `Screen.ProjectList` and `Screen.WorktreeManagement` for consistency — window focus is an app-wide signal, and the user is equally likely to alt-tab away while looking at either screen, even though the original request centered on the worktree screen.

## Risks / Trade-offs

- [Reload on every screen re-entry adds a Git subprocess call each time the user navigates back to a project] → Acceptable: `git worktree list --porcelain` is cheap and local; the project list screen already does the analogous thing for its JSON read with no reported issue.
- [Window-focus reload adds another Git subprocess / JSON read every time the user alt-tabs back into ShogunAI, potentially more often than screen re-entry] → Acceptable for the same reason (cheap, local reads); `collectLatest` plus the `isLoading` guard on the manual button keeps this from stacking up if focus flickers.
- [Adding a reload button while a create/remove action is in flight could race with `refresh()` overwriting an optimistic in-memory update] → Mitigated by the `isLoading` guard on the button; the existing create/remove flows don't set `isLoading`, so this only blocks concurrent reloads against each other, not against create/remove — acceptable since create/remove already don't re-list on success today.
- [`drop(1)` relies on the `LaunchedEffect`'s coroutine subscribing to `snapshotFlow` before the window ever loses focus once] → Correct by construction: the effect restarts on every screen entry (same `key` as the re-entry effect), so `drop(1)` always drops that entry's current focus state, not a stale one from a previous screen visit.
