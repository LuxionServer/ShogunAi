## ADDED Requirements

### Requirement: Close the Preferences window with the keyboard
Cmd/Ctrl+W SHALL close the Preferences window while it has focus, as its close button does, without affecting the main window.

#### Scenario: Close Preferences
- **WHEN** the Preferences window has focus and the user presses Cmd/Ctrl+W
- **THEN** the Preferences window closes and the main window stays open

#### Scenario: Other keys
- **WHEN** the Preferences window has focus and the user presses W without the command key
- **THEN** the window stays open
