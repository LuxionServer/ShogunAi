# Design

## Context

After `all-worktrees-view`, `WorktreeBoard` holds a `ProjectWorktrees` per project and `WorktreeViewState` holds the selection and filters. The views render rows built by `worktreeRowModel` in project groups. Statuses carry `uncommittedChanges`, `upstream` and `mainBranch` divergences and `isMerged`. `isInactive(...)` and the cleanup selection already exist as pure domain functions.

## Goals / Non-Goals

**Goals:**
- A definition of "active" that matches the questions "is an agent on it?", "is work waiting for me?" and "is it still in progress?", and that is testable as a pure function.
- Rows that don't jump between groups or vanish while the user looks at them.

**Non-Goals:**
- Notifications when an agent finishes. That's a candidate for the roadmap once "Active" exists.
- Pull request state per branch (roadmap, "Under exploration").

## Decisions

### Pure classification next to the domain's other rules
`worktreeActivity(worktree, status, agents): Activity` lives in `core` next to `isInactive` and `selectCleanupCandidates`, so the CLI can reuse it later (`shogun status --active`). It returns `AgentWorking`, `NeedsYou`, `InProgress`, `Merged`, `Inactive`, `Missing`, `Idle` (the clean main worktree) or `Unknown` (status not read, no agent). "Active" shows the first three. "Needs you" means uncommitted changes, or commits ahead of the upstream, or commits ahead of the main branch with no upstream at all. *Alternative considered:* computing activity in the UI layer. Rejected because the rule is product logic that the CLI and future notifications need too.

### Selected rows are pinned, not frozen
The board keeps `pinnedRef`: while a worktree is selected in "Active", it's included even if `worktreeActivity` no longer marks it active. It moves to the group for its real state, so the user sees the change, instead of a stale snapshot. Deselecting drops the pin.

### Grouping as a view option
`Grouping.ByState | ByProject` per view, stored in `settings.json` as `grouping.active` and `grouping.all`. A segmented selector in the view's header switches it. In "By state", each row shows its project's name as secondary text. Group headers in "By state" carry only the name and count, except "Merged", which carries "Clean up".

### Sidebar counts from the board
The sidebar reads counts from the board (`activeCount`, `activeCountByProject`, `agentRunningByProject`), so they follow the same refresh as the rows. Counts are drawn with `dataSmall`. The agent indicator is a small dot in the info color with a tooltip naming the agents.

### Default destination
`ShellDestination.Active`. The restore logic from `app-shell-layout` falls back to `Active` instead of the first project, and the empty-catalog state still takes precedence.

## Risks / Trade-offs

- [Statuses arrive one by one after startup, so "Active" fills up progressively] → Rows are added as statuses arrive and the groups are stable in order. The view shows a loading line in its header until every shown project's statuses are read.
- [A long-lived feature branch that nobody touches for 13 days still counts as "In progress"] → It matches the existing 14-day inactivity rule, and the user sees its last commit time on the row.
