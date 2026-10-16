# Spec Delta

## ADDED Requirements

### Requirement: Declare the emulator provider
The system SHALL read `providers.emulator` in `.shogunai.json` as an object with an optional string `avd`, the AVD the repository prefers. A non-object value or a non-string `avd` SHALL make the file invalid.

#### Scenario: Provider with a preferred AVD
- **WHEN** `.shogunai.json` contains `{ "providers": { "emulator": { "avd": "Pixel_8_API_35" } } }`
- **THEN** the repository declares the emulator provider preferring `Pixel_8_API_35`

#### Scenario: Provider without options
- **WHEN** `.shogunai.json` contains `{ "providers": { "emulator": {} } }`
- **THEN** the repository declares the emulator provider with no preferred AVD

#### Scenario: Invalid declaration
- **WHEN** `providers.emulator` is `true` or its `avd` is `35`
- **THEN** the file is reported as invalid with an error that names `.shogunai.json`
