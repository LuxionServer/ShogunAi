# Design

## Context

See proposal.md for the motivation. The pieces involved today:

- `WorktreeScreen.WorktreeContent` measures the content with `BoxWithConstraints` and compares it with `ShogunLayout.compactBreakpoint` (760 dp): above it, a `Row` with the list and a 320 dp column that holds either the selected worktree's `WorktreeDetailPane` or `ProjectSummary`; below it, the detail opens over the list on a scrim.
- `WorktreeListPane` decides `compact` (list narrower than 760 dp) and builds every row through `WorktreeRowFor` → `worktreeRowModel` → `WorktreeRow`, which lays out fixed `DataCell`s with `DataRow`.
- `InspectorSection` draws a section's summary at the end of its label line whether it is expanded or not.
- `RailProjectItem` in `Sidebar` draws `label.take(1).uppercase()`.
- Type styles: the Material `Typography` (Geist) and `ShogunDataTextStyles` (`data`, `dataSmall` in Geist Mono with `tnum`). There is no interface style with tabular figures.

Parallel changes touch the same files (attention model, toolbar tidy, agent focus from the row), so each piece here stays local to the composable or model function it changes.

## Goals / Non-Goals

**Goals:**
- Keep every new decision (what shows, what collapses, how a label is shortened) in plain functions that unit tests cover, so `koverVerify` stays green without UI tests for every case.

**Non-Goals:**
- Restructuring the view's toolbar (toolbar tidy owns it): the summary is only a line of text added under the title.
- The "↑" meaning and the attention groups (attention model).
- Making the row's agent indicator clickable (agent focus from the row).
- Counts in the machine status bar and the machine views (machine entry and alarms): the typography rule is applied to the list, its headers and the inspector.

## Decisions

### Inspector only with a selection, docked from 980 dp

`WorktreeContent` draws only the list when nothing is selected. With a selection it docks the detail when the content (`maxWidth`) is at least `ShogunLayout.inspectorDockMinWidth` = 980 dp, and otherwise opens it over the list as today. `compactBreakpoint` keeps 760 dp for the list's own compact mode, which is measured on the list's width and is unaffected.

The summary that `ProjectSummary` showed becomes `viewSummaryLine(...)`: a pure function that returns "5 worktrees · 1.2 GB · 2 to clean up" (or `null` while loading), drawn as one `bodySmall` line under the view title in `WorktreeScreen`. `ProjectSummary.kt` is deleted. A single line keeps the conflict with the toolbar tidy to one inserted composable.

Alternative considered: keep an empty-state panel at the right on very wide windows. Rejected: the review asked for no inspector without a selection, and the panel repeats what the header can say in one line.

### Execution column collapses per view

`WorktreeRow` takes `showExecution: Boolean`. `WorktreeListPane` computes it once per view as "some row of the view has an execution cell with an agent or a device line", using the same `executionCell(...)` function the row model uses (extracted from `worktreeRowModel`), so the decision and the cell can't disagree. All rows of a view collapse together, which keeps the fixed-position rule of the row.

### Main checkout and marks

`NameCell` gets `isMain`. For the main checkout the folder is left out of the second line, and the first line shows the branch followed by a neutral `StatusChip` "main". `TimeCell.marks` is replaced by `isLocked: Boolean`; the time cell's second line shows a lock icon with the "Locked" tooltip and accessible name. The inspector header shows the same "main" chip after the branch.

### Origin chip only when it isn't ShogunAi

`InspectorHeader` draws the origin chip when the origin is known and isn't `WorktreeOrigin.SHOGUNAI`. The row's branch tooltip keeps naming the origin (it costs nothing on screen).

### Relative path with a middle ellipsis

`displayPath(path, worktreesRoot, home)` returns the path relative to the project's `worktreesRoot` when the path is inside it, `~/…` when it is inside the home folder, and the path otherwise, normalising separators. It is drawn on one line with `TextOverflow.MiddleEllipsis` (already used by `LocalFilePickerDialog`), the full path in its tooltip; copy keeps copying `worktree.path`. The header receives `worktreesRoot` from the project's config, which the detail pane already has.

### Summary only while collapsed; Provisioning only when it matters

`InspectorSection` draws `summary` only when `!expanded`; the summary text style becomes the interface style with tabular figures (see below). `provisioningSectionState` returns `NotApplicable` for `Checking`, a not-yet-read state, and `Checked` with no drift; it returns `Empty(provisioningUnavailable)` for `Unavailable` and `Content` for drift. The "Up to date" chip goes away with its string.

### Agent section and "Runs with"

`AgentSection` receives the agent the worktree opens with (`projectConfig.agentLaunchConfig.displayName`) and the board's default runner (`RunnerId.displayName`), and `runsWithLabel(agent, runner)` builds "Claude Code · Herdr".

- No agent running: the section's body is one line, "No agent running · runs with Claude Code · Herdr" (the strings join `noAgentRunning` and the runs-with label), drawn as the section's `Empty` message.
- Agents running: each agent's indicator and activity lines, as today, then a separate property row "Runs with" → "Claude Code · Herdr" under them.

### Type prefix truncates first

`branchParts(branch)` splits at the last `/` into a prefix (`feature/`) and the rest (`TASK-123`). The row's first line draws the prefix with `Modifier.weight(1f, fill = false)` and ellipsis, then the rest unweighted, so Compose measures the rest first and the prefix shrinks before it. A branch without `/` is one text as today.

### Two-letter rail tiles

`railInitials(labels): Map<String, String>` computes a tile per label among the rail's siblings (the same list the rail iterates): the initials of the first two words when the label has several words (split on spaces, `-`, `_`, `.` and camel case), else its first two letters, uppercased. When siblings collide, each colliding label takes its first letter plus its first later letter that no other colliding label has after its own first letter, and keeps its default tile when it has none; a collision that remains is kept (the tooltip still names the project). `api` and `web` give `AP` and `WE`; `app` and `api` give `AP` and `AI`.

### Interface font with tabular figures

`ShogunTextStyles` gets `figures` (Geist, 12 sp, `tnum`) for counts inside sentences: the view summary line, section summaries, and group header counts in the list. Lone figures (Git counts in the row, property values, sizes on their own) stay in `dataSmall`. The `visual-theme` requirement is updated to state the distinction.

## Risks / Trade-offs

- [Conflicts with the parallel changes in `WorktreeRow`, `WorktreeScreen`, `WorktreeListPane` and the roadmap] → keep edits local, merge `origin/develop` before opening the pull request and re-run `./gradlew test koverVerify`.
- [Hiding the execution column makes the name column jump when the first agent starts in a view] → accepted: it happens once per view, and the review preferred the space over a stable empty column.
- [Hiding Provisioning when up to date removes the confirmation that files were checked] → accepted by the review; a stale or failed check still shows.
- [`TextOverflow.MiddleEllipsis` only works on one line] → the path is single-line by design.
