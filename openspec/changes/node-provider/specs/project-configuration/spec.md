# Spec Delta

## ADDED Requirements

### Requirement: Turn on the Node provider
The "Repository configuration" section SHALL offer a switch for the Node provider on every operating system, with no options, and the Node stack's suggestion SHALL turn it on as an unsaved change.

#### Scenario: Turn Node on
- **WHEN** the user turns on Node and saves
- **THEN** `.shogunai.json` declares `"providers": { "node": {} }` and its other providers are kept

#### Scenario: Accept the suggestion
- **WHEN** the repository's main folder has `package.json` and the user uses the Node suggestion
- **THEN** the Node switch is on, the change counts as unsaved and `.shogunai.json` is unchanged until the user saves

#### Scenario: Turn Node off
- **WHEN** the repository declares Node, the user turns it off and saves
- **THEN** `.shogunai.json` no longer contains `providers.node` and its other providers are kept
