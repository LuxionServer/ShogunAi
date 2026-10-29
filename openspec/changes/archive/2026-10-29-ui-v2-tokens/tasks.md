# Tasks

## 1. Color tokens

- [x] 1.1 `Theme.kt`: ink `primary`/`onPrimary`, neutral `primaryContainer`/`onPrimaryContainer`, `inversePrimary`, `primaryFixed*` and a neutral `surfaceTint`, in light and dark; `tertiary` stays SlateBlue
- [x] 1.2 `ShogunColors` (accent, onAccent, accentContainer, onAccentContainer, brand, disabledContent, disabledContainer, scrim) for light and dark, provided by `ShogunAiTheme` and read as `ShogunTheme.colors`
- [x] 1.3 `ShogunStatusColors.attention` and `active` aliases
- [x] 1.4 Elevation: `ElevationLevel`, `ShogunElevation`, `ShogunTheme.elevation` and `Modifier.elevation(level, shape)`
- [x] 1.5 Contrast tests in `ThemeContrastTest`: ink primary, accent text and focus on every surface, accent container with text, secondary text and status colors, disabled content on every surface and on the disabled container, elevation levels' text and borders; `ColorSchemeTest`: primary container differs from the danger container and no role keeps the brand red; alias test

## 2. Typography, shapes, spacing and layout

- [x] 2.1 `ShogunTextStyles.viewTitle`, `sectionTitle`, `eyebrow` and the `Eyebrow` composable; `TypographyTest` covers their family, weight, size, line height and letter spacing
- [x] 2.2 `Shape.kt`: `ControlShape`, `RowShape`, `PanelShape`, `DialogShape` and `ShogunShapes` 4/6/8/12/12; remove `PillShape`, `InteractiveShape` becomes `RowShape`; a test asserts the scale
- [x] 2.3 `Spacing.smd = 12.dp`
- [x] 2.4 `ShogunLayout` with the sidebar, inspector and breakpoint widths

## 3. Buttons and focus

- [x] 3.1 Focus ring in the accent (`Interactive.kt`) and the folder drop outline in the accent (`AppShell.kt`)
- [x] 3.2 Rebuild `PrimaryButton`, `SecondaryButton`, `DangerButton`, `DangerTextButton`, `NeutralTextButton` on `Surface` with `ButtonSize` (`Compact` 28 dp, `Regular` 32 dp), 10 dp padding, the control shape, the label style and the disabled tokens; add `GhostButton`
- [x] 3.3 UI tests: heights per size, every button keeps a long label on one line, disabled buttons don't run their action, `GhostButton` is in `ButtonLabelsUiTest`
- [x] 3.4 `StatusChip` keeps the control radius (`extraSmall` is `ControlShape`); `IconAction` becomes 28 × 28 with the control shape and the disabled token

## 4. Screens

- [x] 4.1 `StateBadgeKind.NEW` with `Status.NEUTRAL` and outlined rendering; non-null `status`; update the palette indicator and the row model tests
- [x] 4.2 `WorktreeRow.kt` (not its context menu): alias and group tag in `onSurfaceVariant`, selected row on the accent container, agent line, activity date, marks and project name in the sans
- [x] 4.3 `WorktreeScreen.kt`: header title in `viewTitle`, "Clean up" as `GhostButton`, scrim token, `ShogunLayout` inspector width and breakpoint; empty "nothing active" title in `viewTitle`
- [x] 4.4 Remove `bodySmall` button labels in `WorktreeListPane.kt`, `ProjectSummary.kt`, `ErrorNotice.kt` and `MachineViewParts.kt`
- [x] 4.5 Machine: owner link in the accent, `MachineViewFrame` title in `viewTitle`, daemon states and "waiting since" in the sans
- [x] 4.6 Project settings: active index entry in the accent, "This machine only" in secondary text (also in the simulator and emulator sections), agent name in `onSurface`, view titles in `viewTitle` (`SettingsSection` keeps its title size, see design)
- [x] 4.7 Markdown links in the accent, palette cursor in the accent and palette panel on elevation level 2, first-launch empty state title in `viewTitle`, multirepo settings title in `viewTitle` and "Delete multirepo" as `DangerTextButton`
- [x] 4.8 No `colorScheme.primary` left outside `ui/theme`, `components/Buttons.kt` and the vetoed files (checked with a search)

## 5. Docs

- [x] 5.1 `docs/architecture/ui.md` and `ui.es.md`: palette (ink, accent, brand, aliases, disabled, scrim), elevation, type roles and the mono rule, radii and sizes, spacing and layout tokens, buttons, and the color and type rules of design v2

## 6. Validation

- [x] 6.1 `./gradlew test` green with no warnings
- [x] 6.2 Check the app with hot reload in light and dark: worktree views and header, row selection and NEW badge, detail at 320 dp, Machine views, project settings, palette, disabled buttons

## 7. After orca-integration

- [ ] 7.1 (tras orca-integration) `WorktreeDetailPane.kt`: remove the `bodySmall` button labels and move the agent state and dates to the sans
- [ ] 7.2 (tras orca-integration) Removal and cleanup dialogs: check their buttons against the new sizes when `ShogunDialog` lands
