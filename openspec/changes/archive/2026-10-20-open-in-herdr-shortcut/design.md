## Context

Shift+Cmd+C (copy launch command) is wired through `WorktreeBoard.copySelectedLaunchCommand()`, a `WorktreeActions` field handed to the shell by `AppShell`, and a Worktree menu item in `AppMenuBar`. The new shortcut follows the same path.

## Goals / Non-Goals

**Goals:**
- Cmd/Ctrl+E opens the selected worktree in Herdr, with the guards of the other worktree shortcuts.

**Non-Goals:**
- Shortcuts for opening in the terminal, the IDE or the folder.
- Choosing an agent from the shortcut: it uses the default one, like the button.

## Decisions

- `WorktreeBoard.openSelectedInHerdr()` sits next to `copySelectedLaunchCommand()`, returns when `isCreateDialogOpen` or `TextInputFocus.isActive`, and otherwise calls `holder.openInHerdr(worktree)` for the selection.
- `WorktreeActions` gets `onOpenSelectedInHerdr`, passed by `AppShell` as `board::openSelectedInHerdr`.
- `AppMenuBar` gets `onOpenSelectedInHerdr` (wired in `main.kt`) and adds `Item(strings.openInHerdr, shortcut = shortcut(Key.E), enabled = actsOnSelection)` to the Worktree menu; `strings.openInHerdr` already exists.
- Cmd/Ctrl+E isn't used by any other shortcut.
- The command palette has no action to open the selection in Herdr (`OpenKind` has no Herdr entry and `LaunchSelected` only offers the other agents), so no shortcut is shown there and no palette action is added.

## Risks / Trade-offs

- The menu accelerator also fires when the window has focus on a non-worktree view: the item only exists while a worktree view is active, as the other worktree items.
