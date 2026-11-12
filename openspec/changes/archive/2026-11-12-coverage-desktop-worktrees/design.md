# Design

## Context

`ui/worktree` holds the app's main screen: `WorktreeBoard` (the state of the board across projects: views, groupings, sorts, filters, multirepo changes, selection, collapse, agents, sizes and the dialogs it opens), `ProjectWorktrees` (one project's worktrees: loading them, their statuses, agents, sizes and the actions on them), the row and inspector models (`WorktreeRowModel`, `AgentIndicatorModel`, `inspector/InspectorModels`), small helpers (sorting, grouping, filters, relative time, sizes) and the composables that render them (`WorktreeScreen`, `WorktreeListPane`, `WorktreeRow`, `WorktreeActionBar`, `CreateWorktreeDialog`, the inspector's sections).

Kover excludes functions annotated `@Composable`, but the lambdas inside them (`onClick`, a dialog's buttons, a section's content, `LaunchedEffect` blocks) are compiled into separate methods without the annotation, so it counts them. Most of what is uncovered in the composable files is those lambdas. Most of what is uncovered in `WorktreeBoard` and `ProjectWorktrees` is branches: one side of an `if`, a `?:` or a `when` that no test takes.

Measured on macOS on `develop` at 23803b54, with the end-to-end tests:

| File | Lines uncovered | Branches uncovered |
|---|---|---|
| `WorktreeBoard.kt` | 26 of 689 | 240 of 802 |
| `ProjectWorktrees.kt` | 17 of 769 | 148 of 594 |
| `WorktreeScreen.kt` | 88 of 128 | 79 of 101 |
| `WorktreeListPane.kt` | 69 of 266 | 77 of 183 |
| `inspector/ResourcesSection.kt` | 39 of 109 | 48 of 84 |
| `CreateWorktreeDialog.kt` | 20 of 54 | 27 of 44 |
| `inspector/EnvironmentSection.kt` | 21 of 21 | 8 of 8 |
| `inspector/AgentSection.kt` | 14 of 14 | 4 of 4 |
| `WorktreeRow.kt` | 9 of 159 | 8 of 60 |
| `WorktreeRowModel.kt` | 4 of 164 | 7 of 126 |
| `inspector/InspectorModels.kt` | 0 of 67 | 7 of 94 |
| `WorktreeActionBar.kt` | 0 of 15 | 6 of 18 |
| `WorktreeActions.kt` | 0 of 4 | 6 of 8 |
| `ChangeGrouping.kt` | 1 of 27 | 4 of 16 |
| `AgentIndicatorModel.kt` | 0 of 31 | 4 of 40 |
| `WorktreeSorting.kt` | 0 of 27 | 4 of 22 |
| `inspector/ProvisioningSection.kt` | 0 of 6 | 4 of 8 |
| `ActiveChangeRow.kt`, `ViewHeaderRow.kt`, `DialogRepositoryApproval.kt`, `inspector/InspectorPath.kt`, `inspector/StatusSection.kt` | 0 | 9 |
| `ui/worktree` and `ui/worktree/inspector` | 308 of 2,805 | 690 of 2,359 |
| whole desktop app outside composables | 1,097 of 12,749 (91.40%) | 1,632 of 7,921 (79.40%) |

## Goals / Non-Goals

**Goals:**

- 100% of the lines and branches of `ui/worktree` and `ui/worktree/inspector`.
- Raise the desktop floors to the whole percentages measured after this change.

**Non-Goals:**

- Other packages (shell, tray, machine views, multirepo, shared components, the app's wiring); they are the next changes of the roadmap's plan.
- Changing how the board looks or behaves, beyond fixing a defect the tests find.
- Restructuring `ProjectWorktrees`: it is only touched where a branch can't be reached otherwise.
- A per-package Kover rule: Kover 0.9's `verify` rules apply to the whole module, so the package is held by the module floors and its tests.

## Decisions

### Models first, UI tests for the lambdas

The board's, the holder's and the models' branches are covered with plain unit tests next to `WorktreeBoardTest`, `ProjectWorktreesTest` and the existing model tests, using the fakes they already use (`core`'s test fixtures and the constructors' function parameters). They are faster and say more than a UI test.

What only runs from a composable's lambdas is covered with Compose UI tests that render the composable with a real board or holder in the state under test, act on it (click, type, toggle) and assert on the state or on what is shown, like `WorktreeRowUiTest` and `ActiveChangeRowUiTest`. The screen, its list and its dialogs get their own test classes (`WorktreeScreenUiTest`, `WorktreeListPaneUiTest`, `CreateWorktreeDialogUiTest`, `CleanupDialogUiTest`); the inspector's sections are tested through `WorktreeDetailPane` next to the existing detail tests in `ProjectWorktreesTest`.

Alternative considered: driving everything through the end-to-end tests with the whole app. They need a real repository per case and are an order of magnitude slower; they stay for the user journeys they already cover.

### Shared fixtures and UI tests through the whole screen

`WorktreeTestFixtures.kt` builds a project's use cases from a fake git executor (`FakeWorktreeRepository`) and a board over them (`uiBoard`). The UI tests render `WorktreeScreen` with that board, so the list, its rows, the inspector and the dialogs run with the real board and holder instead of hand-made state; the holder's own unit tests live in `ProjectWorktreesStatesTest`, beside the existing `ProjectWorktreesTest`. Kover only counts the classes a test loads: rendering the cleanup dialog made it count that dialog's lambdas, which no test had loaded before, so they are covered here too.

### Branches no input reaches

As in `coverage-desktop-project-config`, a branch the compiler generates but no input reaches is not excluded: the code is rewritten in an equivalent form whose branches all run. The rewrites made:

- `WorktreeBoard` looked a project's holder up by id (`holder(project.id) ?: return …`) while iterating `projects`, although every project in the catalog has its entry. The board now iterates its entries (project and holder together), `projects` is derived from them, and the screen and list read `shownHolders` instead of looking holders up again. The same applies to the cleanup scope (`entries.getValue`, since a scope only holds catalog ids), to the tray, the palette, the machine view and the multirepo aliases.
- Chained safe calls on values that are never `null` became one safe call or an `if`: the shown multirepo, the catalog clean-up in `syncCatalog`, `missingAliases`, the default runner and the menu bar tools, the multirepo agent and trust entry points (now one `openMultirepoAgentIn(runner)`), the fetch preference, the current branch, the Orca openings, the pending removal's runner marks, `agentToFocus`, the cleanup report counts, the simulator deletion count, the Xcode orphans and the unavailable status tooltip of a row.
- `result.exceptionOrNull()!!` when adding a repository to a group became a `fold` with `addedToGroupNotice`; `confirmCreate` checks the project and its holder before `canCreate`, which needs both; the creation dialog is closed unconditionally once a worktree is created, because nothing can change its project while it is creating; `create` resolves its start point with `getOrElse`.
- `isReadingActivity` checked `listFailure == null` for a project not loaded yet, and the focus refresh checked `!hasLoaded` and a `null` `lastReadAt`, but a holder sets `hasLoaded` with every list result, is loading until then, and reads `lastReadAt` as soon as it is created. The checks were dropped and `lastReadAt` starts at the epoch, which reads as long ago.
- `ProjectWorktrees.errorRetry` and `retryError` were never set: `fail` was never given a retry. They were removed, with the retry offered by the list's error notice.
- The list's execution cells and rows read emulators, simulators and headless apps only `takeIf` their repository declared them, but those maps are emptied whenever it doesn't. `groupedBranches` filtered the main worktree out of entries that never contain it. The project header's overflow only holds the clean-up, so its handler no longer checks the id. `isHeaderKey` reads any key as text.
- Code that only ran inside a composable's lambda and kept a branch no rendering reaches moved to ordinary functions with unit tests: the success messages and their action (`successMessage`, `PendingSuccess.action`, now `internal`), the view actions (`WorktreeBoard.performViewAction`, whose `else` the header can't reach), the start point and branch labels of the creation dialog (`startOptionLabel`, `startOptionGroup`, `branchOptionLabel`), the group tag opener of a row (`groupTagOpener`), and the agent, status and provisioning sections' data, now computed before their content. The creation dialog's `when` over its mode moved to a small composable, as `coverage-desktop-project-config` did, so the compiler's default branch isn't counted.
- `AgentState?.phase()` handles `null` before a `when` over the enum, the agent line no longer chains `?.let … ?:`, and a display name capitalizes with `take(1)` instead of `replaceFirstChar`.

### What is left

41 branches and 3 lines of the package stay uncovered, all in composables' lambdas whose missing side needs a rendering state the tests don't build yet: the list's scroll to a selection partly under its header and its notice for every kind of failure, the row's selection and copy callbacks, the action bar's labels with a shortcut or an agent, the default runner preference from a row's actions, the device rows starting and stopping at once, a single project's cleanup headings and the last branches of `ActiveChangeRow` and `ViewHeaderRow`. They are left for the change that covers the shared components, which owns most of those lambdas' callers.

### Floors

Measured on macOS after this change, with the end-to-end tests:

| Scope | Lines | Branches |
|---|---|---|
| `ui/worktree` and `ui/worktree/inspector` | 99.90% (3 uncovered of 2,871) | 98.11% (41 uncovered of 2,175) |
| whole desktop app outside composables | 93.93% (778 uncovered of 12,815) | 87.55% (963 uncovered of 7,737) |

The floors go from 91 to 93 (lines) and from 78 to 87 (branches). Measured again after bringing in `develop` at fdda1d05: 93.98% of lines (779 uncovered of 12,931) and 87.73% of branches (965 uncovered of 7,862), so the floors stay at 93 and 87.

## Risks / Trade-offs

- [UI tests of large composables are slow or flaky] → Each test renders one composable, waits with `waitUntil` for asynchronous loads instead of sleeping, and doesn't depend on animations.
- [A test that only executes a lambda without asserting anything] → Every UI test asserts on the board's or holder's state or on the text shown after the action.
- [Rewriting code for coverage] → Only equivalent rewrites, with the tests of both sides running, and listed here.
- [Parallel changes touch the desktop floors or the roadmap] → Floors and roadmap lines are merged when bringing in `develop` and the floors re-measured before opening the pull request.
