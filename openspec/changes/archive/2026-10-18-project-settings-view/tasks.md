# Tasks

## 1. Form state

- [x] 1.1 Add `touchedFields`, `saveAttempted` and the "first invalid field" signal to `ProjectConfigViewModel`, with "Save" always enabled; verify with `ProjectConfigViewModelTest` cases: untouched new form has no visible errors, editing then emptying the name shows its error, saving with errors doesn't save and reports the first invalid field.
- [x] 1.2 Add the add-from-folder prefill (repository check, top-level resolution, name, sibling worktrees folder, detected main branch as placeholder) and the not-a-repository rejection; verify with `ProjectConfigViewModelTest` cases using fake use cases.

## 2. Leave guard

- [x] 2.1 Add `LeaveGuard` and route `ShellViewModel.navigate`, every view-changing shortcut and menu entry, and the window's close request through it; verify with `ShellViewModelTest` cases: navigation blocked until confirmed, allowed without changes, window close asks with changes.

## 3. Settings view

- [x] 3.1 Rebuild the form as `ProjectSettingsView`: centered 720 dp column, sections with headings instead of cards, labels above fields and help below, provider sections only when declared; verify in the running app with a repository that declares no provider and one that declares both.
- [x] 3.2 Add the section index on content widths of at least 1000 dp with scroll-to-section and the current section highlighted; verify in the running app at 900 dp and 1400 dp.
- [x] 3.3 Add the save bar (unsaved changes text, Discard, Save, add-mode variant) and File > Save with Cmd/Ctrl+S; verify in the running app that it appears on an edit, disappears when the edit is reverted, and Cmd+S saves.
- [x] 3.4 Stop opening the file picker on Enter in path fields; verify in the running app that Enter in a path field does nothing and "Browse" opens the picker.

## 4. Adding a project

- [x] 4.1 Add the first step of `AddProject` (choose folder, drop hint, rejection message) and open the prefilled form; verify in the running app with a repository, a subfolder of a repository and a plain folder.
- [ ] 4.2 Add folder dropping on the main window with the hover highlight for a single directory; verify on macOS and on Linux or Windows that dropping a repository opens the prefilled form and dropping a file does nothing.

## 5. Strings and docs

- [x] 5.1 Add the new strings in both languages; verify `StringsTest` passes.
- [x] 5.2 Update the project configuration section of `docs/architecture/ui.md` and `ui.es.md`, and the "add a project" steps of `docs/getting-started.md` and `getting-started.es.md`; verify `mkdocs build --strict` succeeds.
- [ ] 5.3 Run the app on the secondary display and go through adding a project by picking and by dropping, editing settings, leaving with unsaved changes through the sidebar and closing the window; check them against the modified `project-configuration` scenarios.

## Workflow follow-up

- Archive the change (`/opsx:archive project-settings-view`) once validated, in the same branch, before opening its pull request against `epic/ui-redesign`.
- With the archive, remove `project-settings-view`'s line from the "Desktop app redesign" list in `docs/roadmap.md` and `docs/roadmap.es.md` (the last change of the epic removes the whole section).
