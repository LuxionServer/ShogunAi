## Why

Today the app applies a single fixed dark `colorScheme` (an explicit decision of `visual-theme`), without adapting to the operating system's light/dark mode. Users who work with the OS in light mode are forced into a dark UI, and there is no way to choose. We want the app to follow the system theme automatically and, optionally, let the user pin a manual mode (light/dark) that is remembered across sessions.

## What Changes

- Add a light color scheme (`ShogunLightColorScheme`) next to the existing dark one.
- Introduce a theme mode with three values: `Light`, `Dark`, `Automatic` (follows the OS). `Automatic` is the default.
- `ShogunAiTheme` resolves the effective scheme from the OS theme when the mode is `Automatic`, or uses the matching fixed scheme when the mode is `Light`/`Dark`.
- Persist the mode chosen by the user across app restarts (new local settings file, following the JSON persistence pattern already used for projects).
- Add a UI control for the user to switch between `Light` / `Dark` / `Automatic`.
- **BREAKING**: replaces the previous `visual-theme` requirement that fixed a single `colorScheme` independent of the operating system.

## Capabilities

### New Capabilities
(none — the change extends the existing `visual-theme` capability; persisting the preference is documented as part of that same capability, not as a generic "settings" capability that doesn't exist yet)

### Modified Capabilities
- `visual-theme`: the "Single color scheme (no light/dark)" requirement is replaced by light/dark/automatic mode support, following the operating system's theme and with a persisted manual preference.

## Impact

- `ui/theme/Color.kt`: adds the light palette.
- `ui/theme/Theme.kt`: `ShogunAiTheme` receives the theme mode and resolves light/dark/automatic.
- `ui/AppRoot.kt`: exposes the theme mode state to the composition tree.
- New domain model `ThemeMode` (`Light` / `Dark` / `System`) and a persistence port (`domain/io`) to read/save the preference.
- New infrastructure implementation (local JSON, same pattern as `JsonProjectRepository`) and its wiring in `AppContainer`.
- New UI control (menu or toggle) to change the mode, visible from an existing screen.
- No new Gradle dependencies (Compose already brings Skiko, whose `currentSystemTheme` is used to read the OS theme).
