# Tasks

## 1. Palette

- [ ] 1.1 Scroll to the top and reset the highlight when the query changes, and keep the highlighted result visible; add a UI test (scroll down, edit the query, first result visible) and verify it passes
- [ ] 1.2 Lay out result rows with the title taking the free width and the shortcut right-aligned; add a UI test that a short title isn't truncated and verify it passes
- [ ] 1.3 Deliver keys typed right after Cmd/Ctrl+K to the search field; add a UI test that types immediately after opening and verify it passes
- [ ] 1.4 Show the transient copy confirmation on Cmd/Ctrl+Enter whichever view is open; add a UI test with a worktree outside the current view and verify it passes
- [ ] 1.5 Break ranking ties by kind (project before worktree) after recency; add a matcher test for `web` with `web-app` and `main · web-app` and verify it passes
- [ ] 1.6 Update `docs/architecture/ui.md` and `ui.es.md` if they describe the palette behavior changed here, and verify both languages stay in sync

## 2. Worktree list and rows

- [ ] 2.1 Scroll the selected row into view only when it isn't fully visible, below the sticky group header; add a UI test (arrows between visible rows don't scroll, an off-screen selection ends fully visible) and verify it passes
- [ ] 2.2 Keep the project name when the row context doesn't fit in cross-project views; add a row-model or UI test with a long project name and badges and verify it passes

## 3. Create dialog

- [ ] 3.1 After `PostCreateCommandFailed`, show the created state with the error and its technical details, without "Create worktree", with "Copy launch command" and "Close", and add the worktree to the list; add a UI test and verify it passes
- [ ] 3.2 Add the new strings in English and Spanish and verify `StringsTest` passes

## 4. Window focus

- [ ] 4.1 Reproduce the focus loss (Preferences closed with Cmd+W, "Discard changes?" → Keep editing, "New worktree" → Escape), write the cause into design.md and verify it is stated there
- [ ] 4.2 Return focus to the main window when those windows or dialogs close, and verify in the running app that the first click works and the menu bar keeps its menus

## 5. Roadmap

- [ ] 5.1 Add the five out-of-scope findings to `docs/roadmap.md` and `docs/roadmap.es.md` and verify `mkdocs build --strict` passes

## 6. Validation

- [ ] 6.1 Run `./gradlew test` and verify all modules pass
- [ ] 6.2 In the running app with the demo environment on the secondary display, validate the scenarios of this change (items 1–9) and report the result of each
- [ ] 6.3 In the same run, validate the scenarios the epic skipped: command-palette 2.1, 2.2, 3.3; providers-configuration 3.1, 3.2, 4.3; project-settings-view 4.2, 5.3 (read their archived `tasks.md` for the exact scenarios) and report the result of each

## Workflow follow-up

- Commit the plan, then the implementation.
- Archive the change (`/opsx:archive`) in this branch before opening the pull request.
- Open the pull request against `epic/ui-redesign`.
