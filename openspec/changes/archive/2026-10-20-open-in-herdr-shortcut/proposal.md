# Proposal

## Why

Opening the selected worktree in Herdr is one of the most frequent actions, but it requires the mouse (the "Open in Herdr" button). Copying the launch command already has a keyboard shortcut; opening in Herdr should too.

## What Changes

- Add the shortcut Cmd+E (Ctrl+E outside macOS) that opens the selected worktree in Herdr, like the "Open in Herdr" button.
- Add an "Open in Herdr" item with that shortcut to the Worktree menu, disabled without a selection.
- Like the other worktree shortcuts, it is ignored while a dialog is open or a text field has focus.
- The command palette has no "open in Herdr" action for the selection, so it shows no shortcut there.

## Capabilities

### New Capabilities

### Modified Capabilities
- `keyboard-shortcuts`: new requirement for the open-in-Herdr shortcut.

## Impact

- `WorktreeBoard`, `WorktreeActions` (`ShellViewModel.kt`), `AppShell.kt`, `AppMenuBar.kt` and `main.kt`.
- Tests in `WorktreeBoardTest` (`ProjectWorktreesTest.kt`) and `ShellViewModelTest`.
- `docs/architecture/ui.md` and `ui.es.md` (Shortcuts section).
