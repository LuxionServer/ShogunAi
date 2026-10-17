# Tasks

## 1. Row model

- [ ] 1.1 Add `WorktreeRowModel`, `RowIndicator`, `RowChip` and `worktreeRowModel(...)` with the indicator priority and chip order from `design.md`; verify with `WorktreeRowModelTest`: missing beats agent, agent beats changes, merged vs inactive, chips omitted when they don't apply, device chip only while booting or running, main worktree first.
- [ ] 1.2 Replace `statusSegments` and `portsLabel` callers with the row model and the detail sections, keeping their unit tests for the values they still format; verify `StatusSegmentsTest` and the new tests pass.

## 2. Selection and actions in the ViewModel

- [ ] 2.1 Add `selectedWorktreePath`, `select(path)`, `clearSelection()`, `selectNext()`/`selectPrevious()` and clearing of a path that's no longer listed after each load; verify with `WorktreeViewModelTest` cases for keeping the selection across a reload, clearing it when the worktree disappears, and moving past the ends.
- [ ] 2.2 Add `actionErrors` per worktree path for start/stop emulator, start/stop simulator, copy and open-in-Herdr failures, dismissible per worktree; verify with a `WorktreeViewModelTest` case that a failed emulator start sets the worktree's error and leaves the view-wide `error` empty.

## 3. List pane

- [ ] 3.1 Build `WorktreeRow` from the row model (indicator, monospaced branch, folder and origin, chips, relative time) with `Modifier.interactive`, selection background, hover copy `IconAction` and `ContextMenuArea` (copy launch command, open in Herdr, copy path, delete for non-main worktrees); verify in the running app with worktrees in every state.
- [ ] 3.2 Replace the `Column` + `forEach` with a `LazyColumn` keyed by path, with keyboard navigation and `animateScrollToItem`; verify in the running app that arrows move the selection and keep it visible, and that auto refresh doesn't reset the scroll.
- [ ] 3.3 Rebuild the project header (name, "New worktree", "Clean up", "Project settings", reload) and wire "Project settings" to the shell's `ProjectSettings` destination; verify in the running app that it opens the project's configuration.

## 4. Detail pane

- [ ] 4.1 Build `WorktreeDetailPane` with its header, action row (copy launch command, open in Herdr with its loading state, delete) and sections, omitting sections without content and showing devices only for repositories that declare the provider; verify in the running app with a repository that declares no provider and with one that declares the emulator provider.
- [ ] 4.2 Build `ProjectSummary` for the empty selection (count, total size, cleanup candidates with "Clean up"); verify in the running app.
- [ ] 4.3 Add the overlay layout below 760 dp of content width, closing with Escape; verify in the running app at 700 dp and 1200 dp of content width, with the sidebar expanded and as a rail.
- [ ] 4.4 Add Cmd/Ctrl+Shift+C, Cmd/Ctrl+Backspace and Escape as menu-bound shortcuts while a project view is active; verify in the running app, including that Cmd+Backspace does nothing on the main worktree and inside a text field.

## 5. Cleanup and docs

- [ ] 5.1 Remove `WorktreeStatusLine`, `WorktreeOverviewLine`, `WorktreeBranchLine`, `EmulatorLine` and `SimulatorLine` once no longer used, keeping their pure formatting functions where the detail uses them; verify the build and tests pass.
- [ ] 5.2 Add the new strings in both languages; verify `StringsTest` passes.
- [ ] 5.3 Update the worktree sections of `docs/architecture/ui.md` and `ui.es.md` (row model, detail, selection, layouts); verify `mkdocs build --strict` succeeds.
- [ ] 5.4 Run the app on the secondary display with a demo repository holding worktrees in every state and the emulator provider declared; capture the list, the detail of each state, the summary and the overlay, in light and dark, and check them against the `worktree-detail` scenarios and the modified `worktree-overview` ones.

## Workflow follow-up

- Archive the change (`/opsx:archive worktree-list-detail`) once validated, in the same branch, before opening its pull request against `epic/ui-redesign`.
- With the archive, remove `worktree-list-detail`'s line from the "Desktop app redesign" list in `docs/roadmap.md` and `docs/roadmap.es.md` (the last change of the epic removes the whole section).
