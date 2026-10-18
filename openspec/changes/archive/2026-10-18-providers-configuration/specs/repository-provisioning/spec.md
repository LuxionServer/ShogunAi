# Spec Delta

## ADDED Requirements

### Requirement: Write providers and environment into the repository file
The system SHALL write the `providers`, `env` and `envFile` fields of `.shogunai.json` from the project settings, creating the file when it doesn't exist. Every other field, every `copy` pattern and the options of unknown providers SHALL be kept unchanged, and the file SHALL be replaced atomically.

#### Scenario: Keep other fields
- **WHEN** `.shogunai.json` contains `copy` patterns, an unknown field `"team": "mobile"` and `"providers": { "cocoapods": {} }`, and the user turns on Gradle and saves
- **THEN** the file keeps the patterns, `"team": "mobile"` and `providers.cocoapods`, and adds `providers.gradle`

#### Scenario: Create the file
- **WHEN** the repository has no `.shogunai.json` and the user adds an environment variable and saves
- **THEN** `.shogunai.json` is created with only the `env` field

#### Scenario: Write fails
- **WHEN** writing `.shogunai.json` fails
- **THEN** the app shows an error, keeps the settings open with the edits, and the project's own settings aren't saved either

### Requirement: Never overwrite an unreadable repository file
The system SHALL NOT write `.shogunai.json` when the existing file couldn't be read when the settings were opened, or when its contents changed on disk since then.

#### Scenario: Edited on disk meanwhile
- **WHEN** the user edits the repository configuration, another tool changes `.shogunai.json`, and the user saves
- **THEN** the app doesn't write the file, says it changed on disk, and offers to reload the section
