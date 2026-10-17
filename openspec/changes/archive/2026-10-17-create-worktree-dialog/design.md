# Design

## Context

`WorktreeScreen` holds a collapsible `SectionCard` with the creation form, whose create button block is duplicated for both modes (`WorktreeScreen.kt` lines 237–250 and 273–286). The form state (`taskId`, `branchType`, `createMode`, `selectedBranch`) already lives in `WorktreeViewModel`. Errors go to the screen-wide `error` and successes to `success`, shown as rows at the top. Cmd/Ctrl+N is bound with `onNewItemShortcut` on the scrolling column and creates directly. Depends on `ui-design-foundations` (`Notice`, `InlineProgress`) and `app-shell-layout` (content area, menu).

## Goals / Non-Goals

**Goals:**
- Creation as a focused, keyboard-first dialog whose errors stay next to the input.
- A one-step path from "created" to "copied the launch command".

**Non-Goals:**
- Choosing the base branch or fetching first (roadmap: "Choosing the base of a new worktree").
- Choosing the project inside the dialog. That arrives with `all-worktrees-view`, which opens the dialog from a view that isn't tied to one project.

## Decisions

### Dialog-scoped state in the ViewModel
`WorktreeViewModel` gains `isCreateDialogOpen`, `createError` (separate from the view-wide `error`) and `lastCreatedWorktree`. `openCreateDialog()` resets the form fields. A successful create closes the dialog and sets `lastCreatedWorktree`. A failed one keeps the fields and sets `createError`. The fields are reset on open, not on close, so a failure never loses what the user typed. *Alternative considered:* a separate `CreateWorktreeViewModel`. Rejected because the form state and the creation calls already live here, and the list must be updated by the same holder.

### One dialog component with one create action
`CreateWorktreeDialog` is an in-window Material `BasicAlertDialog` on the app's container color, with the mode selector, the mode's fields, the error `Notice` and one action row. That removes the duplicated button block. The task id field takes focus on open through a `FocusRequester`. Enter is handled by the existing `onEnterKey` modifier, gated by validity. Escape and the scrim dismiss it, except while creating.

### Transient messages through a shell snackbar host
The shell gets one `SnackbarHostState` exposed through a `CompositionLocal` (`LocalAppMessages`), with an action slot. The creation success shows "Worktree created: <folder>" with "Copy launch command". The other success messages of the worktree view (repair after move, cleanup) move to the same host, so success messages no longer push the layout. Errors stay as `Notice` until `understandable-errors`. *Alternative considered:* keeping the inline success row. Rejected because it shifts the list every time an operation succeeds.

### Cmd/Ctrl+N opens, it doesn't create
`onNewItemShortcut` now calls `openCreateDialog()`. Creating with Cmd+N from a hidden form state made sense with an always-visible form, but would be invisible with a dialog.

## Risks / Trade-offs

- [Users used to typing a task id straight into the visible card now need one more keystroke] → Cmd/Ctrl+N opens the dialog with the field focused, so the keyboard path is Cmd+N, type, Enter.
- [The snackbar auto-dismisses before the user reaches "Copy launch command"] → 8 seconds for messages with an action (4 without). The copy action stays available on the worktree itself.
