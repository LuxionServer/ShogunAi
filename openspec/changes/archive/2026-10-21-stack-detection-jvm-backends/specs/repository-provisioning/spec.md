# Spec Delta

## ADDED Requirements

### Requirement: Declare the Maven provider
The system SHALL read `providers.maven` of `.shogunai.json` as an object with the optional field `mavenMaxHeap`, a memory size such as `1g` or `512m`. A non-object value or an invalid size SHALL make the file invalid with an error that names `.shogunai.json` and the field.

#### Scenario: Maven declared
- **WHEN** `.shogunai.json` contains `{ "providers": { "maven": { "mavenMaxHeap": "1g" } } }`
- **THEN** the repository declares the Maven provider with a heap of `1g`

#### Scenario: Maven without options
- **WHEN** `providers.maven` is `{}`
- **THEN** the repository declares the Maven provider without a heap

#### Scenario: Invalid heap
- **WHEN** `mavenMaxHeap` is `1 GB` or `1000`
- **THEN** the file is reported as invalid with an error that names the field

### Requirement: Declare the Docker Compose provider
The system SHALL read `providers.docker-compose` of `.shogunai.json` as an object with the optional field `portVariables`, a list of unique, valid and non-reserved variable names. Any other shape SHALL make the file invalid with an error that names `.shogunai.json` and the field.

#### Scenario: Compose declared
- **WHEN** `.shogunai.json` contains `{ "providers": { "docker-compose": { "portVariables": ["DB_PORT"] } } }`
- **THEN** the repository declares the Compose provider with the port variable `DB_PORT`

#### Scenario: Not a list
- **WHEN** `portVariables` is `"DB_PORT"`
- **THEN** the file is reported as invalid with an error that names the field

#### Scenario: Duplicate name
- **WHEN** `portVariables` is `["DB_PORT", "DB_PORT"]`
- **THEN** the file is reported as invalid with an error that names the variable

#### Scenario: Written back
- **WHEN** a repository declaring both new providers is saved from the settings
- **THEN** `.shogunai.json` keeps their options and every other field it had
