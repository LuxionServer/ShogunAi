# Tasks

## 1. Per-project state holder

- [x] 1.1 Extract `ProjectWorktrees` from `WorktreeViewModel` (list, statuses, overview, providers, create, remove, cleanup, copy) and move the tests to `ProjectWorktreesTest`, with no behavior change; verify every moved test passes unchanged.
- [x] 1.2 Add `WorktreeBoard` (one `ProjectWorktrees` per catalog project, recreated when its config changes, disposed when removed) and `WorktreeViewState` (selection as `WorktreeRef`, filters, dialogs); verify with `WorktreeBoardTest`: projects added and removed with the catalog, config change recreates, selection cleared when its project is removed.

## 2. Loading and refresh

- [x] 2.1 Load projects with at most 2 lists in parallel, show each project's load state, and isolate failures; verify with a `WorktreeBoardTest` case where one project's repository is missing and the others load.
- [x] 2.2 Add the board-wide sequential size queue that prioritizes shown projects; verify with a test using a fake measurer that shown projects are measured first.
- [x] 2.3 Replace the per-project watcher with one ticker over the shown idle projects, reloading only changed fingerprints and running agent detection once with all paths; verify with `WorktreeBoardTest` cases: only the changed project reloads, a busy project is skipped, agents are attributed across projects, a slow tick skips the next one.

## 3. Views

- [x] 3.1 Replace `ShellDestination.Project` with `All(projectFilter)` and migrate saved `lastDestination` values; verify with `ShellViewModelTest` cases for the old format and for each filter.
- [x] 3.2 Add the "All worktrees" sidebar entry with its count, and make project entries set the filter; verify in the running app that sidebar and header filter stay in sync.
- [x] 3.3 Render project groups with sticky headers (name, count, load state, collapse, New worktree, Clean up, Project settings), merge the header in the filtered view, and persist `collapsedProjects`; verify in the running app and after a restart.
- [x] 3.4 Add the text filter (Cmd/Ctrl+F, Escape clears, no-match state) as a pure function with tests; verify with `WorktreeFilterTest` (branch, folder, case, empty groups hidden).
- [x] 3.5 Show the project's name in the detail header when the view spans several projects; verify in the running app.

## 4. Create and clean up across projects

- [x] 4.1 Add the project selector to `CreateWorktreeDialog` when opened from the unfiltered view, defaulting to `lastCreateProject`; verify with a `WorktreeBoardTest` case and in the running app.
- [x] 4.2 Make "Clean up" work over the view's scope, group the dialog by project, and show provider options per the projects that declare them; verify with `WorktreeBoardTest` cases for mixed projects and in the running app.

## 5. Strings and docs

- [x] 5.1 Add the new strings in both languages; verify `StringsTest` passes.
- [x] 5.2 Update `docs/architecture/ui.md` and `ui.es.md` (board, views, filters, auto refresh across projects); verify `mkdocs build --strict` succeeds.
- [x] 5.3 Run the app on the secondary display with three demo projects (one with a missing repository), and check "All worktrees", the project filter, the text filter, collapsing, creating from the unfiltered view and a cross-project cleanup against the `worktree-views` scenarios.

## Workflow follow-up

- Archive the change (`/opsx:archive all-worktrees-view`) once validated, in the same branch, before opening its pull request against `epic/ui-redesign`.
- With the archive, remove `all-worktrees-view`'s line from the "Desktop app redesign" list in `docs/roadmap.md` and `docs/roadmap.es.md` (the last change of the epic removes the whole section).
