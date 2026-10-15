## Why

Every user-facing text in the desktop UI is a hardcoded Spanish literal spread across the screens (`ProjectListScreen`, `ProjectConfigScreen`, `WorktreeScreen`, shared components, theme toggle), while domain error messages are hardcoded in English. The app can't be used comfortably by non-Spanish speakers, and the mix of languages in one screen (Spanish labels next to English error messages) looks unfinished. Making the UI translatable now, while the string set is still small (~70 texts), is far cheaper than retrofitting it later.

## What Changes

- Introduce a translation layer for all user-facing UI text, with two supported languages: **English (default)** and **Spanish**.
- Move every hardcoded UI literal (titles, labels, buttons, placeholders, dialogs, empty states, content descriptions, file chooser titles, theme mode labels) into per-language string tables.
- Localize domain errors shown in the UI: the worktree screen maps each `WorktreeError` variant to a translated message instead of displaying `Throwable.message` verbatim. Raw Git output embedded in an error is shown as-is (it comes from Git, not from the app).
- Localize the "worktree created" success message, which today is built inside `WorktreeViewModel` as a Spanish string.
- Add a language selector next to the existing theme mode selector, reachable from every screen. Changing it updates the whole UI immediately, without restarting.
- Persist the chosen language in `~/.shogunai/settings.json` alongside the theme mode. When no language is stored (first launch, existing users, missing/corrupt file), the app starts in English.
- **BREAKING** (UX only): existing users, who today see the app in Spanish, will see it in English after updating until they pick Spanish in the selector. No data migration is needed.

## Capabilities

### New Capabilities
- `ui-localization`: which languages the UI supports, the default language, how the user picks a language, that the choice applies immediately and is persisted, and that every user-facing text (including error and success feedback) follows the selected language.

### Modified Capabilities

None. Existing capabilities keep their behavior; only the language their texts are rendered in changes, which `ui-localization` owns.

## Impact

- **UI** (`desktopApp/.../ui/`): all screens and shared components read texts from the active string table instead of literals; `AppRoot` provides the active language and hosts the new language selector; `FilePicker` receives localized dialog titles.
- **ViewModels**: `WorktreeViewModel` exposes the failure (`Throwable`) and the created worktree path instead of preformatted message strings, so the UI can render them in the active language. `WorktreeViewModelTest` assertions are updated accordingly.
- **Domain**: new `AppLanguage` model and `LanguagePreferenceRepository` port, mirroring `ThemeMode` / `ThemePreferenceRepository`.
- **Infrastructure**: a shared `settings.json` file abstraction used by the theme adapter and a new language adapter, each preserving the other field on save.
- **Dependencies**: none added.
- **Docs**: `docs/architecture/ui.md` / `ui.en.md` and `infrastructure.md` / `infrastructure.en.md` document the localization layer and the extended settings file.
