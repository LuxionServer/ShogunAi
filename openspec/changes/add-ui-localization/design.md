## Context

See `proposal.md` for motivation. Current state relevant to the approach:

- UI texts are string literals inside composables in `ui/projectlist`, `ui/projectconfig`, `ui/worktree`, `ui/components/SectionCard.kt`, `ui/theme/ThemeModeToggle.kt` and `ui/FilePicker.kt` (the latter in English).
- `WorktreeViewModel` stores `errorMessage: String?` (filled from `Throwable.message`, whose texts come from `WorktreeError` in English) and `successMessage: String?` (built as a Spanish literal).
- `WorktreeError` is a sealed hierarchy whose variants already carry structured data (`path`, `branch`, `files`, `exitCode`, `errorOutput`…). Its KDoc states the intent that the UI decides what message to show without inspecting strings.
- Theme mode persistence: `ThemePreferenceRepository` port + `JsonThemePreferenceRepository` writing `{"themeMode": ...}` to `~/.shogunai/settings.json` with an atomic temp-file move. `AppRoot` loads it into `mutableStateOf` and hosts `ThemeModeToggle` in a top bar shared by all screens.
- `compose.components.resources` is declared in the version catalog but not used by `desktopApp`.

## Goals / Non-Goals

**Goals:**
- Compile-time guarantee that every text exists in every supported language (a missing translation is a build error, not a runtime fallback).
- Switching language recomposes the UI in place, with no restart and no lost screen/ViewModel state.
- Keep the domain free of presentation/language concerns.
- No new Gradle dependencies.

**Non-Goals:**
- Following the OS locale automatically (the user asked for English as the default).
- Translating `docs/`, the window title ("ShogunAi", a brand name), Git output, file paths or the branch type prefixes (`feature`/`fix`, which are literal Git ref prefixes).
- Plural rules, RTL layouts or locale-aware date/number formatting (no such texts exist today).
- A generic, open-ended settings framework beyond the two existing preferences.

## Decisions

**1. Strings as a Kotlin interface with one implementation per language, exposed via a `CompositionLocal`.**
`ui/i18n/Strings.kt` declares `interface Strings` with one `val` per static text and one function per parameterized text (e.g. `fun deleteWorktreeConfirm(path: String): String`). `EnglishStrings` and `SpanishStrings` are `object`s implementing it. `LocalStrings = staticCompositionLocalOf<Strings> { EnglishStrings }` is provided in `AppRoot`; composables read `val strings = LocalStrings.current`.
- *Why*: adding a key to the interface forces every language to implement it (compile error otherwise); parameterized texts are type-safe functions instead of positional `%1$s` formats; trivially testable with plain unit tests; zero dependencies.
- *Alternative — Compose Multiplatform resources* (`strings.xml` + `values-es/`, generated `Res.string.*`): standard and translator-friendly, but on desktop the locale is resolved from `Locale.getDefault()`, so in-app switching requires mutating the JVM-global default locale and forcing recomposition through a workaround; missing translations silently fall back to the default at runtime; it adds a Gradle plugin/codegen step. With two languages and ~70 strings the XML tooling benefit doesn't outweigh these costs. If the set of languages grows or external translators get involved, migrating is mechanical (one key per interface member).
- *Alternative — `ResourceBundle` `.properties`*: same runtime-fallback problem, no type safety for arguments.
- `staticCompositionLocalOf` is appropriate because the value changes rarely (only on a language switch), and when it does the whole tree must recompose anyway.

**2. `AppLanguage` as a domain model with its own port, mirroring `ThemeMode`.**
`domain/model/AppLanguage.kt`: `@Serializable enum class AppLanguage { ENGLISH, SPANISH }`. `domain/io/LanguagePreferenceRepository.kt`: `load(): AppLanguage`, `save(language: AppLanguage)`. The UI maps `AppLanguage` → `Strings` (`AppLanguage.strings()` in `ui/i18n`), so the domain never references UI types.
- The language's display name ("English", "Español") lives on `Strings` implementations' own `languageName`, not on the enum, keeping the enum presentation-free; the selector shows each language with *its own* table's name so it's recognizable regardless of the active language.

