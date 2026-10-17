## MODIFIED Requirements

### Requirement: Declare providers in the repository
The system SHALL read the optional `providers` field of `.shogunai.json` as an object keyed by provider id, whose values are objects with that provider's options. A repository declares the Gradle provider with `"providers": { "gradle": {} }`. Unknown provider ids SHALL be ignored, and a repository without the field declares no provider.

#### Scenario: Gradle provider declared
- **WHEN** `.shogunai.json` contains `{ "providers": { "gradle": {} } }`
- **THEN** the repository declares the Gradle provider

#### Scenario: No providers
- **WHEN** `.shogunai.json` has no `providers` field, or the repository has no `.shogunai.json`
- **THEN** the repository declares no provider and everything else works as before

#### Scenario: Unknown provider
- **WHEN** `providers` contains `{ "gradle": {}, "cocoapods": {} }`
- **THEN** the repository declares the Gradle provider and `cocoapods` is ignored

#### Scenario: Providers kept when writing
- **WHEN** the user moves a local file to `.shogunai.json` in a repository whose file declares providers
- **THEN** the `providers` field is kept unchanged

## ADDED Requirements

### Requirement: Declare the Xcode provider
The system SHALL read `providers.xcode` in `.shogunai.json` as an object with an optional boolean `compilationCache`, false when absent. A non-object value or a non-boolean `compilationCache` SHALL make the file invalid.

#### Scenario: Provider with compilation caching
- **WHEN** `.shogunai.json` contains `{ "providers": { "xcode": { "compilationCache": true } } }`
- **THEN** the repository declares the Xcode provider with compilation caching

#### Scenario: Provider without options
- **WHEN** `.shogunai.json` contains `{ "providers": { "xcode": {} } }`
- **THEN** the repository declares the Xcode provider without compilation caching

#### Scenario: Invalid declaration
- **WHEN** `providers.xcode` is `true` or its `compilationCache` is `"yes"`
- **THEN** the file is reported as invalid with an error that names `.shogunai.json`
