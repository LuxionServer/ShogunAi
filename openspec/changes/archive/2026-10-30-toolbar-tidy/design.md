# Design

## Context

`WorktreeScreen` draws the header as a `Row` (title, loading indicator, Reload, settings icon, multirepo agent indicator, "⋯", primary) followed by a `FlowRow` (project dropdown, text filter, grouping and order segmented controls, "reading status" progress). The view's actions come from `viewCatalog`, whose `overflow` puts "Clean up" last in the "⋯" menu. Reload already has Cmd/Ctrl+R in the menu bar (`AppMenuBar`), routed through `ShellViewModel.reloadRequests`. `ShogunMenu` already supports section titles, separators, checked items and shortcuts, and `ShogunLayout.compactBreakpoint` (760 dp) is the width the list already uses to overlay the detail.

## Goals / Non-Goals

**Goals:**
- One header row at every allowed width, keeping room after the title for the view summary that `calmer-list-and-inspector` moves there.
- Reuse `ShogunMenu` and the existing catalog instead of new widgets.

**Non-Goals:**
- Changing the project group headers' and change group headers' "Clean up" entries.
- Changing the command palette's or the menu bar's actions.

## Decisions

- **The View menu is a plain list of `MenuEntry`, built by a pure function.** `viewOptionsMenu(...)` takes the projects, the current filter, grouping options, grouping, order and callbacks, and returns sections with checked items plus a separator and "Reload" with its shortcut. A pure builder can be unit-tested for every view kind without a UI test; the button that opens it is a small composable (`ViewMenuButton`) next to `MoreMenuButton`. Alternative considered: nested submenus per option; rejected because three short sections fit in one menu and stay one click away.
- **The project filter is a section, not a submenu.** The number of saved projects is usually small; if it grows, the menu scrolls like any `ShogunMenu`. A submenu would add a hover step for the most used option.
- **"Clean up" leaves the overflow and becomes `ViewCatalog.cleanUp`.** `ViewActionContext` gains `cleanUpCount` and `offersCleanUp`; the spec is `Hidden` when nothing can be cleaned up and `Disabled(reason)` while a cleanup runs or the multirepo needs review. `offersCleanUp` mirrors `canCleanUp` of the shown projects, which also counts orphaned simulators and DerivedData: when only those exist the count is 0 and the label is plain "Clean up". `overflow` keeps only the multirepo agent entries, so `viewOverflow` loses its divider, and the header draws `MoreMenuButton` only when the list isn't empty.
- **Compact mode is decided by the header's own width.** The header is wrapped in `BoxWithConstraints`; below `ShogunLayout.compactBreakpoint` the filter is an `IconAction` that sets a local `filterOpen` flag, and the primary action is an `IconAction` with the add icon inside `DisabledReason`. The field reuses `TextFilterField`; it closes on focus loss only when the text is empty, and a find request (Cmd/Ctrl+F) opens it before requesting focus.
- **Layout.** Title (not weighted, single line with ellipsis), the loading/reading progress, then a weighted spacer, then filter, "View ▾", settings/agent icons, "Clean up N", "⋯" and the primary. The title keeps its natural width so a summary can be appended after it; the spacer absorbs the rest. The "Reading worktree status" label is dropped from the header; the progress keeps it as its tooltip so the row doesn't grow.

## Risks / Trade-offs

- [Grouping and order are one click further away] → They're changed rarely; the menu shows the current choice checked, and the palette and shortcuts are unchanged.
- [Parallel sessions edit `WorktreeScreen`'s header] → Keep the header in its own composable (`ViewHeader`) so merges touch a contained block.
- [Very long titles in compact mode] → The title ellipsizes; every other item has a fixed size.
