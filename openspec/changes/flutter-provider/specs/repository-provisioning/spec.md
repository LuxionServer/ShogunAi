# Spec Delta

## ADDED Requirements

### Requirement: Declare the Flutter provider
The system SHALL read `providers.flutter` in `.shogunai.json` as an object with no options; unknown options inside it SHALL be ignored and kept when the file is written. A non-object value SHALL make the file invalid.

#### Scenario: Provider declared
- **WHEN** `.shogunai.json` contains `{ "providers": { "flutter": {} } }`
- **THEN** the repository declares the Flutter provider

#### Scenario: Invalid declaration
- **WHEN** `providers.flutter` is `true`
- **THEN** the file is reported as invalid with an error that names `.shogunai.json`
