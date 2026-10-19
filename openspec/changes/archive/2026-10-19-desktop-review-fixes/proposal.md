# Proposal

## Why

Using the redesigned desktop app on real projects, and the project review of 2026-10-19, found defects that the redesign's own walkthrough didn't hit: the list can crash while a slow creation finishes, errors disappear in the default Active view, a failed removal leaves a silent dialog, and editing a project's path throws away unsaved repository settings. They are small, independent fixes in `desktopApp`, grouped so they ship together.

## What Changes

- A worktree that finishes creating while the list refreshes appears once; the list never crashes on a duplicate.
- Project load failures and action errors are shown when the view is grouped by state and when a project group is collapsed; a project that failed to load doesn't count as "nothing active".
- A failed removal shows its error inside the removal dialog, and the dialog can't be dismissed while the removal runs; the selection is cleared when the selected worktree is removed.
- The create dialog shows a failure to load branches as an error, not as "No branches available".
- Editing the base repository path in project settings keeps the unsaved repository edits and reloads the repository only when the path settles; the settings form validates the path like "Add project" does and rejects blank worktrees folder and agent command, and a repository already added.
- The `.shogunai.json` preview is shown again after the file was reloaded from disk.
- Stopping an emulator or simulator shows progress and ignores repeated clicks; copying a path shows a confirmation.
- The delete shortcut doesn't fire while a text field has focus, and worktree menu items are disabled without a selection.
- The command palette tells the user that Cmd/Ctrl+Enter copies the launch command.

## Capabilities

### New Capabilities

### Modified Capabilities
- `worktree-workspace`: creation racing a refresh, removal errors in the dialog, branch loading errors, selection after removal.
- `worktree-views`: errors in the state grouping and in collapsed groups; failed projects in "Nothing active".
- `project-configuration`: path edits keep the draft, path and field validation, preview after a reload.
- `worktree-detail`: progress when stopping devices, copy path confirmation.
- `keyboard-shortcuts`: delete shortcut and text fields, menu items without a selection.
- `command-palette`: the secondary action is shown.

## Impact

- `desktopApp`: `ProjectWorktrees.kt`, `WorktreeListPane.kt`, `WorktreeBoard.kt`, `WorktreeScreen.kt`, `CreateWorktreeDialog.kt`, `WorktreeDetailPane.kt`, `ProjectConfigViewModel.kt`, `ProjectSettingsView.kt`, `AppMenuBar.kt`, `CommandPalette.kt`, i18n strings.
- Tests: Compose UI tests and view-model tests in `desktopApp/src/test`.
- No `core` or CLI changes.
