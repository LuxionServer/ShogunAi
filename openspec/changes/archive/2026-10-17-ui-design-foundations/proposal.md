# Proposal

## Why

The desktop app grew feature by feature without a design system. Its color scheme only defines 14 Material roles, so dialogs, menus and selected segments fall back to Material's baseline purple. The brand red is used both for success-like states ("merged", "emulator running") and for warnings ("locked", "inactive"), next to a red error color. Body text in the brand color misses WCAG AA contrast. Paths, ports and sizes use proportional figures that shift on every refresh. And spinners, notices and destructive buttons are re-created by hand on each screen. This change is the first step of the `epic/ui-redesign` epic: every later change builds on these foundations.

## What Changes

- Complete light and dark color schemes: every Material 3 color role is defined by the app, from one cool neutral ramp and one brand accent.
- Semantic status colors (success, warning, info, danger, neutral), each with a single meaning across the app. The brand accent is no longer used to convey status.
- Contrast targets for text and status indicators in both schemes, checked by tests.
- Bundled type families: a sans family for UI text and a monospaced family, with tabular figures, for data (paths, branches, ports, PIDs, sizes, counts). A complete type scale, so no style falls back to Material defaults.
- Shared components that replace the per-screen copies: inline progress indicator, feedback notice (error, warning, success, info), status chip, destructive button style, icon action with tooltip, and hover and pointer feedback for custom clickable elements.
- A shape scale and an extended spacing scale used through the theme instead of literals.
- The existing screens adopt the components and tokens, without changing their structure (the structure is redesigned by later changes of the epic).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `visual-theme`: the app's theme defines every color role. Status colors have a single meaning each. Text meets contrast targets, and data text is monospaced with tabular figures.
- `ui-consistency`: interactive elements give hover and pointer feedback, icon-only actions show a tooltip, destructive actions are visually distinct, keyboard focus is visible, and feedback notices share one presentation.

## Impact

- `desktopApp/src/main/kotlin/app/luxion/shogunai/ui/theme/` (`Color.kt`, `Type.kt`, `Theme.kt`, new shape and status color definitions).
- `desktopApp/src/main/kotlin/app/luxion/shogunai/ui/components/` (new shared components; `Spacing.kt` extended).
- Every screen and section composable, to adopt the tokens and components.
- New font resources (OFL-licensed) under `desktopApp/src/main/resources/fonts/`, with their license file.
- `docs/architecture/ui.md` and `ui.es.md` gain a "Design system" section.
