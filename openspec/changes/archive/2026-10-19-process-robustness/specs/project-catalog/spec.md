# Spec Delta

## ADDED Requirements

### Requirement: Unreadable local files are kept and reported
When the projects file or the settings file can't be read, the system SHALL report it, naming the file, SHALL keep a copy of the unreadable file next to it before anything writes a new one, and SHALL NOT replace it silently. The desktop app SHALL show the problem with the file's path; the CLI SHALL fail as an unexpected failure.

#### Scenario: Corrupted projects file in the desktop app
- **WHEN** the app starts and `projects.json` isn't valid JSON
- **THEN** the app shows that the projects file can't be read, with its path, and saving a project first keeps the old file as `projects.json.unreadable-<timestamp>`

#### Scenario: Corrupted settings file
- **WHEN** `settings.json` isn't valid JSON
- **THEN** the app starts with default settings, says the settings were reset, and the old file is kept as `settings.json.unreadable-<timestamp>`

### Requirement: Concurrent writes don't lose changes
Writes to the projects file and the settings file SHALL be serialized between the desktop app and the CLI, each reading the latest contents under the same lock before changing them, so two writers never drop each other's change.

#### Scenario: Two settings changes at once
- **WHEN** the app saves the window bounds while it also saves the grouping of a view
- **THEN** both changes are in `settings.json` afterwards
