# Spec Delta

## MODIFIED Requirements

### Requirement: Refresh while shown
A machine view SHALL refresh its process and device states at the machine view interval of the refresh cadence while it is shown, and its disk sizes when it is opened and on View > Reload. Nothing SHALL be inspected for a resource whose view isn't shown, apart from the sidebar summaries, which refresh at the summary interval of the refresh cadence.

#### Scenario: Daemon finishes a build
- **WHEN** the profile is Balanced and a busy daemon becomes idle while the Gradle view is shown with the window focused
- **THEN** it is shown as idle within about 5 seconds and its stop action becomes enabled

#### Scenario: Light profile
- **WHEN** the profile is Light and the Gradle view is shown with the window focused for a minute
- **THEN** its daemons are read at most 5 times in that minute and the sidebar summaries at most once

#### Scenario: Window in the background
- **WHEN** the profile is Balanced and the Gradle view stays shown in a visible but unfocused window for a minute
- **THEN** its daemons are read at most 5 times in that minute
