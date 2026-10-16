# Spec Delta

## ADDED Requirements

### Requirement: Declare providers in the repository
The system SHALL read the optional `providers` field of `.shogunai.json` as an object keyed by provider id, whose values are objects with that provider's options. A repository declares the Gradle provider with `"providers": { "gradle": {} }`. Unknown provider ids SHALL be ignored, and a repository without the field declares no provider.

#### Scenario: Gradle provider declared
- **WHEN** `.shogunai.json` contains `{ "providers": { "gradle": {} } }`
- **THEN** the repository declares the Gradle provider

#### Scenario: No providers
- **WHEN** `.shogunai.json` has no `providers` field, or the repository has no `.shogunai.json`
- **THEN** the repository declares no provider and everything else works as before

#### Scenario: Unknown provider
- **WHEN** `providers` contains `{ "gradle": {}, "xcode": {} }`
- **THEN** the repository declares the Gradle provider and `xcode` is ignored

#### Scenario: Providers kept when writing
- **WHEN** the user moves a local file to `.shogunai.json` in a repository whose file declares providers
- **THEN** the `providers` field is kept unchanged

### Requirement: Invalid provider declaration
The system SHALL treat `.shogunai.json` as invalid, with the same error and effects as an invalid `copy` field, when `providers` isn't an object or a known provider's value isn't an object.

#### Scenario: Providers is not an object
- **WHEN** `.shogunai.json` contains `{ "providers": ["gradle"] }` and the user creates a worktree
- **THEN** creation fails with an error that names `.shogunai.json` and no worktree or branch is created

#### Scenario: Known provider with a wrong value
- **WHEN** `.shogunai.json` contains `{ "providers": { "gradle": true } }`
- **THEN** the file is reported as invalid with the same error
