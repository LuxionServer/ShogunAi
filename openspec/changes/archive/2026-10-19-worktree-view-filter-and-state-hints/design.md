# Design

## Context

`ShellDestination.All(projectFilter)` is both "All worktrees" (`projectFilter == null`) and a project's view (opened from the sidebar, `projectFilter = id`). `AppShell` passes it to `WorktreeBoard.show(projectFilter, mode)`, and `shownProjects` filters by it. `WorktreeScreen` shows `ProjectFilter` only when `!board.isActiveView`, and picking a project calls `ShellViewModel.selectFilter(id)`, which navigates to `All(projectFilter = id)`: the sidebar then marks the project and the header turns into that project's view.

State groups come from `worktreeActivity` (`core/.../WorktreeOverview.kt`) and `groupByState`; `StateGroupHeader` in `WorktreeListPane.kt` renders the title, the count and, for "Merged", "Clean up".

## Goals / Non-Goals

**Goals:**
- One project filter that narrows "Active" and "All worktrees" in place, and none in a project's view.
- Every state group says what it means where the user sees it.

**Non-Goals:**
- Changing the state rules themselves (e.g. a branch without own commits counted as merged, already on the roadmap).
- Persisting the local filter across restarts.
- Hints on row indicators and chips; rows already say what they show ("3 changes", "merged into main").

## Decisions

**Local filter in the board.** Add `viewProjectFilter: Map<GroupedView, String?>` to `WorktreeBoard`, one per view, held in memory for the session. `shownProjects` uses the destination's project when there is one, otherwise the current view's local filter, otherwise every project. `selectFilter` stops navigating: the header calls `board.updateViewProjectFilter(id)` directly, so the sidebar keeps "Active" or "All worktrees" selected. The create dialog's default project and "Clean up" follow `shownProjects`, as today. A local filter pointing to a project that was removed is cleared. Alternative: persist it per view like the grouping; rejected, a forgotten filter after a restart hides worktrees without an obvious reason.

**Visibility.** The filter is shown when the destination has no project (`Active` and `All()`), hidden in a project's view. Selecting a worktree from the palette into "All worktrees" clears the local filter when it excludes that worktree's project, so the selection is always visible.

**`ShellViewModel.selectFilter`.** Removed if nothing else uses it after the change; the palette's "New worktree in <project>" keeps navigating to the project's view.

**Group hints.** `StateGroupHeader` shows, after the title and before the count, a hint in `bodySmall` and `onSurfaceVariant`, and wraps the title and hint in `WithTooltip` with the full criterion. Strings (English / Spanish):

| Group | Hint | Tooltip |
|---|---|---|
| Agent working | agent running / agente en marcha | An AI agent is running in the worktree. |
| Needs you | uncommitted or unpushed / sin commit o sin subir | Uncommitted changes, or commits not pushed to its upstream (or to the main branch when it has none). |
| In progress | pushed, not merged / subido, sin fusionar | Clean, pushed, not merged and with a commit in the last 14 days. |
| Merged | in main branch / ya en la rama principal | No commits of its own ahead of the main branch. Can be cleaned up. |
| Inactive | idle for 14+ days / sin commits en 14 días | Clean, no agent and no commit in the last 14 days. |
| Folder missing | folder deleted / carpeta borrada | Git still lists it but its folder no longer exists. Can be cleaned up. |
| Other | main or not read yet / principal o sin leer | The main worktree when it's clean, or a worktree whose status isn't read yet. |

The hint truncates with an ellipsis before the count or "Clean up" do on narrow widths. The tooltip texts are the source for the guide's "States" section, so both say the same thing.

## Risks / Trade-offs

- [Users used to picking a project in the header to jump to its view] → The sidebar still does that; the header now filters in place, which is what the header filter is for.
- [Two "project" concepts (destination and local filter)] → Only one is visible at a time: the filter is hidden in a project's view.
- [Hints on narrow windows] → Truncated first; the tooltip keeps the full text.
