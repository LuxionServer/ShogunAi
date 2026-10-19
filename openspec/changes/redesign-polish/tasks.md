# Tasks

## 1. Not started branches

- [ ] 1.1 Add `neverCommitted` and `isNotStarted` to `WorktreeStatus`, read the branch reflog in `ReadWorktreeStatusUseCase` only when the branch would read as merged, and make `isMerged` false for it; add use case tests (main moved after creation, fast-forward merged branch, branch with upstream, reflog with several entries, reflog unreadable) and verify they pass
- [ ] 1.2 Classify a not-started worktree as `InProgress` without changes and `NeedsYou` with them in `worktreeActivity`, and check it isn't a cleanup candidate; add `WorktreeOverview` and `CleanUpWorktreesUseCase` tests and verify they pass
- [ ] 1.3 Show "not started" instead of the merged chip and detail text in the desktop app, and in `shogun status`; add row model, detail and CLI tests and verify they pass
- [ ] 1.4 Describe the not-started state in `docs/guide/desktop-app.md`, `docs/guide/cli.md` and their Spanish versions, and verify `mkdocs build --strict` passes

## 2. Project settings

- [ ] 2.1 Make the save bar's "Discard" reset the form and leave without the leave guard, keeping the guard for the sidebar, shortcuts, menus and window close; add view-model or UI tests for both paths and verify they pass
- [ ] 2.2 Name the saved project in the settings title, keeping "Add project" when adding; add a UI test with an edited, unsaved name and verify it passes
- [ ] 2.3 List `link` patterns marked as linked and the post-creation commands line in `RepositoryProvisioningList`, and show the empty text only when nothing is declared; add UI tests for linked patterns, only commands and an empty repository, and verify they pass

## 3. Machine views

- [ ] 3.1 Add a shared column header row in `MachineViewParts` and use it in the emulators and simulators views; add a UI test and verify the labels are shown above the rows
- [ ] 3.2 Show the AVD a stopped worktree emulator will use ("Automatic" when none) and "Not created" with the configured device type for a missing simulator; add UI tests and verify no row shows "—"
- [ ] 3.3 Update the machine views description in `docs/guide/desktop-app.md` and `desktop-app.es.md` if it mentions the columns, and verify `mkdocs build --strict` passes

## 4. Strings and validation

- [ ] 4.1 Add the new strings in English and Spanish and verify `StringsTest` passes
- [ ] 4.2 Run `./gradlew test` and verify every module passes
- [ ] 4.3 In the running app (secondary display), create a worktree, commit on the main branch and check it reads as not started and isn't offered by cleanup; check Discard, the title, a repository with `link` patterns and the emulators view with a stopped emulator

## Workflow follow-up

- Archive the change before opening its pull request.
