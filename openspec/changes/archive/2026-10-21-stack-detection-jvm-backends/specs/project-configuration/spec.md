# Spec Delta

## ADDED Requirements

### Requirement: Suggest the providers of the detected stacks
The "Repository configuration" section SHALL show, for the providers the platform supports and the repository doesn't declare, a suggestion for each stack detected in the repository, with the files that revealed it and an action to turn the provider on. Using it SHALL turn the switch on as an unsaved change. Nothing SHALL be written to the repository until the user saves and confirms the preview. Nothing is suggested when there is nothing to suggest.

#### Scenario: Suggestion shown
- **WHEN** the user opens the settings of a project whose repository has `pom.xml` and declares no provider
- **THEN** the section suggests the Maven provider and names `pom.xml` as the evidence

#### Scenario: Accept a suggestion
- **WHEN** the user uses the Maven suggestion
- **THEN** the Maven switch is on, the change counts as unsaved and `.shogunai.json` is unchanged until the user saves

#### Scenario: Nothing to suggest
- **WHEN** every detected stack already has its provider on
- **THEN** the section shows no suggestion

#### Scenario: Suggestion for another platform
- **WHEN** the repository has an Xcode project and the app runs on Windows
- **THEN** no iOS or Xcode suggestion is shown

### Requirement: Turn on the Maven and Docker Compose providers
The section SHALL offer a switch for the Maven and Docker Compose providers on every operating system. With Maven on it SHALL show the heap option, and with Docker Compose on it SHALL show the list of port variables, both following the rules of option editing: an empty option is omitted from the file and an invalid value is flagged and blocks saving.

#### Scenario: Maven heap
- **WHEN** the user turns on Maven, enters `1g` as heap and saves
- **THEN** `providers.maven` is `{ "mavenMaxHeap": "1g" }`

#### Scenario: Compose port variables
- **WHEN** the user turns on Docker Compose, adds `DB_PORT` and `API_PORT` and saves
- **THEN** `providers.docker-compose` is `{ "portVariables": ["DB_PORT", "API_PORT"] }`

#### Scenario: Invalid variable name
- **WHEN** the user adds `1DB` as a port variable
- **THEN** the field explains that names use letters, digits and `_`, not starting with a digit, and saving is blocked

#### Scenario: Turn a provider off
- **WHEN** the repository declares Maven, the user turns it off and saves
- **THEN** `.shogunai.json` no longer contains `providers.maven` and its other providers are kept
