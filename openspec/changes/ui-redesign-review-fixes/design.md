# Design

## Context

Findings come from an end-to-end walkthrough of `epic/ui-redesign` (2026-10-18). Motivation and scope: see proposal.md. The walkthrough also produced a demo environment (three projects with worktrees in every state, Gradle and emulator providers, a missing repository) that runs the app isolated from the real `~/.shogunai` with `-Duser.home`; the running-app validation uses it. The app runs on the secondary display.

The code involved: `CommandPalette.kt` (`LaunchedEffect(Unit)` requests focus, `LaunchedEffect(highlighted)` scrolls, keyed `LazyColumn`, row weights), `PaletteMatcher.kt`/`PaletteIndex.kt` (ranking), `WorktreeListPane.kt` (`animateScrollToItem` on `board.selected`), `WorktreeRow.kt`/`WorktreeRowModel.kt` (row context), `CreateWorktreeDialog.kt`, `PreferencesWindow.kt` and the dialogs opened from `AppShell.kt`/`main.kt`.

## Goals / Non-Goals

**Goals:**
- Fix the nine findings with the smallest change that satisfies the specs.
- Validate in the running app the scenarios the three epic changes skipped.

**Non-Goals:**
- The five roadmap items (branch without own commits shown as merged, save-bar Discard asking twice, Project settings title, `link`/`hooks` in Worktree files, machine views polish).
- Redesigning the palette or the list.

## Decisions

**Palette scroll (1).** Scroll to the highlighted index in a `LaunchedEffect(results, highlighted)` and reset the highlight to 0 when the query changes, using `scrollToItem` (not animated) so a stale offset never survives a new result list. Alternative: drop the item keys so the `LazyColumn` stops anchoring to the old first key; rejected because keys keep the highlight stable while the user navigates.

**Palette rows (2).** Title `weight(1f, fill = false)`, context fixed size with its own max, and one `Spacer(weight(1f))` before the shortcut so the title takes the free space and the shortcut goes to the right edge. Alternative: a `Row` with the title `weight(1f)` and the context and shortcut after it; chosen if the spacer variant still thirds the row.

**List scroll (3).** Replace `animateScrollToItem(index)` with a check of `listState.layoutInfo.visibleItemsInfo`: do nothing when the item is fully visible below the sticky header (the header's height is taken from the group header's item info), otherwise `scrollToItem(index, -headerHeight)`. Arrow navigation then moves the list only at the edges.

**Typing right after Cmd+K (4).** Request focus in the composition of the field (`SideEffect`/`onGloballyPositioned` once) instead of after the dialog appears, and verify with a UI test that types immediately. If the dialog window takes focus after composition, the cause is the window's focus and the fix is requesting focus when the dialog window is focused.

**Copy confirmation (5).** The copy action from the palette posts the same transient message the app already uses for copies (the create confirmation's snackbar path), instead of only toggling the row's ✓. Alternative: keep the ✓ and add a toast; rejected, one mechanism is enough.

**Ranking tie (6).** Add the item kind to the sort key after recency: project, then other kinds, then worktree. Unit test in the matcher tests.

**Focus after dialogs (7).** Investigate first: reproduce with Preferences (Cmd+W), "Discard changes?" → Keep editing, and "New worktree" → Escape; check whether the dialog `Window`s are disposed without returning focus to the main `Window` (AWT keeps focus on a hidden owner). Candidate fix: `window.toFront()`/`requestFocus()` on the main window when the last secondary window closes, or declaring the main window as the owner of the dialogs. The root cause found goes into this section before the fix is implemented.

**Row context (8).** Order and weight the context so the project name is laid out first and the origin truncates first in views with several projects; the single-project view is unchanged. Alternative: drop the origin in cross-project rows; rejected, the spec "Show where each worktree comes from" still requires it where it fits.

**Create dialog after a post-create failure (9).** `PostCreateCommandFailed` carries the created worktree's path, so the dialog moves to a "created, with error" state: the error with its technical details stays, "Create worktree" disappears, and "Copy launch command" and "Close" appear; the worktree is added to the list as on success. Alternative: close the dialog and show the error in the app's message area; rejected, it would lose the technical details next to the context.

## Risks / Trade-offs

- [The focus bug (7) may be an AWT/Compose Desktop limitation] → Investigate first; if it can't be fixed cleanly, document the cause and the workaround here and tell the user before archiving.
- [Compose UI tests don't reproduce window focus] → Cover it with the running-app validation on the secondary display.
- [Scrolling rules (3) depend on the sticky header's height] → Measure it from the layout info instead of a constant.
