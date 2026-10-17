## 1. Domain: theme mode and persistence port

- [x] 1.1 Create `domain/model/ThemeMode.kt` with `enum class ThemeMode { LIGHT, DARK, SYSTEM }`
- [x] 1.2 Create the `domain/io/ThemePreferenceRepository.kt` port with `fun load(): ThemeMode` and `fun save(mode: ThemeMode)`

## 2. Infrastructure: JSON persistence

- [x] 2.1 Create `infrastructure/JsonThemePreferenceRepository.kt`, persisting to `~/.shogunai/settings.json` with `JsonProjectRepository`'s atomic write pattern (temporary file + `Files.move` with `ATOMIC_MOVE`)
- [x] 2.2 `load()` returns `ThemeMode.SYSTEM` if the file doesn't exist, is empty or can't be parsed
- [x] 2.3 Wire `JsonThemePreferenceRepository` in `AppContainer` as `themePreferenceRepository`

## 3. Light palette

- [x] 3.1 In `ui/theme/Color.kt`, add the `ShogunLight*` set (background, surface, surfaceVariant, onBackground, onSurface, onSurfaceVariant) keeping `ShogunRed`/`ShogunSlate` as accents
- [x] 3.2 Check ~4:1 contrast between text and backgrounds in the light palette (same criterion applied to the dark one in `app-icon-and-visual-style`)

## 4. Theme resolves light/dark/automatic

- [x] 4.1 In `ui/theme/Theme.kt`, add `ShogunLightColorScheme` (`lightColorScheme(...)`) next to the existing `ShogunDarkColorScheme`
- [x] 4.2 Change `ShogunAiTheme`'s signature to `ShogunAiTheme(themeMode: ThemeMode, content: @Composable () -> Unit)`, resolving `useDark` from the OS theme when `themeMode == SYSTEM` (`rememberSystemInDarkTheme()`, see design decision 4), and applying the matching `colorScheme`
- [x] 4.3 Remove the comment in `Theme.kt`/`Color.kt` that described the scheme as "single, with no light/dark variant"

## 5. Theme mode state and UI control

- [x] 5.1 In `AppRoot.kt`, load the initial `ThemeMode` from `appContainer.themePreferenceRepository.load()` and keep it in `mutableStateOf`
- [x] 5.2 When the mode changes from the UI, update the state and call `themePreferenceRepository.save(mode)`
- [x] 5.3 Create the `ThemeModeToggle` composable in `ui/theme/` (menu or segmented button with the 3 options: Light/Dark/Automatic)
- [x] 5.4 Anchor `ThemeModeToggle` in `AppRoot.kt` so it's visible on all three screens (`ProjectListScreen`, `ProjectConfigScreen`, `WorktreeScreen`), without modifying each screen individually
- [x] 5.5 Pass the resolved `themeMode` to `ShogunAiTheme` in `AppRoot.kt`

## 6. Verification

- [x] 6.1 Confirm the app starts in Automatic mode by default (no previous `settings.json`) and reflects the current operating system theme. An earlier manual check on macOS (Corretto) had reported that it didn't. Re-checked on 2026-10-16 with the packaged app (`createDistributable`) launched with an empty, isolated home directory on macOS arm64 with the OS in dark mode: the toggle showed "Automatic" and the dark `colorScheme` was applied. A standalone probe confirmed that `currentSystemTheme` matches `defaults read -g AppleInterfaceStyle` (see design.md, Context).
- [x] 6.2 Switch manually to Light and to Dark from the UI control and confirm the `colorScheme` changes immediately on all three screens. Confirmed by the user: the control no longer overlaps other buttons, and choosing Light/Dark changes the scheme.
- [x] 6.3 Restart the app after choosing a manual mode and confirm it starts with that preference (reads `~/.shogunai/settings.json`). Checked on 2026-10-16 by launching the packaged app with `{"themeMode": "LIGHT"}` stored while the OS was in dark mode: it started in the light `colorScheme` with "Light" selected. The save/reload round trip is also covered by `JsonSettingsFileTest`.
- [x] 6.4 Change the operating system theme while the app is open in Automatic mode and confirm the app updates without restarting. **Not re-verified when closing the change**: doing so means changing the operating system's appearance, which the closing agent didn't do. The mechanism reads the OS value again on every one-second poll (Skiko queries `NSUserDefaults` natively on each call; see design.md, Context). Closed with this limitation explicitly accepted by the user on 2026-10-16. If live switching turns out not to work on some JDK, track it as a new change.
- [x] 6.5 Run `./gradlew test` and confirm the existing tests still pass
- [x] 6.6 Confirm no new Gradle dependencies were introduced
