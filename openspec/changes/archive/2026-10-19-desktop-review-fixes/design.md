# Design

## Context

See proposal.md for motivation. Relevant current state in `desktopApp`:

- `ProjectWorktrees.runCreation` appends with `worktrees = worktrees + worktree`; `refresh()` replaces the list from `git worktree list` and isn't skipped while a creation runs when called from `refreshShown()` (window focus) or Cmd+R. The list keys rows by `worktree:<id>:<path>`, so a duplicate throws.
- `WorktreeListPane.stateListItems` never emits `ListItem.GroupState`; `projectListItems` returns early for collapsed groups before adding it. `WorktreeBoard.isNothingActive` counts a failed project as loaded.
- Removal keeps `pendingRemoval` set on failures that don't suggest `--force`, and writes the error to `holder.error`; the dialog's `onDismissRequest` isn't guarded by `isRemoving`; `selected` isn't cleared.
- `ProjectConfigViewModel.baseRepositoryPath`'s setter calls `loadRepositoryConfig()`, `refreshEmulatorSection()` and `refreshSimulatorSection()` on every keystroke; `repositoryPreviewConfirmed` is never reset.
- `AppMenuBar` binds "Delete worktree" to the platform modifier + Backspace and builds the item whenever the callback exists.
- `paletteCopyLaunchCommandHint` exists in the strings and is unused.

## Goals / Non-Goals

**Goals:**
- Fix each finding with the smallest change that satisfies its spec, each with a test.

**Non-Goals:**
- The "Polish after the interface redesign" items already in the roadmap.
- Splitting `ProjectWorktrees.kt` and `ProjectSettingsView.kt` (`background-refresh-cost` extracts the device state, which is the natural first split).

## Decisions

**Creation and refresh.** Both: skip `refresh()` while `isCreating` (as the watcher already skips busy holders) and merge by path on success (`worktrees.filterNot { it.path == worktree.path } + worktree`). The merge alone would be enough to avoid the crash; the skip avoids a flicker of half-provisioned state.

**Errors in every grouping.** A new `ListItem.ProjectNotice(holder)` emitted, in state grouping, before the first state header for each shown project with a load failure or an action error, and in project grouping right after the header even when collapsed. `isNothingActive` requires every shown project to be loaded without failure.

**Removal dialog.** The dialog takes `removeError` from the holder for its pending worktree and renders it with the existing `ErrorNotice` component; `onDismissRequest` is a no-op while `isRemoving`. On success, `selected` is cleared when it equals the removed path.

**Branch loading error.** `CreateWorktreeDialog` receives `branchesError` and a retry callback; the holder stops writing branch errors to `holder.error`.

**Base path editing.** The setter only updates the text and marks the field touched. A `snapshotFlow` on the path with `debounce(400)`, plus explicit calls on focus loss and after Browse/drop, triggers the reload. The reload compares the new path's repository root with the current one (`sameDirectory`): same → keep the draft; different with unsaved repository edits → ask with the existing discard confirmation. Browse reuses `chooseFolder`'s `inspectRepository` so a subfolder or non-repository is flagged.

**Form validation.** `ProjectConfigDraft.problems` gains blank worktrees folder, blank agent command and duplicate repository (from the catalog, excluding the project being edited). Each maps to a field message.

**Preview reset.** `loadRepositoryConfig()` sets `repositoryPreviewConfirmed = false`.

**Device stop progress.** A `stopping` set mirrors the existing `starting` set for emulators and simulators.

**Copy path confirmation.** Reuse the snackbar path used by the launch command copy.

**Shortcuts.** Keep Cmd/Ctrl+Backspace but only enable the menu item when a worktree is selected and no text field has focus (the filter, palette and create dialog fields report focus through the `trackTextInputFocus` modifier into a global `TextInputFocus` flag; the board also ignores the shortcut actions while it is set). Alternative: change the shortcut to Cmd+Delete-forward or Shift+Cmd+Backspace; rejected for now, the platform convention for "move to trash" on macOS is Cmd+Backspace and it's only harmful while typing.

**Palette hint.** Show `paletteCopyLaunchCommandHint` in the palette footer when the highlighted item is a worktree.

## Risks / Trade-offs

- [Debounced path reload feels slower] → 400 ms and immediate on blur or Browse.
- [Text field focus tracking misses a field] → Only the fields of the worktree view report focus; a UI test covers the modifier and a board test covers the guard.
