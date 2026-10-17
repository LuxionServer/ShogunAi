# Proposal

## Why

The "New worktree" form is a card that always sits above the worktree list. It pushes the list down and has to be collapsed by hand. When creation fails, the error appears at the top of the screen, away from the field that caused it. After creating a worktree, the next step is almost always copying its launch command, but the success message offers nothing. Creating a worktree is a short, focused task, and in the epic's layout (`docs/roadmap.md`, "Desktop app redesign") it becomes a dialog opened from the project view's header or with Cmd/Ctrl+N. That frees the content area for the list and detail of `worktree-list-detail`.

## What Changes

- A "New worktree" dialog replaces the always-visible creation card. It's opened from a "New worktree" action in the project view's header, or with Cmd/Ctrl+N. **BREAKING** (UI): the collapsible card is removed.
- The dialog keeps both modes (new branch with task id and branch type, existing branch with the grouped dropdown). The first field is focused when the dialog opens. Enter creates, Escape cancels.
- While creating, the dialog shows progress and disables its inputs and actions. On failure, it stays open with the entered values and shows the error inside the dialog.
- On success, the dialog closes and a transient confirmation offers "Copy launch command" for the new worktree.
- Cmd/Ctrl+N opens the dialog instead of creating directly from the form's state.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `worktree-workspace`: creation happens in a dialog. Its in-progress and failure behavior is defined inside the dialog, the success feedback offers copying the launch command, and the collapsible form is removed.
- `keyboard-shortcuts`: Cmd/Ctrl+N opens the new worktree dialog. Enter in the dialog creates when the input is valid.
- `ui-consistency`: the creation form is separated from the list by being a dialog, instead of a card above it.

## Impact

- `ui/worktree/WorktreeScreen.kt`: the creation card and the duplicated create button blocks are removed. A header action and the dialog are added (`ui/worktree/CreateWorktreeDialog.kt`).
- `WorktreeViewModel`: dialog visibility, error scoped to the dialog, values kept on failure, the last created worktree for the copy action.
- A shared transient message host (snackbar) in the shell for success messages with an action. `understandable-errors` reuses it.
- Strings in both languages. `docs/architecture/ui.md` ("Creating worktrees") in both languages.
