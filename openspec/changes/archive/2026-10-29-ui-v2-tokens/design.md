# Design

## Context

The theme (`ui/theme`) already defines every Material 3 role from one cool neutral ramp, status tokens with tested contrast, Geist and Geist Mono with tabular figures, and a radius scale that no button uses. The brand red is `primary`, so Material controls and seven screen call sites inherit it (`WorktreeRow.kt:164,174,308`, `MachineViewParts.kt:146`, `ProjectSettingsView.kt:307,648,728`, `SimulatorSettingsSection.kt:38`, `EmulatorSettingsSection.kt:28`, `MarkdownText.kt:137`, `CommandPalette.kt:194`, `Interactive.kt:35`, `AppShell.kt:161`). The five shared buttons wrap Material's `Button`, `OutlinedButton` and `TextButton` with a pill focus ring and Material's 40 dp height.

This change applies section "02 · Design system v2" of the designer's v2 document (direction A) and these of its rules for agents:

- "One main action per view, in ink" — ink `primary`, a single `PrimaryButton` in the view header.
- "Destructive never goes in a bar" — the filled danger button only in a confirmation; "Clean up" leaves the danger color in the header (it moves to the ⋯ menu with `ui-v2-action-catalog`).
- "Color only means state, always through the mapping" — NEW gets a status, the brand never on controls, the scrim becomes a token.
- "Mono is for data, not for states".
- "A disabled control is visible" — 3:1, tested. The tooltip with the reason belongs to `ui-v2-forms-states`.
- "Accessibility is checked by tests" — contrast of ink, accent, disabled and elevation; focus in the accent.
- "What already works is not touched" — the neutral ramp, the status tokens, Geist, Geist Mono and `tnum` stay as they are.

