## MODIFIED Requirements

### Requirement: Preferences window
The app SHALL provide a Preferences window, opened from the menu or with Cmd/Ctrl+Comma, holding the theme mode, the language, and a "Tools" section with the terminal and the default IDE, each chosen among the ones detected on the machine. Changes SHALL apply immediately to the main window.

#### Scenario: Change the theme from Preferences
- **WHEN** the user opens Preferences and selects Dark
- **THEN** the main window and Preferences switch to the dark scheme immediately

#### Scenario: Choose a terminal
- **WHEN** Terminal and iTerm2 are installed and the user chooses iTerm2 in Preferences
- **THEN** the worktree actions read "Open in iTerm2" and open iTerm2

#### Scenario: Nothing detected
- **WHEN** no supported terminal is installed
- **THEN** the terminal choice says none was found and "Open in terminal" isn't offered
