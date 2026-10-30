# Design

## Context

Each worktree has one `Activity`, computed by `worktreeActivity` in `core` (`domain/usecase/WorktreeOverview.kt`) and used by the desktop app's state groups (`WorktreeGrouping.kt`), the row's badge (`WorktreeRowModel.kt`), the sidebar's counts (`WorktreeBoard.activeCount`/`activeCountOf`), the tray's marks (`TrayMenuModel.activityMark`), the change groups' combined state (`ChangeGrouping.combinedState`) and Orca's board and comment (`OrcaWorktreeSync.kt`, shared with the CLI).

Today the order is: `Missing`; `NeedsYou` (agent blocked or done); `AgentWorking`; `Unknown`; `NeedsYou` (uncommitted, or ahead of the upstream, or ahead of the base without an upstream); `Idle` (main); `New`; `Merged`; `Inactive` (which requires a clean worktree); `Stopped`. Because the Git-based `NeedsYou` comes before the main checkout and inactivity checks, the main checkout with a stray file and long-abandoned branches pile up in "Needs you", next to the agents that really wait.

The row's Git cell shows `status.base?.arrows()` as its second line, so "↑" there is ahead of the base, while the inspector's label line already uses the upstream (`InspectorModels.kt`). State groups have no collapse toggle; project groups and change groups do.

Decisions taken with the user while planning:
- "Idle worktrees" means inactive ones, and inactivity no longer requires a clean worktree.
- The calm "Nothing needs you" shows whenever nothing is "Waiting for you", above the other groups, not only when "Active" is empty.

## Goals / Non-Goals

**Goals:**
- Separate "an agent waits for me" from "there is Git work left", with the sidebar's counts measuring only the first.
- One meaning for "↑" in the row: commits not pushed.
- A grouping that the next change (one row per multirepo change in "Active") can extend with a parent row per change without reworking the states.

**Non-Goals:**
- The multirepo parent row itself, the row and inspector layout, the toolbar and the header summary (parallel changes).
- Persisting the collapsed state groups across restarts.
- Counting agents in multirepo folders in the calm notice.
- Changing what `shogun status` prints.

## Decisions

### Two activities instead of `NeedsYou`

```kotlin
enum class Activity { WaitingForYou, AgentWorking, Unfinished, New, Stopped, Merged, Inactive, Missing, Idle, Unknown }
```

The order of the enum is the order of urgency used by `StateGroupKind` and `combinedState` (which takes the `minBy` ordinal of the badge kinds), so `Unfinished` sits after `AgentWorking`: a change group with one repository working and another unfinished reads "Agent working". `isActive` covers the first five.

`worktreeActivity` becomes:

```
Missing                       if prunable
WaitingForYou                 if a deciding agent waits (blocked or done)
AgentWorking                  if any deciding agent
Unknown                       if status not read
Idle                          if main worktree
Inactive                      if isInactive(...)
Unfinished                    if uncommitted > 0, upstream.ahead > 0, or no upstream and hasOwnCommits
New                           if no own commits and not merged
Merged                        if merged
Stopped                       otherwise
```

The main checkout still reaches `WaitingForYou` and `AgentWorking` through its agents: what waits is the agent, wherever it runs. It only leaves the Git-based group. Its row's ✎ comes from the Git cell, which doesn't depend on the activity, so it is kept without extra work.

"No upstream and own commits" replaces "no upstream and ahead of the base": with a base, `hasOwnCommits` is `base.ahead > 0`, the same test; without one it falls back to "not unborn", which `orcaComment` already uses for "branch not pushed". One private `hasUnpushedWork(status)` serves both.

Alternative considered: keep `NeedsYou` and add a sub-reason. Rejected: the groups, badges, tray and counts all switch on the state, and a sub-reason would have to be threaded through each of them.

### Inactivity no longer requires a clean worktree, and excludes merged branches

`isInactive` drops `uncommittedChanges == 0` and adds `!status.isMerged`. The merged check keeps "Merged" winning for old merged branches now that `Inactive` is tested before `Merged`. Cleanup doesn't use `isInactive` (it has its own candidates and keeps worktrees with changes), so this doesn't make anything removable.

Risk: the activity date of a worktree with uncommitted changes is its last commit, so someone editing for days without committing and without an agent sees it go "Inactive". Accepted: with no agent and no commit for the threshold, the work is idle by the app's own definition, the ✎ stays visible, and the threshold is configurable.

### State groups

