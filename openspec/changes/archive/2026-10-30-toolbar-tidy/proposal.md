# Proposal

## Why

The worktree view's header spreads over two rows: the title row holds Reload, a "⋯" whose only entry is often a disabled "Clean up", and New worktree; a second row holds the project dropdown, the text filter and two segmented controls, and on narrow windows it wraps again. The UX review of the v2 interface asked for a calmer header that fits in one row and only shows what can be used.

## What Changes

- The header becomes a single row: the title (with room after it for a summary), the text filter, a "View ▾" menu, "Clean up N" and the primary action.
- "View ▾" holds the project filter (in "Active" and "All worktrees"), the grouping (outside a project's view), the order and Reload. Reload keeps its Cmd/Ctrl+R shortcut from the View menu of the menu bar. The segmented controls, the header's project dropdown and the Reload button go away.
- "Clean up" leaves the "⋯" menu and becomes a neutral ghost button, "Clean up N" with the number of worktrees selected for cleanup, shown only when there is something to clean up; while a cleanup runs it stays disabled with its reason.
- The "⋯" menu is shown only when it has entries, which today means only in a multirepo view (opening the multirepo agent).
- When the header is narrower than the compact breakpoint, the filter becomes a search icon that opens the field (also with Cmd/Ctrl+F) and New worktree / New change becomes a "+" icon button with its label as tooltip.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `worktree-views`: the project filter, the grouping and the order move into the header's "View ▾" menu; the text filter collapses to an icon on narrow windows; a new requirement describes the one-row header.
- `action-catalog`: the view header's actions: "Clean up N" as a ghost button outside the "⋯" menu, and the "⋯" menu only when it has entries.
- `worktree-cleanup`: where the view's "Clean up" action is offered and when it is shown.

## Impact

- Desktop app: `WorktreeScreen` (header), `ViewActionCatalog` (clean up out of the overflow, its count and visibility), a new builder for the "View ▾" menu, English and Spanish strings.
- Tests: `ActionSurfacesTest`, header UI tests, `MultirepoViewUiTest`.
- No change to the CLI, `core` or the cleanup itself.
