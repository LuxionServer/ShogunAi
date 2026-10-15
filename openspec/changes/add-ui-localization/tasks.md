## 1. Domain: language model and preference port

- [ ] 1.1 Create `domain/model/AppLanguage.kt` (`@Serializable enum class AppLanguage { ENGLISH, SPANISH }`) and `domain/io/LanguagePreferenceRepository.kt` (`load(): AppLanguage`, `save(language: AppLanguage)`); verify `./gradlew :desktopApp:compileKotlin` succeeds

## 2. Infrastructure: shared settings file

- [ ] 2.1 Extract `JsonSettingsFile` (shared `~/.shogunai/settings.json`; defaults `SYSTEM` / `ENGLISH`, `ignoreUnknownKeys`, read-modify-write with atomic move); make `JsonThemePreferenceRepository` and a new `JsonLanguagePreferenceRepository` use it; wire one shared instance in `AppContainer`
- [ ] 2.2 Add `JsonSettingsFileTest` covering: missing file → defaults; legacy file with only `themeMode` → stored theme + `ENGLISH`; corrupt file → defaults; saving language keeps theme and vice versa; round-trip of both values. Verify with `./gradlew :desktopApp:test`
- [ ] 2.3 Document the extended `settings.json` in `docs/architecture/infrastructure.md` and `infrastructure.en.md`

## 3. Translation layer

- [ ] 3.1 Create `ui/i18n/Strings.kt` (`interface Strings`, `LocalStrings`, `AppLanguage.strings()`), `ui/i18n/EnglishStrings.kt` and `ui/i18n/SpanishStrings.kt` with every UI text currently hardcoded in `ui/` (Spanish table preserves today's wording); verify the module compiles
- [ ] 3.2 Add `Strings.worktreeError(error: Throwable)` in `ui/i18n` with an exhaustive `when` over `WorktreeError` plus a fallback for other throwables
- [ ] 3.3 Add `StringsTest` verifying, for both languages: each `WorktreeError` variant renders a non-blank message containing its key data (path/branch/files), `GitCommandFailed` includes the raw Git output verbatim, and an unknown throwable uses the fallback. Verify with `./gradlew :desktopApp:test`

## 4. ViewModel: expose data instead of preformatted messages

- [ ] 4.1 In `WorktreeViewModel`, replace `errorMessage: String?` with `error: Throwable?` and `successMessage: String?` with `createdWorktreePath: String?`
- [ ] 4.2 Update `WorktreeViewModelTest` to assert on the error type/data (`assertIs<WorktreeError.BranchNotFound>`…) and on `createdWorktreePath`; verify with `./gradlew :desktopApp:test`

## 5. UI: use the translation layer

- [ ] 5.1 `AppRoot`: load the language from `languagePreferenceRepository`, hold it in state, provide `LocalStrings`, and add `LanguageToggle` (new, in `ui/i18n`) next to `ThemeModeToggle`; save on change
- [ ] 5.2 Replace literals with `LocalStrings.current` in `ProjectListScreen`, `ProjectConfigScreen`, `WorktreeScreen` (including error/success rendering via `worktreeError` / `worktreeCreated`), `SectionCard` and `ThemeModeToggle`
- [ ] 5.3 Add a `dialogTitle` parameter to `FilePicker.pickDirectory` / `pickFiles` and pass localized titles from `ProjectConfigScreen`
- [ ] 5.4 Grep `desktopApp/src/main/kotlin/.../ui` for remaining hardcoded user-facing literals (e.g. `Text("`, `contentDescription = "`, `label = "`) and confirm only non-translatable ones remain (brand, `A-Z`/`Z-A`, branch prefixes)
- [ ] 5.5 Document the localization layer (`Strings`, `LocalStrings`, language selector, how to add a text/language) in `docs/architecture/ui.md` and `ui.en.md`

## 6. Integration checks

- [ ] 6.1 Run `./gradlew test` and confirm all tests pass
- [ ] 6.2 Manual: launch with `./gradlew :desktopApp:run` without a `language` in `settings.json` → UI in English; switch to Español → all three screens in Spanish without leaving the current screen; restart → still Spanish with the theme unchanged

## Workflow follow-up

- Archive the change with `/opsx:archive add-ui-localization` once reviewed and manually verified.
