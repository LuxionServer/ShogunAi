## Context

`ShogunAiTheme` (`ui/theme/Theme.kt`) always applied `ShogunDarkColorScheme`, a fixed `darkColorScheme()` defined in `ui/theme/Color.kt`. `AppRoot.kt` is the only place where `ShogunAiTheme { ... }` is invoked, wrapping the `when` that switches between `ProjectListScreen`, `ProjectConfigScreen` and `WorktreeScreen`. There was no mechanism to persist user preferences: the only precedent is `JsonProjectRepository` (`infrastructure/JsonProjectRepository.kt`), which persists a list of projects as JSON in `~/.shogunai/projects.json` with an atomic write (temporary file + `Files.move` with `ATOMIC_MOVE`). The project uses Compose Multiplatform 1.11.1 for desktop.

**Post-implementation correction**: the initial assumption that `isSystemInDarkTheme()` reacts at runtime in Compose Desktop 1.11.1 was wrong. Its implementation (`androidx.compose.foundation.isSystemInDarkTheme` → `LocalSystemTheme`, a `staticCompositionLocalOf`) computes the value once, lazily, and doesn't update if the operating system theme changes while the app is open — in `desktop-jvm` 1.11.1 there is no listener/`CompositionLocalProvider` keeping it up to date. See decision 4 for the fix (polling `org.jetbrains.skiko.currentSystemTheme`, which queries the real OS state — through native JNI — on every call).

**Verification of Automatic mode (2026-10-16)**: after the fix in decision 4, a manual check on macOS (Amazon Corretto) had reported that Automatic mode didn't reflect the OS theme, either at startup or live. The root cause was investigated when closing this change:

- On macOS, Skiko's native `SystemThemeHelper.getCurrentSystemTheme` reads `[[NSUserDefaults standardUserDefaults] stringForKey:@"AppleInterfaceStyle"]` and compares it with `"Dark"` on every call, so each poll reads the current OS value.
- A standalone JVM probe (Homebrew OpenJDK 27, macOS arm64, OS in dark mode) returned `DARK`, before and after AWT was initialized, matching `defaults read -g AppleInterfaceStyle`.
- The packaged app (`createDistributable`) launched in Automatic mode with an empty settings file showed the dark `colorScheme` with the OS in dark mode; launched with `LIGHT` stored, it showed the light `colorScheme` and the "Light" label.

The earlier report could not be reproduced with the current toolchain. Live switching wasn't re-tested, because that requires changing the operating system's appearance. Known limitations that remain: the macOS window title bar stays light (the JDK keeps the Aqua appearance unless told otherwise), and when macOS appearance is set to "Auto" the `AppleInterfaceStyle` key may not reflect the current appearance. Neither is in scope for this change.

## Goals / Non-Goals

**Goals:**
- Support three theme modes: Light, Dark and Automatic (follows the OS).
- Automatic mode reacts to operating system theme changes without restarting the app.
- Persist the mode chosen by the user across restarts, with Automatic as the default if no preference is stored.
- Keep the architectural pattern already used in the project: domain with ports (`domain/io`), JSON infrastructure adapter, manual wiring in `AppContainer`.

**Non-Goals:**
- No accent/color customization beyond light/dark (no palette picker).
- System theme detection on Linux isn't guaranteed if the desktop environment doesn't expose it; the manual mode (Light/Dark) still works as a fallback.
- No generic "settings"/preferences capability is introduced; this preference is persisted in its own small, dedicated file, not an extensible configuration mechanism for future use.

## Decisions

**1. `ThemeMode` as a domain model, not a UI detail.**
Add `domain/model/ThemeMode.kt` with an enum `ThemeMode { LIGHT, DARK, SYSTEM }`. It lives in the domain (not in `ui/theme`) because it is persisted and passed through a port, following the same criterion as `ProjectConfig`.

