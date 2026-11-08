# Design

## Context

"Active" builds its rows in `WorktreeBoard.stateGroups`, a `groupByState` over one `ActivityItem<WorktreeEntry>` per worktree. The multirepo view already knows a multirepo's groups (`groupedBranches`, `ChangeGroup`, `combinedState`), shows them as sticky `ChangeGroupHeader`s, and resolves "Add repository" from `shownMultirepo`, so its group actions only work inside that view. Other views mark a group's rows with a `GroupTag` built from every membership (`groupTags`).

## Goals / Non-Goals

**Goals:**
- Reuse the multirepo view's grouping, combined state and group actions instead of a second definition of a change.
- Keep the flat per-worktree `stateGroups` semantics for every view except "Active" grouped by state.

**Non-Goals:**
- Change rows in "All worktrees", project views or "Active" grouped by project: a change spans projects, so it has no single project group to sit in.
- Persisting which change rows are expanded across restarts.
- Showing a change's Git or time summary in its row; the child rows carry them.

## Decisions

### A state item is a worktree or a change

`stateGroups` becomes `List<StateGroup<StateItem>>`, where `StateItem` is `Worktree(entry)` or `Change(group, expanded)`, each exposing the worktree entries it holds. Only "Active" grouped by state produces `Change` items; the other views wrap every worktree in `Worktree`, so their behavior doesn't change. One list keeps one source for the list items, the keyboard order (`visibleRefs`), collapse handling, the empty state and the counts, rather than a second "Active only" property that would drift from it.

Alternative: keep `stateGroups` per worktree and fold the changes in `WorktreeListPane`. Rejected: selection, keyboard navigation and collapsing live in the board and would need the same folding again.

### Combined state as an activity

`combinedState(kinds)` becomes `combinedActivity(activities)`: "Merged" only when all are merged, otherwise the most urgent non-merged activity by its badge order, `Unknown` for none. The badge order lists the active states first, so a change is active exactly when one of its worktrees is. `groupByState` can then place a change like any worktree, and `ChangeGroup.state` is `combinedActivity(...).badgeKind()`, so the header in the multirepo view and the row in "Active" can't disagree.

### Groups know their multirepo

`ChangeGroup` gains `multirepoFolder`. "Active" builds its changes from every membership, as `groupTags` does, and keys its expanded set by folder and branch, since two multirepos can share a branch name. `missingAliases` and `addRepositoryToGroup` resolve the membership from the group's folder instead of `shownMultirepo`; in the multirepo view both are the same folder.

### Expanded set, selection and filtering

The board keeps `expandedChanges: Set<ChangeKey>` in memory, empty at start (collapsed by default). `select(ref)` expands the change holding `ref`, as it already expands a collapsed state group; collapsing the change of the selected worktree clears the selection, as collapsing a state group does. While a text filter is active, every change with a matching worktree is treated as expanded and lists only its matching entries, as the multirepo view does. A change stays listed while one of its worktrees is selected, through the existing `isPinned` hook.

Sorting reuses `sortWorktreeItems`: a change sorts by its branch as name, and by the most recent activity date of its worktrees.

### The change row

A new composable built on `DataRow` with the worktree row's badge, execution and name columns, so the badge and agent line up with the rows around it; its name cell holds the expand chevron, the title and the `branch · aliases` detail, and its action slot is a "⋯" menu with the group catalog's primary action, its overflow entries and "Show in multirepo". The row's click toggles expansion; it is never the selection, since the detail pane shows one worktree. "Show in multirepo" builds the group's `GroupTag` and reuses the existing `onOpenGroup` navigation.

Child rows are the usual worktree rows with the alias column first, which also indents them under their change. `NameCell` gains an optional `label` that replaces the branch on the first line (the branch stays in the tooltip); child rows set it to the project's name and pass no project and no group tag.

### Coverage

The new logic (state items, combined activity, expanded set, folder-based group actions, list items) lives outside composables and is covered by `WorktreeBoardTest`, `ChangeGroupingTest` and list item tests. The change row composable is covered by a UI test like `MultirepoViewUiTest`. No new exclusion and no new `@Serializable` class.

## Risks / Trade-offs

- [Parallel desktop changes touch `WorktreeListPane` and `WorktreeRow`] → Keep the edits there small and additive; resolve conflicts when catching up with `develop` before the pull request.
- [A collapsed change hides which repository needs the user] → The row shows the combined badge and every agent, and the state comes from the most urgent worktree; expanding is one click and selecting from elsewhere expands it.
- [Changing `stateGroups`' item type touches many existing tests] → Mechanical: tests read the entries through `StateItem.entries`.
