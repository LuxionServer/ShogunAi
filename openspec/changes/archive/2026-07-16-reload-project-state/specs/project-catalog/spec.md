## ADDED Requirements

### Requirement: Reload the project list
The system SHALL let the user manually trigger a fresh read of the persisted project catalog via a visible "Reload" action on the project list screen, and SHALL automatically re-run this read whenever the OS window regains focus while the project list screen is active, so that projects added, edited, or removed outside the current app session are reflected without restarting the app.

#### Scenario: User manually reloads the project list
- **WHEN** the user activates the "Reload" action on the project list screen
- **THEN** the app re-reads the project catalog via `ProjectRepository.list()` and updates the displayed list to match its result

#### Scenario: Application window regains focus while the project list screen is active
- **WHEN** the project list screen is the active screen and the application window transitions from unfocused to focused (e.g. the user switches back from another app)
- **THEN** the app re-reads the project catalog via `ProjectRepository.list()` and updates the displayed list to match its result