**2. `ThemePreferenceRepository` port + dedicated JSON adapter.**
New port in `domain/io/ThemePreferenceRepository.kt` with `fun load(): ThemeMode` and `fun save(mode: ThemeMode)`. The `infrastructure/JsonThemePreferenceRepository.kt` implementation persists to `~/.shogunai/settings.json` (its own file, not `projects.json`) with the same atomic write pattern as `JsonProjectRepository`. If the file doesn't exist or is corrupt/empty, `load()` returns `ThemeMode.SYSTEM`. (The `add-ui-localization` change later moved the file handling into a shared `JsonSettingsFile`, also used for the language preference.)
- Rejected alternative: storing the preference inside `projects.json` or in Java `Preferences` (`java.util.prefs`) — rejected because it breaks the JSON pattern already established in the project and couples a UI preference to the project repository.

**3. Theme mode state lives in `AppRoot`, not in a dedicated ViewModel.**
`AppRoot` loads the initial `ThemeMode` from `appContainer.themePreferenceRepository.load()` once (synchronously, since it's a small local file read), keeps it in `mutableStateOf`, and exposes an `onThemeModeChange` callback that updates the state and calls `save()`. A `ThemeViewModel` is rejected because the state is app-wide (not per screen) and the project has no state holder at that level; adding one just for three values would be over-engineering.

**4. `ShogunAiTheme` resolves the effective scheme internally, polling the real OS theme.**
`ShogunAiTheme(themeMode: ThemeMode, content: @Composable () -> Unit)` computes `val useDark = when (themeMode) { LIGHT -> false; DARK -> true; SYSTEM -> systemDark }`, where `systemDark` comes from `rememberSystemInDarkTheme()` (`ui/theme/SystemDarkTheme.kt`), not from `isSystemInDarkTheme()`. `rememberSystemInDarkTheme()` uses `produceState` with a `while (true) { value = currentSystemTheme == SystemTheme.DARK; delay(1000) }` loop over `org.jetbrains.skiko.currentSystemTheme` — a public property already present transitively through `compose-desktop` (no new Gradle dependency) that queries the OS through a native (JNI) call on every access, unlike the static `CompositionLocal` behind `isSystemInDarkTheme()`. Polling every second gives live updates without depending on AWT hooks specific to the JDK distribution (which do vary between JetBrains Runtime and others, e.g. Corretto). Scheme resolution stays encapsulated in `ShogunAiTheme` (not in `AppRoot`), just as it previously encapsulated the single fixed scheme.

**5. Light palette (`ShogunLightColorScheme`) as the counterpart of the dark one.**
Add a `ShogunLight*` set to `Color.kt` (background, surface, onSurface, etc.) keeping the same accent colors (`ShogunRed`, `ShogunSlate`) but with inverted backgrounds/text, checking ~4:1 contrast as was done for the dark scheme (see the contrast fixes in `app-icon-and-visual-style`, tasks 6.2–6.4).

**6. Mode control placed in `AppRoot`, visible on every screen.**
Instead of adding a theme control to each of the three screens (duplication), `AppRoot` puts its own header row (`Row` with `ThemeModeToggle` aligned to the end) above the existing `when`, inside a `Column` that gives the `when` container the remaining space with `Modifier.weight(1f)`. Overlaying the control as a floating overlay (`Box` + `Alignment.TopEnd`) was rejected because `ProjectListScreen` and `WorktreeScreen` already have their own button anchored in the top-right corner, and the overlay covered them. The dedicated header row avoids the collision without touching the individual screens. This keeps `AppRoot` as the single injection point for app-wide UI, consistent with how it already wraps everything in `ShogunAiTheme`.

## Risks / Trade-offs

- [Risk] OS theme detection may not work reliably on Linux depending on the desktop environment → Mitigation: Automatic mode falls back to whatever Skiko reports, and the user can always force Light/Dark manually.
- [Risk] Automatic mode had been reported as not working on macOS (Corretto) → Investigated when closing the change (see Context): detection and startup behavior were confirmed working with the current toolchain; live switching wasn't re-tested. Mitigation: Light/Dark can always be forced manually.
- [Risk] The light palette is new and hasn't gone through the same contrast iteration as the dark one (which needed adjustments after visual feedback) → Mitigation: apply the same contrast criterion (~4:1) when defining it, before closing it.
- [Risk] Adding the theme control inside `AppRoot` couples more UI logic to that file → Mitigation: extract the control to its own composable (`ThemeModeToggle`) in `ui/theme/`, so `AppRoot` only invokes it.
