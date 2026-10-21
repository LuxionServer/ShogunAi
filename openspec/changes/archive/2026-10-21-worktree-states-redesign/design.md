# Design

## Context

A worktree's state is computed once, in `core` (`worktreeActivity` in `domain/usecase/WorktreeOverview.kt`), and used by the state groups (`WorktreeGrouping.kt`), the "Active" filter, the sidebar's counts and the tray menu's marks (`TrayMenuModel`). Its first rule is "any agent process → Agent working", which is what lets an open agent hide everything else. The row (`WorktreeRow.kt`) is one line with a dot, the branch, a `folder · project · origin` context and a list of chips built by `chipsOf` in `WorktreeRowModel.kt`; the date is `WorktreeStatus.lastCommitAt`, the date of `HEAD`'s commit.

This change is the first of two. The second, `agent-state-hooks`, adds a Claude Code hook that reports agent states outside Herdr and a command that installs the hooks. Until it lands, an agent seen only in the process list has no state.

## Goals / Non-Goals

**Goals:**
- One state per worktree, with "Needs you" holding only what waits for the user, and new worktrees told apart from stopped ones.
- A row where every piece of information has a fixed place, without losing anything the chips show today.
- A date that says what it measures, and an inactivity threshold that is short and configurable.
- Natural name order by default, activity order on demand, per view.

**Non-Goals:**
- Knowing the state of agents outside Herdr (change `agent-state-hooks`).
- Notifications when an agent becomes blocked or done.
- Changing what cleanup offers: merged worktrees and missing folders stay the candidates; inactivity doesn't make a worktree a candidate.
- Redesigning the detail pane beyond showing the creation date.

## Decisions

### States and their priority

`Activity` becomes `NeedsYou, AgentWorking, New, Stopped, Merged, Inactive, Missing, Idle, Unknown` (`InProgress` disappears). `worktreeActivity` applies, in order:

1. `isPrunable` → `Missing`.
2. Any agent blocked or done → `NeedsYou`.
3. Any agent working, or without a state → `AgentWorking`. An agent always has uncommitted changes while it works, so they can't send it to "Needs you".
4. No status → `Unknown`.
5. Uncommitted changes, or unpushed commits (ahead of the upstream, or of the main branch without an upstream) → `NeedsYou`.
6. Main worktree → `Idle`.
7. No commits of its own → `New`.
8. Merged → `Merged`.
9. Inactive → `Inactive`.
10. Otherwise → `Stopped`.

Idle agents are skipped by rules 2 and 3, so they never decide the state, but they still count as running for inactivity and cleanup.

"Commits of its own" is `mainBranch.ahead > 0`. Without a resolved main branch it is "not `isNotStarted`", so the worktree is `New` only when Git's record shows the branch was never updated. `isNotStarted` keeps its current meaning for cleanup and `shogun status`; `New` is wider (a branch at the main branch's tip is new even if its reflog is missing).

Group order is `NEEDS_YOU, AGENT_WORKING, NEW, STOPPED, MERGED, INACTIVE, FOLDER_MISSING, OTHER`; `isActive` covers the first four.

Alternative considered: keep "uncommitted changes" under "Stopped" and leave "Needs you" for agents only. Rejected by the user: pending work is something to act on.

### Activity date and creation time

`WorktreeStatus` gains `createdAt: Instant?`. `ReadWorktreeStatusUseCase` reads it without a Git process: the linked worktree's `.git` file points (`gitdir: <path>`) to its administrative folder `.git/worktrees/<name>`, and the creation time is the modification time of its `commondir` file, which `git worktree add` writes once and Git never rewrites. It is read through the `FileManager` port; a failure leaves `createdAt` null. The main worktree has no such folder.

A pure function `activityDate(worktree, status): ActivityDate?` returns `ActivityDate(at, kind)` with `kind` `COMMIT` or `CREATED`, following the rule in the spec. The row, the "Last activity" order and the inactivity check all use it, so they can't disagree.

Alternative considered: the branch's first reflog entry. Rejected: a worktree created from an existing branch would show the branch's age, not the worktree's.

### Inactivity threshold

