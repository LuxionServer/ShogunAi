# Tasks

## 1. Domain: the two states

- [x] 1.1 Write first, and see failing, `WorktreeActivityTest`/`WorktreeOverviewTest` cases for every scenario of "Each worktree has one state", "What counts as an active worktree" and "Flag inactive worktrees" (blocked and done agents → `WaitingForYou`, also in the main worktree; main with changes → `Idle`; idle agent with changes, ahead of upstream and own commits without upstream → `Unfinished`; old worktree with changes → `Inactive`; old merged branch → `Merged`). Verify `./gradlew :core:test --tests '*WorktreeActivity*' --tests '*WorktreeOverview*'` fails on them
- [x] 1.2 Replace `Activity.NeedsYou` with `WaitingForYou` and `Unfinished` (enum order `WaitingForYou, AgentWorking, Unfinished, New, Stopped, …`), update `isActive`, reorder `worktreeActivity` as in the design, drop the clean requirement from `isInactive` and add `!isMerged`, and share one `hasUnpushedWork`. Verify the tests of 1.1 pass
- [x] 1.3 Update `orcaBoardStatus`/`orcaComment` and `OrcaWorktreeSyncTest` for both states ("waiting for you", "unfinished"). Verify `./gradlew :core:test :cli:test` passes
- [x] 1.4 Rename the tour's `needs-you` worktree to `unfinished` (`feature/unfinished`) and its script line, with `TourUseCasesTest`. Verify `./gradlew :core:test --tests '*Tour*'` passes

## 2. Desktop: groups, badges and headers

- [x] 2.1 Replace `NEEDS_YOU` with `WAITING_FOR_YOU` and `UNFINISHED` in `StateGroupKind` and `StateBadgeKind` (warning with `PriorityHigh`; neutral with `Edit`), their labels, hints and criteria in `Strings`, `EnglishStrings` and `SpanishStrings`; update `WorktreeGroupingTest`, `WorktreeRowModelTest`, `ChangeGroupingTest`, `StringsTest`. Verify `./gradlew :desktopApp:test` passes
- [x] 2.2 Draw the "Waiting for you" header title in the warning color with its icon and keep the others neutral; add a UI test. Verify the test passes
- [x] 2.3 Add collapsing to state groups: `StateGroup.collapsed`, the header's toggle, `collapsedStates` per view with "Stopped" collapsed in Active, rows hidden in `stateListItems`, selection expanding a collapsed group, collapsing clearing the selection, navigation skipping hidden rows. Tests first for each scenario of "Collapse state groups" in `WorktreeBoardTest` and a header UI test. Verify they pass
- [x] 2.4 Mark only `WaitingForYou` with "!" in `TrayMenuModel`, with `TrayMenuModelTest`. Verify the test passes

## 3. Desktop: counts, Git cell, inspector and calm notice

- [x] 3.1 Count only `WaitingForYou` in the sidebar (`waitingCount`/`waitingCountOf`, `ProjectActivity.waitingCount`), hide "Active"'s count at zero; tests in `WorktreeBoardTest` and the sidebar's UI test. Verify they pass
- [x] 3.2 Git cell: second line from the upstream (arrows with a tooltip saying not pushed/not pulled), `notPushed` with the `CloudOff` icon, tooltip and accessible name, nothing from the base; `WorktreeRowModelTest` for every "Git cell" scenario and `WorktreeRowUiTest` for the icon. Verify they pass
- [x] 3.3 Inspector: "not pushed" upstream property and label suffix for a branch with own commits and no upstream, in `InspectorModels` and its test. Verify it passes
- [x] 3.4 Calm notice: `isNothingWaiting` and `workingAgentCount` in `WorktreeBoard`, `NothingNeedsYou` in `WorktreeScreen` above the list or alone, removing `NothingActive` and its strings; tests in `WorktreeBoardTest` and a UI test for both layouts and "See all worktrees". Verify they pass

- [x] 3.5 Keep the top of the list in view when a group appears above the first one while the list is at its top (`KeepTopInView`), with a regression test seen failing first (`KeepTopInViewUiTest`). Verify it passes

## 4. Documentation

- [x] 4.1 Update `docs/guide/desktop-app.md`, `docs/architecture/ui.md`, `docs/architecture/domain.md`, `docs/agents.md`, `docs/process/clean-account-rehearsal.md` and their `.es.md` translations: the two groups, the order of states, inactivity, collapsed "Stopped", the sidebar counts, the Git cell's "↑" and "not pushed", the calm notice, the tray mark, Orca's comment. Verify `grep -rn "Needs you\|NeedsYou" docs core desktopApp cli --include=*.md --include=*.kt` only finds the agent phase "needs you" of the agent indicator, and `mkdocs build --strict` passes

## 5. Integration

- [x] 5.1 Run `./gradlew test koverVerify`; cover any missed line and raise any floor passed (desktop branches 66 → 67). Verify both pass
- [x] 5.2 Check the app with `shogun headless start` and captures, with sample data only: "Active" with the two groups and their headers, "Stopped" collapsed and expanded, the calm notice above the groups and alone, the sidebar counts, the Git cell's arrows and "not pushed" icon, the inspector's Status section, the main checkout with ✎ under "Other" in a project's view

## Workflow follow-up

- Planning commit and push after this proposal validates.
- Implementation commit and push once 5.1 and 5.2 pass; archive with `/opsx:archive`, remove the attention model bullet from `docs/roadmap.md` and `docs/roadmap.es.md`, bring in `origin/develop` (expect conflicts in `WorktreeRow`, the row model and the roadmap), re-run the tests, and open the pull request with `--base develop --assignee CiroFeliu`.