`StateGroupKind` gets `WAITING_FOR_YOU` and `UNFINISHED` in place of `NEEDS_YOU`, with the hint and criterion strings for each. `StateGroupHeader` gets `collapsed` and `onToggle`, drawn with the same chevron `IconAction` as the change group header, and the title of `WAITING_FOR_YOU` in `Status.WARNING` with the badge's icon (`Icons.Default.PriorityHigh`). `StateBadgeKind.UNFINISHED` is `Status.NEUTRAL` with `Icons.Default.Edit`, matching the ✎ of the Git cell.

`WorktreeBoard` keeps `collapsedStates: Map<Boolean, Set<StateGroupKind>>` keyed by `isActiveView`, starting with `{true: {STOPPED}, false: {}}`, kept for the session only (the existing `collapsedChanges` is session-only too). `StateGroup` gains `collapsed`; `stateListItems` emits the header and, unless collapsed, the rows. Selecting a worktree whose state group is collapsed expands it (as `select` does for collapsed projects), collapsing the selected worktree's group clears the selection, and the navigation order skips collapsed groups' rows, as for projects.

For the multirepo parent row that comes next, the grouping stays a pure function `groupByState(items, …)` over `ActivityItem`s; a parent row can be one item whose activity is the combined state, so the follow-up only adds an item type and doesn't touch the states.

### Sidebar counts

`activeCount`/`activeCountOf` become `waitingCount`/`waitingCountOf`, counting `Activity.WaitingForYou`. `ProjectActivity.activeCount` is renamed `waitingCount`. Projects and folders already hide a zero count; `ViewRow` takes a nullable count and "Active" passes `null` for zero, so a calm sidebar shows no "0". "All worktrees" keeps its total.

Both the view and the projects change, so the numbers add up: a folder or project count is part of the "Active" number.

### Git cell

`GitCell` gains `notPushed: Boolean`. The second line is, in order: merged label; `status.upstream?.arrows()`; nothing. `notPushed` is set when there is no upstream, the branch isn't merged and `hasOwnCommits`. `GitCellView` draws `Icons.Default.CloudOff` at the cell's icon size in `onSurfaceVariant`, with `strings.branchNotPushed` as tooltip and content description. The arrows' tooltip names what they mean (`strings.upstreamArrowsTooltip(ahead, behind)`) instead of the base's name.

The inspector keeps its base and main-branch divergences. `statusProperties` adds an upstream property reading `strings.branchNotPushed` when the upstream is missing and the branch has own commits and isn't merged, and the label line appends "not pushed" in that case.

### Calm notice

`WorktreeBoard.isNothingWaiting` is true in the Active view when no text filter is active, the activity isn't being read, no shown project failed to load, and no listed worktree is `WaitingForYou`. `workingAgentCount` counts the agents of the listed worktrees whose state is working or unknown (the agents that make `AgentWorking`).

`WorktreeScreen` replaces `NothingActive` with `NothingNeedsYou`: a neutral row (`bodyMedium` title "Nothing needs you", `bodySmall` "2 agents working"/"No agents working" in `onSurfaceVariant`, and a "See all worktrees" text link), drawn at the top of the list's column. When the list is empty it is the only content, centered as the empty states are, with the same pieces and no `Inbox` illustration. The old `isNothingActive`, `nothingActiveTitle` and `nothingActiveMessage` go away.

### Tray, Orca and the tour

- Tray: "!" only for `WaitingForYou`.
- Orca: `WaitingForYou` and `Unfinished` → `in-progress`, as `NeedsYou` was; comment states "waiting for you" and "unfinished".
- Tour: the `needs-you` sample worktree is renamed `unfinished` (`feature/unfinished`), and the script line says it shows under "Unfinished". Its branch has no commits of its own, so it can't be inactive.

## Risks / Trade-offs

- [Parallel changes edit `WorktreeRow`, `WorktreeRowModel`, `WorktreeListPane` and the roadmap] → keep the edits local to the Git cell, the badge kinds and the state header; resolve conflicts when bringing in `develop` before the pull request.
- [Hiding Git work from the counts may make it easy to forget] → it stays in "Active" under "Unfinished", and the ✎/↑/not-pushed marks are unchanged or clearer.
- [Users who learned "Needs you"] → the badge, the group and the docs all switch together; the guide explains both groups.

## Migration Plan

No stored data changes. Orca cards already in `in-progress` stay there; their comment changes on the next sync.