Three changes run in parallel on the epic. This one owns `ui/theme/**`, `components/Buttons.kt`, `Spacing.kt`, `Interactive.kt`, `StatusChip.kt` and the color and typography of screens. Files vetoed by `orca-integration` (`WorktreeDetailPane.kt`, `AgentPicker.kt`, `OpenActions.kt`, the row's context menu, the removal and cleanup dialogs, `PaletteIndex.kt`, `TrayMenuModel.kt`, `AppMenuBar.kt`) aren't edited; their colors follow the theme by themselves.

## Goals / Non-Goals

**Goals:**

- Ink as `primary`, SlateBlue as an accent role, the brand red only as a brand token, `attention`/`active` aliases, a scrim token and a disabled pair, all with contrast tests.
- Three elevation levels as theme tokens.
- Typography roles for view title, section title and section label; view headers use the view title.
- `Spacing.smd`, the 4/6/8/12 radius scale, 28/32 dp controls and named layout widths.
- The five buttons rebuilt with the same names and parameters, plus `size` and `GhostButton`.
- Every current use of `primary` outside the vetoed files moved to its role.

**Non-Goals:**

- New components (`Field`, `ShogunDialog`, states, the disabled reason tooltip, `heading()` semantics): `ui-v2-forms-states`.
- The status bar, the sidebar's layout and its selection: `ui-v2-status-bar`. The sidebar adopts the sidebar width tokens there.
- `SplitButton`, `ShogunMenu`, the action catalog and the detail's action bar: `ui-v2-action-catalog`.
- `DataRow`, `InspectorSection` and row heights: `ui-v2-inspector-and-rows`.
- Restyling Material's text fields, dropdown menus, switches and checkboxes beyond what the color scheme gives them.

## Decisions

### Ink primary and neutral containers

`primary` is Neutral9 with `onPrimary` Neutral100 in light (17.8:1) and Neutral91 with `onPrimary` Neutral6 in dark (15.7:1), the mockup's values. `primaryContainer`/`onPrimaryContainer` become Neutral89/Neutral9 (light) and Neutral22/Neutral91 (dark), `inversePrimary` the opposite ink, the `primaryFixed*` roles the neutral fixed values, and `surfaceTint` a mid neutral (Neutral50/Neutral70), so tonal elevation never tints red. The primary container no longer equals the danger container.

Material controls that read `primary` (switch track, checkbox, radio, progress indicators, text field focus) become ink. That reads as a neutral, calm "on" state, and it's what the forms change replaces with `Field` anyway. Alternative considered: mapping `primary` to the accent and adding an "ink" role for buttons only. Rejected: every Material control would turn blue and compete with selection and links, and the designer chose ink as the primary role.

### Accent, brand, disabled and scrim in `ShogunTheme.colors`

A `ShogunColors` data class provided like the status colors, with `accent`, `onAccent`, `accentContainer`, `onAccentContainer` (the existing SlateBlue tokens), `brand` (the scheme's brand red), `disabledContent`, `disabledContainer` and `scrim`. `tertiary` keeps the SlateBlue values, so it remains an alias of the accent for Material components that read it.

Disabled content is Neutral53 in light and Neutral50 in dark, and the disabled container is `surfaceContainerHighest`. The minimum ratio is 3.04:1 (light, on `surfaceContainerHighest`) and 3.10:1 (dark), checked on every surface by the tests. Material's default disabled color (`onSurface` at 38 %) is around 2.5:1, which is what the audit saw.

The scrim is `Neutral2` at 40 % alpha (the previous literal was black at 40 %), so the overlay looks the same.

### Status aliases and NEW

`ShogunStatusColors` gets `attention` (returns `warning`) and `active` (returns `info`) as read-only properties; `Status` keeps its five values, so `StatusMapping` and its tests don't change. `StateBadgeKind.NEW` becomes `Status.NEUTRAL` with an `outlined` flag; `status` stops being nullable, which removes the only fallback to `primary` in `WorktreeRow` and the palette's indicator.

### Elevation as tokens plus one modifier

`ShogunElevation(level0, level1, level2)`, each an `ElevationLevel(container, border, shadow)`, exposed as `ShogunTheme.elevation`, and `Modifier.elevation(level, shape)` in `ui/theme` that draws the shadow, the container and the 1 dp border. Level 1 and 2 use `outlineVariant` for the border. The border carries the separation (the design prefers borders to shadows), so the test asks it to stay distinguishable from its container and the background at 1.3:1; it's decorative, so WCAG's 3:1 for component boundaries doesn't apply. Level 2's shadow is 4 dp. Consumers (menus, dialogs, popovers) arrive with the other changes; this one applies level 2 to the command palette's panel only.

### Typography roles

`ShogunTextStyles` gains `viewTitle`, `sectionTitle` and `eyebrow`. Uppercasing can't live in a `TextStyle`, so an `Eyebrow(text)` composable in `ui/theme` uppercases with the current locale. The Material slots keep their values, because dialogs, menus and fields read them; only the view headers change from `headlineSmall` to `viewTitle` (worktree views, project settings, add project, multirepo settings, Machine views and the first-launch empty state). Dialog titles move to `sectionTitle` with `ShogunDialog` in the forms change. The project settings section headings (`SettingsSection`) keep their size: at 13/18 they read the same as the field labels, and the forms change rebuilds that form with `Field`.

`data` and `dataSmall` keep their 13/18 and 12/16 sizes. The design's table lists 12/16 and 11.5/16 but marks them as kept; resizing data would change every row's density, which belongs to `ui-v2-inspector-and-rows`.

### Mono only for data

In the row, the agent line ("Claude · working"), the activity date ("commit 3 h ago"), the marks and the project name move to `bodySmall`; the branch, the folder, the Git counts and the alias stay in mono. In the Machine views, daemon states and "waiting since" texts move to a `MachineSecondaryText`-style sans call; serials, PIDs, sizes and counts stay in mono. The worktree detail's agent and dates are in a vetoed file and wait for `orca-integration`.

### Radii and sizes

`Shape.kt` names the scale: `ControlShape` 4, `RowShape` 6, `PanelShape` 8, `DialogShape` 12, and `ShogunShapes` maps `extraSmall` 4, `small` 6, `medium` 8, `large` 12, `extraLarge` 12, so Material dialogs (which read `extraLarge`) get 12 and cards 8. `PillShape` is removed; `InteractiveShape` becomes `RowShape`. The segmented selector keeps Material's segment shapes for now: they belong to the forms change's controls.

`Spacing.smd = 12.dp`. Layout widths live in `ui/theme/Layout.kt` (`ShogunLayout.sidebarMinWidth` 196, `sidebarMaxWidth` 240, `inspectorWidth` 320, `compactBreakpoint` 760); the worktree screen uses the last two, so the detail pane goes from 360 to 320 dp.

### Buttons built on `Surface`

Each button is a `Surface(onClick)` with the control shape, the button's colors and border, and a `Row` of the size's height with 10 dp horizontal padding and `ProvideTextStyle(labelLarge)` (Geist 500 13/18). `Surface(onClick)` already gives the button role, the hover and press state layers of the theme's ripple and the disabled handling; the focus ring (now accent, with the control shape) and the hand pointer stay. `singleLineLabel` stays, so labels never wrap.

| Button | Fill | Border | Content |
|---|---|---|---|
| Primary | primary | none | onPrimary |
| Secondary | surfaceContainerLowest | outlineVariant | onSurface |
| Ghost | none | none | onSurfaceVariant |
| Danger | danger | none | onDanger |
| DangerText | none | none | danger |
| NeutralText | none | none | onSurface |

Disabled: filled buttons use the disabled container, all of them the disabled content, Secondary keeps its border. `size: ButtonSize = ButtonSize.Compact` is a new trailing-default parameter, so every caller compiles unchanged; dialogs and forms pass `Regular` once they move to `ShogunDialog` and `Field`. Alternative considered: keep wrapping Material's buttons and override their min height and padding. Rejected: Material's inner `defaultMinSize(40.dp)` and its state colors fight the 28 dp height and the disabled token.

`IconAction` keeps its size; its 28 × 28 target is checked and adjusted only if it differs, since the forms change edits it for the disabled tooltip.

### Screen call sites

Header: "Clean up" becomes `GhostButton`, "Reload" stays secondary (the catalog removes it). `MultirepoSettingsView`'s "Delete multirepo", outside a dialog, becomes `DangerTextButton`. `Text(style = bodySmall)` inside buttons are removed in `WorktreeListPane`, `ProjectSummary`, `ErrorNotice` and `MachineViewParts`; the eleven in `WorktreeDetailPane` are a task after `orca-integration`. The selected row's background moves from `surfaceVariant` to the accent container. The agent name in the project settings' agent list moves from `primary` to `onSurface` with `sectionTitle`.

## Risks / Trade-offs

- [Parallel changes conflict in shared screens] → the edits in `WorktreeRow.kt`, `AppShell.kt`, `ErrorNotice.kt` and the dialogs' neighbours are one-line color or style swaps; this change merges first and the others merge the epic into their branches.
- [Ink switches and checkboxes look different from v1] → accepted; they're checked in the app in both schemes, and `Field` arrives next.
- [A 320 dp inspector wraps the detail's action rows] → validated in the app; the detail's actions are rebuilt by the catalog change.
- [Compact buttons inside existing `AlertDialog`s are 28 dp until `ShogunDialog`] → acceptable for one merge; the forms change passes `Regular`.
- [Vetoed files keep `bodySmall` labels and mono agent text] → listed as tasks after `orca-integration` in this change's `tasks.md`, so they aren't lost.
