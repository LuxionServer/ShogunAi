# Proposal

## Why

The roadmap's "Reliability on any machine" asks for the desktop app's coverage outside its composables to reach 100%, one package per change, starting with the worktrees and their inspector (`ui/worktree` and `ui/worktree/inspector`). That package is the app's main screen: the board that lists, groups, filters and sorts every project's worktrees, the per-project holder that loads them and runs their actions, the creation and cleanup dialogs and the inspector. Today 308 of its 2,805 lines and 690 of its 2,359 branches run in no test, so a regression in what the board shows or in what an action does goes unnoticed.

## What Changes

- Unit tests for the board (`WorktreeBoard`), the per-project holder (`ProjectWorktrees`) and the package's models and helpers cover the paths no test reaches today: every view, grouping, sort and filter, multirepo changes, selection and collapse, agents and activity, sizes, actions and their failures, creation and cleanup.
- Compose UI tests (`runComposeUiTest`, like the existing `*UiTest` classes) cover the lambdas the compiler moves out of the package's composables, which Kover counts: the worktree screen, list pane, rows, action bar, header, creation dialog and the inspector's sections.
- Branches no input reaches are rewritten in an equivalent form instead of being excluded, and listed with their reason in design.md. `ProjectWorktrees.kt` is only touched where that is unavoidable.
- The desktop floors in `desktopApp/build.gradle.kts` go up to the whole percentages measured after this change.
- The roadmap item names what is left after this change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None: the tests check the behavior the existing worktree specs already require. If they find a defect, its fix and the requirement it breaks are added to this change's specs before it is fixed.

## Impact

- Tests: new and extended tests under `desktopApp/src/test/kotlin/app/luxion/shogunai/ui/worktree/` and `.../ui/worktree/inspector/`.
- Production code: only equivalent rewrites of unreachable branches in `ui/worktree`, small seams where a path can't be reached from a test otherwise, and fixes for defects the tests find, if any.
- Build: `desktopApp/build.gradle.kts` (floors).
- Docs: `docs/roadmap.md` and `docs/roadmap.es.md`.
- Parallel work: `doctor-gradle-java`, `docs-ci` and `herdr-plugin-health` branch from `develop` at the same time; any of them may touch the desktop floors or the roadmap, so the floors are re-measured and conflicts resolved when bringing in `develop` before the pull request.
