# Spec Delta

## ADDED Requirements

### Requirement: The approval covers installing Node dependencies
A repository that declares the `node` provider SHALL need an approval even with no commands nor variables, and its approval SHALL cover installing its dependencies. Wherever the commands to approve are shown (the desktop dialog and project settings, the `shogun new` and `shogun provision` question, `shogun trust`), the system SHALL also say Node dependencies will be installed. `shogun trust --json` SHALL include `nodeInstall`.

#### Scenario: Only the provider declared
- **WHEN** a repository declares `{ "providers": { "node": {} } }` and no commands nor variables, and the user runs `shogun trust`
- **THEN** it says Node dependencies will be installed, says it isn't approved and asks to approve it

#### Scenario: Provider added by a pull
- **WHEN** the user approved a repository's commands `[["npm", "run", "build"]]` and a pull declares the `node` provider
- **THEN** the repository counts as not approved until the user approves it again

#### Scenario: Approvals without the provider are kept
- **WHEN** the user approved a repository's commands with an earlier version and the repository doesn't declare `node`
- **THEN** the commands still count as approved

#### Scenario: JSON
- **WHEN** the user runs `shogun trust --json` in a repository that declares `node`
- **THEN** the object has `nodeInstall: true`
