# Spec Delta

## ADDED Requirements

### Requirement: Pause while the window is hidden
While the main window is minimized, in the tray or the app is hidden, neither the shown machine view nor the sidebar summaries SHALL refresh, except for trimming Gradle daemons to the pool limit. Both SHALL refresh as soon as the window is shown again.

#### Scenario: Window minimized
- **WHEN** the Gradle daemons view is shown, a project declares the emulator provider and the user minimizes the window for a minute
- **THEN** no daemon, emulator or other machine state is read during that minute except to trim idle Gradle daemons beyond the limit, and the view and the summaries refresh when the window is shown again
