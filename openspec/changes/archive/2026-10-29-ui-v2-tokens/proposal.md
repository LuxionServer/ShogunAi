# Proposal

## Why

The brand red is the theme's `primary`, so it paints the main action, the NEW state, repository aliases, links, the active settings entry, "This machine only" and the keyboard focus ring, and its container is the same color as danger's: any red text reads as an error, and "Clean up" next to "New worktree" makes deleting look like creating. The shared buttons are Material pills of about 40 dp that screens shrink with `bodySmall`, there are no elevation levels, a view title takes 22 sp, and almost everything in a row is monospaced. This is the first change of the `epic/ui-redesign-v2` epic (design system v2, direction A): the forms and states change and the status bar change build on its tokens and button contract.

## What Changes

- **Color roles**: `primary` becomes ink (Neutral9 in light, Neutral91 in dark) with neutral `onPrimary` and containers; `surfaceTint` stops being red. SlateBlue becomes the accent, a role of its own in `ShogunTheme` (selection, focus, links, active entry), with `tertiary` kept as its alias. The brand red stays only as `ShogunTheme.colors.brand`, never on a control or a state. New semantic aliases `attention` (= warning) and `active` (= info). A `scrim` token with its alpha replaces the literal overlay color of the worktree screen. A `disabled` content and container pair reaching 3:1 on every surface.
- **Elevation**: three levels, `level0` (surface), `level1` (surfaceContainerLow with a 1 dp border) and `level2` (surfaceContainerHigh with a border and a small shadow, for menus, dialogs and popovers), exposed as `ShogunTheme.elevation`.
- **Current uses of `primary`** move to their role: aliases and group tags to `onSurfaceVariant`, the Machine owner link and Markdown links to the accent, the active settings index entry to the accent, "This machine only" to secondary text, the focus ring, the drop outline and the palette cursor to the accent, the selected worktree row to the accent container.
- **NEW** gets its own status (neutral) and an outlined badge instead of falling back to `primary`.
- **Typography**: `ShogunTheme.typography` adds `viewTitle` (Geist 600 16/22), `sectionTitle` (Geist 600 13/18) and `eyebrow` (Geist Mono 500 10.5/14, +0.06em, uppercase applied by its composable). View headers use `viewTitle` instead of `headlineSmall`. Body stays 13/18 and secondary 12/16; Geist, Geist Mono and `tnum` are kept.
- **Mono is for data only**: agent states ("working", "busy", "idle · 12 min ago"), relative dates written as text and project names move to the sans; branches, paths, ports, PIDs, sizes and counts stay in mono.
- **Spacing, radii, sizes**: `Spacing.smd = 12.dp`. Radii 4 (controls), 6 (rows, menu items), 8 (panels, menus), 12 (dialogs); the pill, 14 and 20 are retired. Controls are 28 dp (compact) or 32 dp (regular) high with 10 dp horizontal padding; icon buttons are 28×28. Layout constants: sidebar 196–240, inspector 320, collapse at 760.
- **Buttons**: `PrimaryButton`, `SecondaryButton`, `DangerButton`, `DangerTextButton` and `NeutralTextButton` keep their names and parameters and are rebuilt inside (4 dp radius, 28/32 dp, label style set by the button); a `size: ButtonSize` parameter (`Compact`, `Regular`) and a new `GhostButton` are added. The `Text(style = bodySmall)` inside buttons are removed outside the files vetoed by `orca-integration`. A filled danger button only appears in a confirmation dialog.
- **Header**: "Clean up" is a `GhostButton` next to the single ink action of the view.
- **Tests**: contrast tests for ink as primary, the accent, disabled content and the elevation levels in light and dark; structure tests for the buttons (heights, radius, label on one line).
- **Docs**: the design system section of `docs/architecture/ui.md` and `ui.es.md`, with the color and type rules of design v2.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `visual-theme`: ink as primary, accent and brand roles, semantic aliases, disabled contrast, elevation levels, typography roles, mono only for data, radius and control size scales.
- `ui-consistency`: focus indicator in the accent; destructive actions keep the danger color, but a filled danger button only in a confirmation and an action that only opens a confirmation from a view's header is neutral; buttons have two sizes and no pill.
- `worktree-row`: the NEW state badge is a neutral outlined badge, aliases and group tags use the secondary text color, and the selected row uses the accent container.

## Impact

- `desktopApp/.../ui/theme/**` (colors, scheme, shapes, type, status, new elevation and layout tokens), `components/Buttons.kt`, `Spacing.kt`, `Interactive.kt`, `StatusChip.kt`.
- Color and typography of screens: `worktree/WorktreeRow.kt` (not its context menu), `WorktreeRowModel.kt`, `WorktreeScreen.kt`, `WorktreeListPane.kt`, `ProjectSummary.kt`, `machine/MachineViewParts.kt` and the daemon views, `projectconfig/ProjectSettingsView.kt`, `SimulatorSettingsSection.kt`, `EmulatorSettingsSection.kt`, `multirepo/MultirepoSettingsView.kt`, `components/MarkdownText.kt`, `components/ErrorNotice.kt`, `palette/CommandPalette.kt` (cursor only), `shell/AppShell.kt` (drop outline and empty state title only).
- Not touched (vetoed by `orca-integration`): `WorktreeDetailPane.kt`, `AgentPicker.kt`, `OpenActions.kt`, the row's context menu, the removal and cleanup dialogs, `PaletteIndex.kt`, `TrayMenuModel.kt`, `AppMenuBar.kt`. Their colors follow the theme; their `bodySmall` button labels stay as a pending task after `orca-integration`.
- Dependent changes: `ui-v2-forms-states` and `ui-v2-status-bar` consume the new tokens and the button contract.
- No domain, CLI or dependency changes.
