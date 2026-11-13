# Tasks

## 1. Board, holder and models

- [x] 1.1 Cover the uncovered lines and branches of `WorktreeBoard` (views, groupings, sorts, filters, multirepo changes, selection and collapse, agents and activity, dialogs) with unit tests; verify with `./gradlew :desktopApp:koverXmlReport` that the file has no missed lines or branches
- [x] 1.2 Cover the uncovered lines and branches of `ProjectWorktrees` (loading, statuses, agents, sizes, actions and their failures) with unit tests; verify the same way
- [x] 1.3 Cover `WorktreeRowModel`, `AgentIndicatorModel`, `ChangeGrouping`, `WorktreeSorting`, `WorktreeActions`, `inspector/InspectorModels` and `inspector/InspectorPath` with unit tests; verify the same way
- [x] 1.4 Rewrite any branch no input reaches in an equivalent form and record it in design.md; verify the existing tests still pass

## 2. Composables' lambdas

- [x] 2.1 Add `WorktreeScreenUiTest` and `WorktreeListPaneUiTest` covering the screen's dialogs and effects and the list pane's views, groups and menus; verify the screen has no missed lines or branches and the list pane misses only what design.md lists as left
- [x] 2.2 Add `CreateWorktreeDialogUiTest` and `CleanupDialogUiTest`, extend the row UI tests and test the repository approval; verify the creation dialog has no missed lines or branches and the rest misses only what design.md lists as left
- [x] 2.3 Add UI tests for the inspector's sections (agent, environment, resources, provisioning, status); verify they miss only the device rows' branches design.md lists as left

## 3. Floors and docs

- [x] 3.1 Measure `ui/worktree` and `ui/worktree/inspector` (99.9% of lines, 98.1% of branches), raise the desktop floors in `desktopApp/build.gradle.kts` to the measured whole percentages and record the figures and what is left in design.md
- [x] 3.2 Update the roadmap item in `docs/roadmap.md` and `docs/roadmap.es.md` with the desktop figures after this change and the packages left
- [x] 3.3 Run `./gradlew test koverVerify` and verify it passes

## Workflow follow-up

- Archive the change, bring in `origin/develop`, re-measure the floors and open the pull request against `develop`, assigned to CiroFeliu.
