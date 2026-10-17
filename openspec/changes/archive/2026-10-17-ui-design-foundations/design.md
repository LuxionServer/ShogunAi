# Design

## Context

First change of `epic/ui-redesign` (see `docs/roadmap.md`, "Desktop app redesign"). The theme lives in `ui/theme/` (`Color.kt` with 24 literals, `Theme.kt` with two partial schemes plus `ShogunExtendedColors(success, onSuccess)`, `Type.kt` with six styles). `ui/components/` has `Spacing`, `SectionCard`, `SegmentedSelector` and `DropdownSelector`. Spinners (about 15 copies with 12/14/20 dp sizes), notices (`WorktreeScreen`, `CleanupIssues`, `DaemonStopMessage`) and destructive buttons are written inline on each screen. There are no Compose UI tests. The UI is verified through ViewModel unit tests and by running the app.

The structure of the screens is replaced by later changes of the epic. This change only provides the tokens and components and moves the current screens onto them, so the app looks coherent while the epic is in progress.

## Goals / Non-Goals

**Goals:**
- A theme where every Material role and every status color is defined once and has a single meaning.
- Shared components that later changes reuse instead of re-creating.
- Contrast that is checked by a test, not by eye.

**Non-Goals:**
- Changing navigation, layout or screen structure (changes 2 to 11 of the epic).
- Animations beyond Material's defaults, and a reduced-motion setting.
- Theming per project.

## Decisions

### Palette: one cool neutral ramp, one brand accent, five status colors
A neutral ramp of cool slate tones (about 12 steps from near-black `#0F1115` to near-white `#F8F9FB`) feeds every surface, surface container, outline and on-color role in both schemes. The brand red stays as `primary`, only for the main action of a view and for brand marks. It is darkened in the light scheme so white text on it reaches 4.5:1. Status colors are separate tokens, each with a container variant for chips:

| Token | Meaning | Examples |
|---|---|---|
| success | done or healthy | merged, device running, operation succeeded |
| warning | needs your attention, not broken | uncommitted changes, unpushed commits, inactive |
| info | work in progress | agent running, booting, loading notes |
| danger | failed or destructive | errors, missing folder, delete actions |
| neutral | plain state | locked, other origin, idle |

The status colors are exposed through the existing `CompositionLocal`, extended to a `ShogunStatusColors` with `color`, `onColor`, `container` and `onContainer` per status, accessed as `ShogunTheme.status`. *Alternative considered:* mapping statuses onto `primary`/`secondary`/`tertiary`. Rejected because Material's roles carry emphasis, not meaning, which is exactly how red ended up meaning both "merged" and "locked".

### Contrast checked in a unit test
A small `contrastRatio(foreground, background)` function (WCAG relative luminance) lives next to the theme. `ThemeContrastTest` asserts the pairs that matter in both schemes: on-colors on their roles, status colors on `background` and `surfaceContainer*`, and `onSurfaceVariant` on surfaces. Picking colors by eye is how the current 4.2:1 and 4.4:1 pairs slipped in.

### Fonts: Geist and Geist Mono, bundled as static weights
Geist (UI) and Geist Mono (data) come from one family, so they share their metrics, both are under the SIL Open Font License, and they read as a developer tool without being the default Inter or Roboto. They are bundled as static TTFs in weights 400, 500 and 600 under `desktopApp/src/main/resources/fonts/`, with `OFL.txt`, and loaded with `androidx.compose.ui.text.platform.Font(resource = ...)`. *Alternatives considered:* the system font, rejected because Compose Desktop doesn't resolve the native UI font consistently across the three platforms. JetBrains Mono with Inter, rejected because the two families come from different designs. Variable fonts, rejected because static weights are simpler to load and three weights are enough.

### Complete type scale with a data style
Every `Typography` slot is defined (display to label), with Geist and sizes tuned for a dense desktop tool (body 13–14 sp, labels 11–12 sp). Two extra styles live in `ShogunTheme.typography`: `data` (Geist Mono, 13 sp, `fontFeatureSettings = "tnum"`) and `dataSmall` (12 sp). Paths, branches, ports, PIDs, sizes, counts and relative times use them.

### Shapes and spacing
`Shapes` gets an explicit scale (`extraSmall` 4, `small` 6, `medium` 10, `large` 14, `extraLarge` 20 dp). The rule is that inner radius is smaller than outer radius. `Spacing` gains `xxs = 2.dp` and `xl = 32.dp`. Literal dp values in screens are replaced by tokens, apart from component-internal sizes such as icon sizes, which get named constants.

### Shared components
In `ui/components/`:
- `InlineProgress(size = Small | Medium)`: the one spinner, with fixed sizes and stroke widths.
- `Notice(kind, message, onDismiss, details = null)`: icon, color and dismiss action per kind. The `details` slot is used by `understandable-errors` later.
- `StatusChip(status, label, icon = null)`: compact chip on the status container color. Used heavily by `worktree-list-detail`.
- `dangerButtonColors()` / `DangerButton` / `DangerTextButton`: destructive styles.
- `IconAction(icon, label, onClick, enabled)`: `IconButton` inside a `TooltipArea`, with `contentDescription = label`.
- `Modifier.interactive(onClick, enabled)`: `clickable` with a hover indication, `pointerHoverIcon(PointerIcon.Hand)` when enabled, and a focus ring. Used for custom clickable rows and headers.

A visible focus ring comes from a `focusRing` modifier that draws an outline in `primary` when focused. It is applied by `interactive` and by the shared buttons.

## Risks / Trade-offs

- [Fonts add about 600 KB to the installers] → Acceptable for a desktop app; only three weights per family are shipped.
- [The current screens get new components that the epic later replaces] → The adoption is mechanical (swap a call), and it keeps the app shippable from `develop` if the epic stops halfway.
- [Brand red darkened in the light scheme looks slightly different from the icon] → The icon keeps its color; only interactive surfaces use the adjusted tone.
- [`TooltipArea` is experimental in Compose Desktop] → Contained in `IconAction`, so an API change touches one file.
