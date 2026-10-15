# ui-localization Specification

## Purpose
Lets users run the desktop app in their preferred language: defines which UI languages are supported, the default, how the language is selected and persisted, and that all user-facing text follows it.

## Requirements

### Requirement: Supported UI languages
The app SHALL support English and Spanish as UI languages. Every user-facing text the app itself produces SHALL be available in both languages.

#### Scenario: All screens are translated to Spanish
- **WHEN** the selected language is Spanish and the user navigates the project list, project configuration and worktree screens
- **THEN** every title, label, button, placeholder, dialog, empty state and accessibility description is shown in Spanish

#### Scenario: All screens are translated to English
- **WHEN** the selected language is English and the user navigates the project list, project configuration and worktree screens
- **THEN** every title, label, button, placeholder, dialog, empty state and accessibility description is shown in English

### Requirement: English is the default language
When no language preference is stored, the app SHALL start in English, regardless of the operating system locale.

#### Scenario: First launch without stored preference
- **WHEN** the app starts and the settings file doesn't exist
- **THEN** the UI is shown in English

#### Scenario: Settings file without a language
- **WHEN** the app starts and the settings file only contains a theme mode (written by a previous version)
- **THEN** the UI is shown in English and the stored theme mode is still applied

#### Scenario: Corrupt settings file
- **WHEN** the app starts and the settings file can't be parsed
- **THEN** the UI is shown in English

### Requirement: Language selection from the UI
The app SHALL expose a language selector reachable from every screen, listing each supported language by its own name ("English", "Español"). Selecting a language SHALL update all visible text immediately, without restarting the app or losing the current screen state.

#### Scenario: User switches to Spanish
- **WHEN** the UI is in English and the user selects "Español" in the language selector
- **THEN** all visible text switches to Spanish immediately and the user stays on the same screen

### Requirement: Language preference persistence
The app SHALL remember the selected language across restarts. Saving the language SHALL NOT change the stored theme mode, and saving the theme mode SHALL NOT change the stored language.

#### Scenario: Restart after choosing Spanish
- **WHEN** the user selects Spanish and restarts the app
- **THEN** the app starts in Spanish

#### Scenario: Theme and language are stored independently
- **WHEN** the user selects Spanish and then changes the theme mode to Dark
- **THEN** after a restart the app starts in Spanish with the Dark theme

### Requirement: Localized operation feedback
Error and success messages shown after an operation SHALL be rendered in the selected language. Output produced by external tools (such as Git's error output) SHALL be shown verbatim, untranslated, as part of the localized message.

#### Scenario: Domain error in Spanish
- **WHEN** the selected language is Spanish and creating a worktree from a branch fails because the branch doesn't exist
- **THEN** the error message is shown in Spanish and names the missing branch

#### Scenario: Git failure keeps Git's output
- **WHEN** a Git command fails with error output
- **THEN** the message describing the failure is in the selected language and Git's own error output is included unchanged

#### Scenario: Success message follows the language
- **WHEN** the selected language is English and a worktree is created successfully
- **THEN** the success message is shown in English and includes the worktree path

#### Scenario: Language change re-renders a visible message
- **WHEN** an error message is visible and the user switches the language
- **THEN** the same error is shown in the newly selected language
