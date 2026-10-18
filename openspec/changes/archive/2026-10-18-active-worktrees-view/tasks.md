# Tasks

## 1. Classification

- [x] 1.1 Add `Activity` and `worktreeActivity(...)` in `core` next to `isInactive`; verify with `WorktreeActivityTest` covering every scenario of "What counts as an active worktree", "Needs you" with upstream ahead, with no upstream and ahead of main, and an agent on a merged worktree.
- [x] 1.2 Add the grouping functions for "By state" (active order, then Merged, Inactive, Folder missing, empty groups hidden) and the pinned selection; verify with `WorktreeGroupingTest` and a `WorktreeBoardTest` case where the selected worktree stops being active and stays until deselected.

## 2. Views

- [x] 2.1 Add `ShellDestination.Active` as the first sidebar entry with its count, the default restore fallback, and Cmd/Ctrl+1 and Cmd/Ctrl+2 in the View menu; verify with `ShellViewModelTest` cases for the fallback and in the running app for the shortcuts.
- [x] 2.2 Render "Active" with state groups, project names on rows, the loading line until statuses are read, and the empty state; verify in the running app with demo projects in every state and with everything merged.
- [x] 2.3 Add the grouping selector to both views, persisted per view in `settings.json`; verify with a `JsonSettingsFileTest` case and in the running app after a restart.
- [x] 2.4 Add "Clean up" on the "Merged" group header; verify in the running app that it opens the cross-project confirmation.

## 3. Sidebar activity

- [x] 3.1 Show active counts and the agent indicator (with a tooltip naming the agents) on project entries; verify in the running app by starting an agent in a demo worktree and watching the indicator appear within a few seconds.

## 4. Strings and docs

- [x] 4.1 Add the new strings in both languages; verify `StringsTest` passes.
- [x] 4.2 Update `docs/architecture/ui.md` and `ui.es.md` (Active view, activity rule, grouping) and the main-view description in `README.md` and `README.es.md`; verify `mkdocs build --strict` succeeds.
- [x] 4.3 Run the app on the secondary display with three demo projects, start and stop an agent, commit and merge a branch, and check "Active", the groups, the pinned selection and the sidebar counts against the scenarios.

## Workflow follow-up

- Archive the change (`/opsx:archive active-worktrees-view`) once validated, in the same branch, before opening its pull request against `epic/ui-redesign`.
- With the archive, remove `active-worktrees-view`'s line from the "Desktop app redesign" list in `docs/roadmap.md` and `docs/roadmap.es.md` (the last change of the epic removes the whole section).
