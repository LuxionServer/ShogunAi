## ADDED Requirements

### Requirement: Errors in secondary windows don't close the app
An unexpected error raised while composing, recomposing or running effects of the Preferences or About window SHALL close only that window, SHALL be recorded in the diagnostics log as an unhandled error, and SHALL be reported to the user with a message in the main window in the selected language. The main window and the rest of the app SHALL keep running.

#### Scenario: Error when opening Preferences
- **WHEN** the Preferences window throws while it is first composed
- **THEN** the Preferences window closes, the main window stays open, an unhandled-error entry is added to the diagnostics log and the main window shows a message saying the window closed because of an error

#### Scenario: Error after the window is open
- **WHEN** the Preferences window is open and its content throws during a later recomposition or in a background effect
- **THEN** the same happens: only Preferences closes, the error is recorded and the main window shows the message

#### Scenario: Error in About
- **WHEN** the About window throws
- **THEN** only the About window closes, the error is recorded and the main window shows the message

#### Scenario: Reopening after an error
- **WHEN** a secondary window closed because of an error and the user opens it again
- **THEN** the window opens normally and is isolated in the same way
