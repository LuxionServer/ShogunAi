# Spec Delta

## MODIFIED Requirements

### Requirement: Declare the Xcode provider
The system SHALL read `providers.xcode` in `.shogunai.json` as an object with the optional booleans `compilationCache` and `cocoaPods`, each false when absent. A non-object value or a non-boolean `compilationCache` or `cocoaPods` SHALL make the file invalid.

#### Scenario: Provider with compilation caching
- **WHEN** `.shogunai.json` contains `{ "providers": { "xcode": { "compilationCache": true } } }`
- **THEN** the repository declares the Xcode provider with compilation caching

#### Scenario: Provider with CocoaPods
- **WHEN** `.shogunai.json` contains `{ "providers": { "xcode": { "cocoaPods": true } } }`
- **THEN** the repository declares the Xcode provider with CocoaPods and without compilation caching

#### Scenario: Provider without options
- **WHEN** `.shogunai.json` contains `{ "providers": { "xcode": {} } }`
- **THEN** the repository declares the Xcode provider without compilation caching or CocoaPods

#### Scenario: Invalid declaration
- **WHEN** `providers.xcode` is `true`, its `compilationCache` is `"yes"` or its `cocoaPods` is `1`
- **THEN** the file is reported as invalid with an error that names `.shogunai.json`
