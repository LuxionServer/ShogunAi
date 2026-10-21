# Tasks

## 1. Activity date and creation time (core)

- [x] 1.1 Add `createdAt` to `WorktreeStatus` and read it in `ReadWorktreeStatusUseCase` from the `commondir` file of the worktree's administrative folder (resolved through the `.git` file) via `FileManager`, null for the main worktree or on failure; verified by `ReadWorktreeStatusUseCaseTest` cases for a linked worktree, the main worktree and an unreadable `.git` file.
- [x] 1.2 Add `ActivityDate(at, kind)` and `activityDate(worktree, status)` following the spec's rule (commits of its own, no main branch, main worktree, creation date, fallback to the last commit); verified by unit tests for each scenario of "Activity date of a worktree".

## 2. States and inactivity (core)

- [x] 2.1 Replace `INACTIVITY_THRESHOLD` with an `inactiveAfter: Duration` parameter in `isInactive`, measured from `activityDate`, requiring commits of its own; verified by the "Flag inactive worktrees" scenarios as unit tests, including the old base commit in a new worktree.
- [x] 2.2 Redefine `Activity` (`NeedsYou, AgentWorking, New, Stopped, Merged, Inactive, Missing, Idle, Unknown`), `isActive` and `worktreeActivity` with the priority in design.md; verified by unit tests for every scenario of "Each worktree has one state" and "What counts as an active worktree".
- [x] 2.3 Add `inactiveAfterDays` (default 3, 1–90) and `sorting` (`WorktreeSort` per view: active, all, project; default `NAME`) to `Settings`, keeping old settings files readable; verified by `JsonSettingsFile` tests reading a file without the new fields.

## 3. Groups, sorting and state consumers (desktopApp)

- [x] 3.1 Update `StateGroupKind` to the new groups and order, and `WorktreeBoard` to pass the configured threshold, recomputing groups when it changes; verified by `WorktreeGroupingTest` and `WorktreeBoardTest` for "Group by state" and "Longer threshold".
- [x] 3.2 Add the natural-order comparator and the activity order, with the main worktree first, replacing `orderMainFirst`; verified by unit tests for "Natural order" and "By last activity".
- [x] 3.3 Add the sort selector to the view header next to the grouping selector in every view, remembered per view; verified by `WorktreeHeaderUiTest` for "Each view keeps its own order" and a restart through the settings store.
- [x] 3.4 Update the group hints and tooltips (new "New" and "Stopped", "Inactive" naming the configured days) in English and Spanish; verified by the "State groups explain themselves" scenarios in a UI test.
- [x] 3.5 Update `TrayMenuModel` and the sidebar's activity to the new `Activity`; verified by their existing tests passing with new cases for `New` and `Stopped`.

## 4. Row (desktopApp)

- [x] 4.1 Replace chips in `WorktreeRowModel` with per-cell models (badge, execution, name, Git, time), including the most urgent agent with "+N", folder shown only when it differs from the branch's last segment, and project shown only when the view spans projects and isn't grouped by project; verified by `WorktreeRowModelTest` for the scenarios of "Execution cell", "Name cell", "Git cell" and "Time cell".
- [x] 4.2 Rewrite `WorktreeRow` as the fixed-width two-line grid with the state badge (icon, color, tooltip, `contentDescription`) and the hover action in its slot; verified by a UI test that the branch, Git and time cells of two rows with different contents start at the same x position, and that the badge exposes the state's name.
- [x] 4.3 Add the compact execution cell, chosen once per list from its width, with icons colored by state and labels in tooltips; verified by a UI test that narrows the list and checks every row is compact and the agent tooltip reads "Claude: blocked".
- [x] 4.4 Show the creation date with the last commit date in the worktree's detail; verified by a `WorktreeDetailPane` UI test.
- [x] 4.5 Add the inactivity threshold field to Settings, rejecting values outside 1–90; verified by a UI test for "Out of range" and the setting being saved.
- [x] 4.6 Remove strings and models left unused by the chips and "In progress", and add the new strings in English and Spanish; verified by the build compiling with no unused-string test failures and `./gradlew test` passing.

## 5. CLI and docs

- [x] 5.1 Add `status.createdAt` to `shogun status --json`; verified by a `StatusJson` test and the field documented in `docs/guide/cli.md` and `docs/guide/cli.es.md`.
- [x] 5.2 Update `docs/guide/desktop-app.md` and `docs/guide/desktop-app.es.md` (states, row layout, sorting, inactivity setting), and `docs/architecture/ui.md`/`ui.es.md` and `domain.md`/`domain.es.md` where they describe the row or the activity rules; verified by `mkdocs build --strict` passing.

## 6. Verification

- [x] 6.1 Run `./gradlew test` and check every scenario in the change's specs has a test.
- [x] 6.2 Run the app (`./gradlew :desktopApp:run`) on the secondary display and check, in English and Spanish, wide and narrow: rows align, the badge icons and colors, the compact execution cell, the dates, the sort selector and the inactivity setting.

## Workflow follow-up

- Archive the change with `/opsx:archive` once validated, before opening the pull request, removing roadmap item 2 from `docs/roadmap.md` and `docs/roadmap.es.md` and renumbering.
- Open the pull request against `develop`.
