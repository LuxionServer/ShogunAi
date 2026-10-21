# Proposal

## Why

The state groups don't separate what matters. A worktree with an agent process is "Agent working" whatever else is true, so an agent left open hides most of the other worktrees. "In progress" mixes worktrees that were never started with worktrees whose work is pushed and waiting. The row's date is the last commit, which in a new worktree is inherited from the base branch and reads as activity. Fourteen days of silence before a worktree counts as inactive is far too long. And the row's information is a run of chips whose number and order change from one worktree to the next, so nothing lines up and two worktrees can't be compared at a glance.

## What Changes

- Worktree states are redefined, in priority order: "Needs you" (an agent is blocked or done, or no agent is working and there are uncommitted changes or unpushed commits), "Agent working", "New" (no commits of its own, no changes and no agent), "Stopped" (commits of its own, everything pushed, no agent), then "Merged", "Inactive" and "Folder missing". **BREAKING** for the group names: "In progress" disappears, replaced by "New" and "Stopped".
- An agent no longer hides pending work just by being open: an idle agent doesn't decide the state, so a worktree with an idle agent and uncommitted changes needs the user. A working agent, or one whose state is unknown, still means "Agent working".
- The row's date says what it measures: "commit 3 h ago" when the branch has commits of its own, "created 2 d ago" otherwise, from the worktree's creation time.
- The inactivity threshold drops to 3 days by default and becomes a setting. It is measured from that same date. New worktrees aren't flagged as inactive.
- Each view gets a sort selector in its header, remembered per view: natural alphabetical order by default (`TASK-9` before `TASK-10`), or by last activity.
- The row becomes a two-line grid where every piece of information has a fixed cell, kept even when empty: a state badge (colored circle with a state icon, accessible without color), an execution column (agent state above, emulator or simulator state below), the name column (branch above; folder when it differs from the branch, origin and, in views grouped by state across projects, the project below), a Git column (uncommitted changes above, divergence or merged below) and a time column (date above, main and locked marks below). Chips leave the row.
- In a narrow list the execution column collapses to icons only, keeping the state colors (the agent icon keeps changing color with its state).
- The tray menu's marks and the sidebar's counts follow the new states, without changing what they promise.

## Capabilities

### New Capabilities
- `worktree-row`: the layout of a worktree's row in every worktree view: its cells, what each shows, how it adapts to the grouping and to a narrow list, and its accessibility.

### Modified Capabilities
- `worktree-views`: the state groups, what counts as active, the group hints, the sort selector, and the row context requirement (moved to `worktree-row`).
- `worktree-overview`: the inactivity rule (threshold, the date it is measured from, new worktrees excluded) and its setting.
- `worktree-status`: the row shows the activity date with what it measures, and reads the worktree's creation time.

## Impact

- `core`: `worktreeActivity` and `Activity`, `isInactive`, `WorktreeStatus` (creation time, own commits), `ReadWorktreeStatusUseCase`, settings (`inactiveAfterDays`, sort per view); tests.
- `desktopApp`: `WorktreeRow`, `WorktreeRowModel`, `WorktreeGrouping`, `WorktreeBoard`, the view headers (sort selector), Settings (inactivity), `TrayMenuModel`, the sidebar's activity, localized strings (English and Spanish); UI tests.
- `cli`: `shogun status` keeps its JSON fields; `lastCommitAt` stays, and a creation time is added.
- `docs/`: the guide pages describing the worktree list, states and settings, in both languages; the roadmap item is removed when archiving.
