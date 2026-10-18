# Spec Delta

## MODIFIED Requirements

### Requirement: Supported UI languages
The app SHALL support English and Spanish as UI languages. Every user-facing text the app itself produces SHALL be available in both languages.

#### Scenario: All screens are translated to Spanish
- **WHEN** the selected language is Spanish and the user goes through every view, window, menu and dialog of the app
- **THEN** every title, label, button, menu entry, placeholder, dialog, empty state, tooltip and accessibility description is shown in Spanish

#### Scenario: All screens are translated to English
- **WHEN** the selected language is English and the user goes through every view, window, menu and dialog of the app
- **THEN** every title, label, button, menu entry, placeholder, dialog, empty state, tooltip and accessibility description is shown in English

### Requirement: Language selection from the UI
The app SHALL expose a language selector in the Preferences window, listing each supported language by its own name ("English", "Español"). Selecting a language SHALL update all visible text immediately, including the application menu, without restarting the app or losing the current view's state.

#### Scenario: User switches to Spanish
- **WHEN** the UI is in English and the user selects "Español" in Preferences
- **THEN** all visible text, including the application menu, switches to Spanish immediately and the main window stays on the same view
