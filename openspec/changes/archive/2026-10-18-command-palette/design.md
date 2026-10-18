# Design

## Context

After the previous changes of the epic, the shell holds the catalog and navigation (`ShellViewModel`), `WorktreeBoard` holds every project's rows, `MachineResources` knows which machine entries are visible, and shortcuts are menu items. There is no search in the UI. Project search and sort were removed by `app-shell-layout` because this palette covers them.

## Goals / Non-Goals

**Goals:**
- Getting anywhere in two to four keystrokes.
- A matcher that behaves predictably and is tested without Compose.

**Non-Goals:**
- Searching file contents or Git history.
- Destructive or multi-step actions (see the spec).
- A plugin model for palette actions.

## Decisions

### An index rebuilt from existing state
`PaletteIndex` is a pure function `buildPaletteIndex(catalog, rows, machineEntries, strings): List<PaletteItem>` that runs when the palette opens. A `PaletteItem` has an id, a kind, a title, search keys (name, branch, folder, project), context, a shortcut label, a primary action and an optional secondary one. With a few hundred items, rebuilding on open is instant and avoids keeping an index in sync.

### Matcher and ranker
`matchScore(query, key): Int?` does an in-order subsequence match ignoring case. It scores, in order: exact match, prefix, every matched character at a word start (after `/`, `-`, `_`, space or a case change), contiguous run length, and fewer gaps. An item's score is its best key. Ties are broken by recency (an index in `recentPaletteItems`), then alphabetically. *Alternative considered:* a fuzzy library. Rejected because the rule set is small, has to match the spec exactly, and the app avoids new dependencies.

### Overlay UI
A centered overlay 560 dp wide near the top of the window, on `surfaceContainerHigh` with a scrim. It has a search field, then up to 8 visible results in a `LazyColumn`, each with an icon by kind, a title with the matched characters in bold, context in muted text, and the shortcut right-aligned in `dataSmall`. Arrow keys move the highlight and keep it visible. Enter and Cmd/Ctrl+Enter are handled in `onPreviewKeyEvent` on the field. The palette dims, but doesn't block, the auto refresh underneath.

### Actions go through the shell
Primary actions call `ShellViewModel.navigate(...)`, so the leave guard of `project-settings-view` still asks before discarding unsaved settings. Copying a launch command goes through the board's copy, so the confirmation and error handling are the same as everywhere else. Running an item records it in `recentPaletteItems` (most recent first, unique ids, at most 20).

## Risks / Trade-offs

- [Item ids for worktrees change if a worktree moves] → Ids are `projectId + path`. A stale recent item that no longer resolves is dropped when the index is built.
- [Cmd+K may clash with a focused text field's own binding] → No text field in the app binds Cmd+K. The menu item gets priority, as the other shortcuts do.
