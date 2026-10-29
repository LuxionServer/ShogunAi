# Proposal

## Why

The final review of the app against the v2 design's rules found seven places that break them (runner buttons in a group header, a destructive action in a bar, two entries for the same action, a loose "Clean up", a mixed second line, mono for modes and mixed case in chips, Preferences shown as a view) and three truncations at normal widths. It is the last change of the `ui-redesign-v2` epic, so the epic reaches `develop` following its own rules.

## What Changes

- A change group's header shows only "Open all in <default runner>" as a button; the other runner, "Add repository" and, after a separator in danger text, "Clean up group" (disabled with its reason) move to the group's ⋯ menu, all built from the group catalog.
- The multirepo view's ⋯ menu shows "Open multirepo agent in <default runner>" and puts the other runner in an "Open multirepo agent with…" submenu.
- The summary shown with nothing selected becomes informative only; "Clean up" stays in the view's ⋯ menu.
- The second line of a row's name cell shows only the group tag, the folder and the project, separated by " · "; the origin stays in the inspector's chip and is added to the branch's tooltip.
- Machine views write modes ("Automatic", the capacity profile) and states ("Not acquired", "Not created") in the interface font, and every state chip in sentence case ("Stopped", "Running", "Shut down", "Over the limit").
- The command palette lists Preferences as a setting, with its own kind and icon, not as a view.
- The row's time cell shows a compact activity ("12 h", "2 d") when the list is narrow, with the full text in a tooltip.
- The alias column of the multirepo view fits its longest alias within limits, and the group header's branch line has a tooltip.
- Inspector property keys and values that don't fit show their full text in a tooltip.
- Cmd/Ctrl+W closes the Preferences window, and Escape closes the command palette on macOS, after checking that each failure is real.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `action-catalog`: view header ⋯ with one multirepo agent entry plus "Open multirepo agent with…"; group header with one runner button and a ⋯ menu holding the other runner, "Add repository" and "Clean up group".
- `multirepo-desktop`: group header actions and the multirepo agent entries move as above.
- `worktree-row`: the name cell's second line drops the origin and is separated; the time cell gets a compact format.
- `worktree-detail`: the summary with nothing selected has no "Clean up" action.
- `machine-resources`: modes and states in the interface font, state chips in sentence case.
- `command-palette`: Preferences is a setting item; Escape closes the palette on every platform.
- `inspector-sections`: truncated keys and values show their full text in a tooltip.
- `keyboard-shortcuts`: Cmd/Ctrl+W closes the Preferences window.

## Impact

- `desktopApp`: `ui/actions` (`ViewActionCatalog.kt`, `ActionSpec.kt`), `worktree/WorktreeListPane.kt`, `worktree/WorktreeScreen.kt`, `worktree/ProjectSummary.kt`, `worktree/WorktreeRow.kt`, `worktree/WorktreeRowModel.kt`, `worktree/RelativeTime.kt`, `machine/` views and labels, `palette/`, `components/PropertyList.kt`, `shell/PreferencesWindow.kt`, `i18n/` (English and Spanish).
- Docs: `docs/architecture/ui.md` and its Spanish translation.
- No change to `core`, the CLI or settings files.
