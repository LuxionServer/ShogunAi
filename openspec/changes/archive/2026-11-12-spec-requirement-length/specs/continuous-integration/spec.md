## ADDED Requirements

### Requirement: Specs validated in strict mode on pull requests
The system SHALL run `openspec validate --specs --strict` in CI, with the OpenSpec version used locally, for every pull request that changes `openspec/`, whatever its base branch, and report the result on the pull request. Any warning, such as a requirement whose text is longer than 500 characters, SHALL fail the check.

#### Scenario: Pull request with an overlong requirement
- **WHEN** a pull request archives a change that leaves a requirement of `openspec/specs/` with more than 500 characters of text
- **THEN** the documentation check fails and its log names the spec and the requirement

#### Scenario: Pull request with valid specs
- **WHEN** a pull request changes `openspec/` and every spec passes strict validation
- **THEN** the check succeeds

#### Scenario: Pull request that touches no specs or docs
- **WHEN** a pull request changes only Kotlin sources
- **THEN** the documentation check does not run
