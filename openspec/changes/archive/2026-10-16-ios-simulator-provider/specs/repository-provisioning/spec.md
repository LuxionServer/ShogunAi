# Spec Delta

## ADDED Requirements

### Requirement: Declare the iOS simulator provider
The system SHALL read `providers.ios-simulator` in `.shogunai.json` as an object with an optional string `device` (a device type name such as `iPhone 16`) and an optional string `runtime` (such as `iOS 18.5`). A non-object value or a non-string option SHALL make the file invalid.

#### Scenario: Provider with preferences
- **WHEN** `.shogunai.json` contains `{ "providers": { "ios-simulator": { "device": "iPhone 16", "runtime": "iOS 18.5" } } }`
- **THEN** the repository declares the iOS simulator provider with those preferences

#### Scenario: Provider without options
- **WHEN** `.shogunai.json` contains `{ "providers": { "ios-simulator": {} } }`
- **THEN** the repository declares the provider with no preferences

#### Scenario: Invalid declaration
- **WHEN** `providers.ios-simulator` is `true` or its `device` is `16`
- **THEN** the file is reported as invalid with an error that names `.shogunai.json`
