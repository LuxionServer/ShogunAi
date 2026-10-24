# Spec Delta

## MODIFIED Requirements

### Requirement: First launch without projects
When no project is saved, the content area SHALL show an empty state that explains what a project is and offers to add one, and SHALL offer to take the ShogunAi tour next to it.

#### Scenario: No projects
- **WHEN** the app starts with no saved projects
- **THEN** the content area shows the empty state with an action that opens the add-project form and an action that starts the tour

### Requirement: Application menu
The app SHALL provide an application menu with File (Add project, and on Windows and Linux Preferences and Quit), View (Reload, Toggle sidebar) and Help (ShogunAi Tour, Check for updates, Copy diagnostic report, and on Windows and Linux About) entries, each with its keyboard shortcut when it has one. On macOS, Preferences and About SHALL be in the application menu named after the app.

#### Scenario: Reload from the menu
- **WHEN** the user chooses View > Reload or presses Cmd/Ctrl+R
- **THEN** the current view reloads its data

#### Scenario: macOS conventions
- **WHEN** the app runs on macOS
- **THEN** "About ShogunAi" and "Settings…" (Cmd+,) are in the ShogunAi menu

#### Scenario: Tour from the Help menu
- **WHEN** the user has projects and chooses Help > "ShogunAi Tour"
- **THEN** the tour starts
