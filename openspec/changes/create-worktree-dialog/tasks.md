# Tasks

## 1. State

- [ ] 1.1 Add `isCreateDialogOpen`, `openCreateDialog()`, `dismissCreateDialog()`, `createError` and `lastCreatedWorktree` to `WorktreeViewModel`, closing on success and keeping fields on failure; verify with `WorktreeViewModelTest` cases: open resets fields, failure keeps the task id and sets `createError` without touching `error`, success closes and sets `lastCreatedWorktree`, dismiss is ignored while creating.

## 2. Dialog

- [ ] 2.1 Build `CreateWorktreeDialog` with the mode selector, both modes' fields, the error `Notice`, `InlineProgress` and a single action row, focusing the first field on open; verify in the running app that Enter creates with a valid id, Escape cancels, and every input is disabled while creating.
- [ ] 2.2 Remove the creation `SectionCard` and the `isCreateSectionExpanded` state from `WorktreeScreen`, and add the "New worktree" header action; verify the worktree list starts right below the header.
- [ ] 2.3 Bind Cmd/Ctrl+N to `openCreateDialog()` and add File > New worktree to the menu while a project view is active; verify in the running app that Cmd+N opens the dialog and that Enter with an invalid id does nothing.

## 3. Transient messages

- [ ] 3.1 Add the shell's `SnackbarHostState` and `LocalAppMessages` with an optional action, 4 s without action and 8 s with one; verify in the running app that a message with an action stays 8 s and that the layout doesn't move when it appears.
- [ ] 3.2 Show the creation success with "Copy launch command" and move the repair and cleanup success messages to the host; verify with a `WorktreeViewModelTest` case that copying from the message copies the new worktree's command.

## 4. Strings and docs

- [ ] 4.1 Add the dialog and message strings in both languages; verify `StringsTest` passes.
- [ ] 4.2 Update "Creating worktrees" in `docs/architecture/ui.md` and `ui.es.md`; verify `mkdocs build --strict` succeeds.
- [ ] 4.3 Run the app on the secondary display and create a worktree in both modes, including a failure (an existing task id); capture the dialog, the error inside it and the success message, and check them against the modified `worktree-workspace` scenarios.

## Workflow follow-up

- Archive the change (`/opsx:archive create-worktree-dialog`) once validated, in the same branch, before opening its pull request against `epic/ui-redesign`.
- With the archive, remove `create-worktree-dialog`'s line from the "Desktop app redesign" list in `docs/roadmap.md` and `docs/roadmap.es.md` (the last change of the epic removes the whole section).
