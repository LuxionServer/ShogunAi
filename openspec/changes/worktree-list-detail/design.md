# Design

## Context

`WorktreeScreen` renders the header, the notices, the worktree rows (`forEach` inside a `verticalScroll` `Column`) and, at the bottom, `GradleDaemonsSection` and `XcodeDerivedDataSection`. `WorktreeRow` takes 20 parameters and stacks `WorktreeBranchLine`, `WorktreeStatusLine`, `WorktreeOverviewLine`, `EmulatorLine` and `SimulatorLine`. `WorktreeViewModel` (687 lines) keeps per-path maps (`statuses`, `agents`, `sizes`, `ports`, `emulators`, `simulators`) and a single screen-wide `error`. Depends on `ui-design-foundations` (`StatusChip`, `IconAction`, `interactive`, `DangerButton`, data styles), `app-shell-layout` (content area, menu), `create-worktree-dialog` (the header's "New worktree" with Cmd/Ctrl+N through `onNewItemShortcut`, and success messages through `LocalAppMessages`) and `herdr-integration` (`openInHerdr`, `openingInHerdr`, `herdrWorktreesRoot` for the origin).

## Goals / Non-Goals

**Goals:**
- A row that answers "what is this and does it need me?" at a glance, in one line.
- One place for everything about a worktree, which later changes (`understandable-errors`, launch integrations from the roadmap) can extend.

**Non-Goals:**
- Showing worktrees of several projects. The list and detail components are built so `all-worktrees-view` reuses them with rows from many projects.
- Moving the Gradle and DerivedData sections (`machine-resources-view`).
- New worktree actions such as "Open in IDE" (roadmap: "Launching the agent and tools directly"). The detail's action row leaves room for them.

## Decisions

### One row model, built outside the composable
A pure function `worktreeRowModel(worktree, status, agents, emulator, simulator, origin, now)` returns a `WorktreeRowModel`: branch label, folder name, origin, a leading `RowIndicator` and an ordered list of `RowChip`s. It's unit-tested without Compose and replaces the 20-parameter `WorktreeRow`. The leading indicator takes the most urgent state: missing (danger) > agent running (info) > uncommitted changes (warning) > merged (success) > inactive (neutral, muted) > clean (none). Chips follow a fixed order and only appear when they apply: agent, changes `✎3`, `↑2 ↓5` (against the main branch), merged, inactive, locked, device. The last commit time is right-aligned in `dataSmall`. *Alternative considered:* keeping the per-line composables with smaller text. Rejected because the problem is the amount of information per row, not its size.

### Detail pane sections
`WorktreeDetailPane` is a scrollable column with a header (branch in `titleMedium` with the data family, path in `dataSmall` with a copy `IconAction`, origin chip), then sections that only render when they have content: Status (changes, upstream divergence, main divergence or merged, last commit), Agents, Environment (ports), Devices (emulator and simulator lines with Start/Stop, only when the repository declares the provider), Disk (size). The action row is at the top, under the header: "Copy launch command" as a filled button, "Open in Herdr" as a `SecondaryButton` disabled with an `InlineProgress` while the worktree's path is in `openingInHerdr`, then "Delete worktree" as `DangerTextButton` for non-main worktrees. Per-worktree action errors (start emulator, stop simulator, copy failed, Herdr not installed or failing) are stored in `actionErrors[path]` and shown as a `Notice` inside the detail, instead of the view-wide banner.

### Selection lives in the ViewModel, keyed by path
`selectedWorktreePath: String?` in `WorktreeViewModel`. After every list load, a path that's no longer listed is cleared. The list is a `LazyColumn` with `key = { it.path }`. Keyboard navigation uses `onPreviewKeyEvent` on the focused list (up and down move the selection and `animateScrollToItem` keeps it visible), and the shortcuts are menu items bound while a project view is active, so they also show in the menu.

### Layout: two panes with a fixed detail width, overlay below a threshold
The list takes the remaining width. The detail is 360 dp wide, with a vertical divider. Below a content width of 760 dp, the detail is drawn as a sheet over the list from the right edge, with a scrim, and closing it clears the selection. `BoxWithConstraints` on the content area decides which layout to use. *Alternative considered:* a draggable split. Deferred, because a fixed width keeps the first version simple and the detail's content is designed for 360 dp.

### Header and summary
The project view's header shows the project name and, right-aligned, "New worktree" (filled), "Clean up" (outlined, enabled when there are candidates), "Project settings" (`IconAction` with a settings icon) and the reload action. The summary shown when nothing is selected reuses the counts the ViewModel already computes (`worktrees.size`, `totalSize`, `cleanupCandidates`).

### Gradle and DerivedData sections stay for now
They render below the list, inside the list pane's scroll, exactly as today, until `machine-resources-view` moves them to the Machine section. This keeps the epic's intermediate states complete.

## Risks / Trade-offs

- [Information hidden behind a selection (size, ports, origin) is one click further away] → Those values aren't needed to triage, and the summary keeps the total size visible. The command palette and the cross-project views make getting to a worktree fast.
- [Hover-only quick actions aren't discoverable] → The same action is the detail's primary action and in the context menu. Hover is only a shortcut.
- [The 760 dp overlay threshold interacts with the sidebar's rail threshold] → Both use content-area width, not window width, so collapsing the sidebar gives the list more room before the overlay kicks in. The tasks include checking both thresholds together.