**3. One settings adapter implementing both preference ports.**
`JsonThemePreferenceRepository` is replaced by `JsonSettingsRepository`, implementing `ThemePreferenceRepository` and `LanguagePreferenceRepository` over the same `settings.json`. The serialized `Settings(themeMode: ThemeMode = SYSTEM, language: AppLanguage = ENGLISH)` uses defaults so files written by the previous version (only `themeMode`) decode correctly, and the `Json` instance sets `ignoreUnknownKeys = true` for forward compatibility. Each `save` is read-modify-write: load the current `Settings`, `copy` the changed field, write atomically (same temp-file + `ATOMIC_MOVE` pattern).
- *Alternative — a second file (`language.json`)*: avoids the read-modify-write but scatters preferences; the requirement that theme and language are stored independently is equally satisfied by read-modify-write.
- *Alternative — two adapters writing the same file*: each would overwrite the other's field. Rejected.
- Unparseable file → both defaults (`SYSTEM`, `ENGLISH`), as today for theme.
- `AppContainer` exposes the single instance through both port-typed properties.

**4. ViewModels expose data, the UI formats messages.**
`WorktreeViewModel.errorMessage: String?` becomes `error: Throwable?`; `successMessage: String?` becomes `createdWorktreePath: String?` (with `dismissError()` / `dismissSuccess()` unchanged). `WorktreeScreen` renders them with `strings.worktreeError(error)` and `strings.worktreeCreated(path)`.
- `Strings.worktreeError(error: Throwable): String` is implemented once, in a shared extension in `ui/i18n`, that `when`s over the `WorktreeError` sealed hierarchy and delegates each variant to a parameterized `Strings` function (`branchNotFound(branch)`, `gitCommandFailed(exitCode, command, output)`…). Exhaustive `when` over a sealed class means a new `WorktreeError` variant without a translation fails to compile. Non-`WorktreeError` throwables (unexpected I/O failures) fall back to `strings.unexpectedError(error.message)`.
- Storing the `Throwable` instead of a string also makes a visible message re-render when the language changes (spec scenario "Language change re-renders a visible message").
- `WorktreeError` keeps its English `message` for logs/debugging; the UI no longer displays it.
- *Alternative — inject `Strings` into ViewModels*: ViewModels would need to be recreated or notified on language change, and tests would depend on a language. Rejected.

**5. Language selector next to the theme selector.**
`ui/i18n/LanguageToggle.kt`, a `TextButton` + `DropdownMenu` identical in shape to `ThemeModeToggle`, placed in `AppRoot`'s top bar before the theme toggle. `AppRoot` holds `language` in `mutableStateOf(languagePreferenceRepository.load())`, saves on change, and wraps the content in `CompositionLocalProvider(LocalStrings provides language.strings())`. Since the provider sits above the screen `when`, ViewModels (scoped by `viewModel(key = ...)`) and `remember`ed state survive the switch.

**6. Non-composable callers receive texts as parameters.**
`FilePicker.pickDirectory/pickFiles` (Swing `JFileChooser`, not composable) get a `dialogTitle: String` parameter supplied by the calling composable from `LocalStrings`.

## Risks / Trade-offs

- [Existing users suddenly see English] → Accepted by the requirement (English default); the selector is visible on every screen and the choice persists. Called out as a UX breaking change in the proposal.
- [Kotlin string tables are less convenient for non-developer translators than XML/properties] → Acceptable for two languages maintained by the developers; migration path to Compose resources noted in decision 1.
- [Read-modify-write in the settings adapter could lose an update under concurrent writes] → Writes only happen from the UI thread on user clicks; no concurrency in practice.
- [A `Strings` text accidentally left as a literal in a composable] → Task list includes a grep over `ui/` for remaining Spanish literals; reviewers check new composables use `LocalStrings`.
- [Older app versions reading a `settings.json` that now contains `language`] → They don't set `ignoreUnknownKeys`, so they'd fall back to `SYSTEM` theme. Only relevant on downgrade; accepted.

## Migration Plan

No data migration: old `settings.json` files decode with `language = ENGLISH`. Rollback is reverting the change; a newer `settings.json` read by the old version degrades to the default theme as noted above.
