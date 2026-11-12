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

What only runs from a composable's lambdas is covered with Compose UI tests that render the composable with a real board or holder in the state under test, act on it (click, type, toggle) and assert on the state or on what is shown, like `WorktreeRowUiTest` and `ActiveChangeRowUiTest`. Each composable file gets its own test class (`WorktreeScreenUiTest`, `WorktreeListPaneUiTest`, `CreateWorktreeDialogUiTest`, `inspector/*SectionUiTest`…), so a failure points to it.

Alternative considered: driving everything through the end-to-end tests with the whole app. They need a real repository per case and are an order of magnitude slower; they stay for the user journeys they already cover.

### Branches no input reaches

As in `coverage-desktop-project-config`, a branch the compiler generates but no input reaches is not excluded: the code is rewritten in an equivalent form whose branches all run (for example `x?.let { … } ?: y` whose lambda never returns `null` becomes an `if`, a chained safe call on a value that is never `null` loses the `?`, and a `when` over a sealed type inside a composable's lambda moves to an ordinary function). Each rewrite is listed here with its reason as it is made, and the tests of both sides keep running.

### Floors

The floors in `desktopApp/build.gradle.kts` go up to the whole percentages measured after this change. If the package reaches 100% with nothing else changing, the whole desktop app goes to about 93.8% of lines (789 uncovered of 12,749) and 88.1% of branches (942 uncovered of 7,921), so the floors would go from 91 to 93 and from 78 to 88. The measured figures are recorded here once the change is done, and again after bringing in `develop`.

## Risks / Trade-offs

- [UI tests of large composables are slow or flaky] → Each test renders one composable, waits with `waitUntil` for asynchronous loads instead of sleeping, and doesn't depend on animations.
- [A test that only executes a lambda without asserting anything] → Every UI test asserts on the board's or holder's state or on the text shown after the action.
- [Rewriting code for coverage] → Only equivalent rewrites, with the tests of both sides running, and listed here.
- [Parallel changes touch the desktop floors or the roadmap] → Floors and roadmap lines are merged when bringing in `develop` and the floors re-measured before opening the pull request.