`INACTIVITY_THRESHOLD` is replaced by a parameter: `isInactive` and `worktreeActivity` take `inactiveAfter: Duration`. The app's `Settings` gains `inactiveAfterDays: Int = 3`, edited in Settings with a number field limited to 1–90; `WorktreeBoard` reads it from the settings state, so a change recomputes the groups without reading statuses again. `shogun status` doesn't report inactivity today and doesn't start to.

### Sorting

`Settings` gains `sorting: SortingSettings(active, all, project)` with `WorktreeSort { NAME, ACTIVITY }`, `NAME` by default; every project's view shares the `project` key, as a single project's view shares its grouping rule. The selector sits next to `GroupingSelector` in `WorktreeScreen`'s header, in every view.

`NAME` uses a natural comparator over the branch label: case-insensitive, splitting digit runs and comparing them by numeric value, with the full label as a tie-breaker so the order is total. `ACTIVITY` sorts by `activityDate` descending, worktrees without a date last. The main worktree goes first in both, replacing `orderMainFirst`.

### Row layout

`WorktreeRowModel` stops building chips; it exposes one value per cell: `badge: StateBadge`, `execution: ExecutionCell(agent, device)`, `name: NameCell(branch, context)`, `git: GitCell`, `time: TimeCell(activity, marks)`. `WorktreeRow` lays them out as a `Row` of fixed-width cells, each a two-line `Column` with fixed line heights, so an empty cell still takes its space.

| Cell | Width | Line 1 | Line 2 |
|---|---|---|---|
| Badge | 20 dp | state icon in a colored circle | — |
| Execution | 136 dp, 44 dp compact | agent icon, name and state, "+N" | device icon and state |
| Name | remaining | branch | folder (if it differs), origin, project (if shown) |
| Git | 76 dp | `✎ 3` | `↑2 ↓1`, or merged |
| Time | 104 dp | "commit 3 h ago" / "created 2 h ago" | main, locked |
| Action | 28 dp | copy launch command on hover | — |

Nothing the chips showed today is lost: main and locked go to the time cell; folder missing, not started and inactive are the badge's state (folder missing also keeps the row's dangerous actions disabled); status unavailable is the Git cell's dash; agents and devices go to the execution cell; changes, divergence and merged go to the Git cell.

The badge uses Material icons (`PriorityHigh` needs you, `PlayArrow` agent working, `FiberNew` new, `Pause` stopped, `Check` merged, `Bedtime` inactive, `FolderOff` folder missing, `Remove` other) in `ShogunTheme.status` colors (warning, info, the theme's primary color, neutral, success, muted neutral, danger, neutral), with the state's name as tooltip and `contentDescription`. Icons instead of letters, because a letter depends on the language and a shape is what tells states apart without color.

The most urgent agent is chosen by blocked > done > working > no state > idle. Agent and device icons are colored by state with the existing `HerdrAgentState.status()` and device mappings.

The compact mode is decided once per list: `WorktreeListPane` measures its width with `BoxWithConstraints` and passes `compact = width < 640.dp` to every row, so all rows switch at once. The project's name is shown when the view spans several projects and isn't grouped by project; `rowContext` is replaced by the name cell's builder, which keeps the project last and gives it the highest priority when truncating (the folder and origin are a weighted `Text` that shrinks first).

### Tray and sidebar

`TrayMenuModel` keeps its marks for `AgentWorking` and `NeedsYou`; nothing else changes. The sidebar counts active worktrees with the new `isActive`, so new and stopped worktrees still count.

### CLI

`shogun status --json` adds `status.createdAt` (ISO-8601 or `null`) next to `lastCommitAt`; existing fields are unchanged. Documented in `docs/guide/cli.md` and its Spanish page.

## Risks / Trade-offs

- [Spanish labels may not fit the fixed widths ("bloqueado", "hace 3 h")] → widths are checked with both languages in the UI tests and the visual check; labels truncate with a tooltip rather than pushing cells.
- [The `commondir` modification time can change if someone edits that file by hand or a tool rewrites it] → acceptable: it only shifts a "created" date; the commit date is used once there are commits.
- [Process-only agents still read as "Agent working" even when they wait for the user] → solved by `agent-state-hooks`; until then, an agent left open still sits in "Agent working", but no longer hides worktrees with an idle Herdr agent.
- [Three days may flag worktrees the user considers alive] → the setting exists, and the badge only changes the group; nothing is removed.
- [Two lines per row show fewer worktrees per screen] → accepted by the user for readability.
