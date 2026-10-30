# Tasks

## 1. Catalog

- [x] 1.1 Move "Clean up" out of `ViewCatalog.overflow` into `ViewCatalog.cleanUp`, with `cleanUpCount` and `offersCleanUp` in `ViewActionContext`, the "Clean up N" label in English and Spanish, `Hidden` when nothing can be cleaned up and `Disabled` with its reason otherwise; drop the divider from `viewOverflow`; verify with updated `ActionSurfacesTest` cases (hidden, count label, disabled while running, overflow without clean up)

## 2. View menu

- [x] 2.1 Add `viewOptionsMenu` building the project, grouping and order sections with checked items and "Reload" with its shortcut after a separator, and its strings in English and Spanish; verify with unit tests for "All worktrees", "Active" and a project's view, and that each item calls its callback
- [x] 2.2 Add the "View ▾" button that opens it; verify with a UI test that opening it and picking an order reports the choice

## 3. Header

- [x] 3.1 Rewrite the header as one `ViewHeader` row (title, progress, spacer, filter, View, icons, Clean up N ghost button, ⋯ only when not empty, primary), removing the segmented controls, the project dropdown and the Reload button; verify with UI tests that "Clean up N" shows and opens the cleanup, is absent with nothing to clean up, and that there is no ⋯ outside a multirepo
- [x] 3.2 Add the compact mode below `ShogunLayout.compactBreakpoint`: search icon that opens the focused field (also on find requests), kept while it has text, and the "+" primary with its label as tooltip; verify with UI tests at a narrow width
- [x] 3.3 Update `MultirepoViewUiTest` and any other test that relied on the old header; verify `./gradlew test koverVerify` passes

## 4. Validation

- [x] 4.1 Check the header in the app running hidden (`shogun headless start`) with sample data at a wide and a narrow width in English; Spanish labels are covered by `ViewOptionsMenuTest`

## Workflow follow-up

- Archive the change, remove the "A tidier toolbar" bullet from `docs/roadmap.md` and `docs/roadmap.es.md`.
- Open the pull request against `develop`, up to date with `origin/develop`.
