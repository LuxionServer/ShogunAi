## Purpose

Runs the automated test suite in CI on every branch and pull request that feeds a release, so regressions are caught at the change that introduced them.

## ADDED Requirements

### Requirement: Tests run on every pull request to develop
The system SHALL run the full test suite in CI for every pull request targeting `develop`, and report the result on the pull request.

#### Scenario: Pull request with passing tests
- **WHEN** a pull request targeting `develop` is opened or updated and every test passes
- **THEN** the CI check for that pull request succeeds

#### Scenario: Pull request with a failing test
- **WHEN** a pull request targeting `develop` is opened or updated and at least one test fails
- **THEN** the CI check for that pull request fails visibly

### Requirement: Tests run on pushes to integration and release branches
The system SHALL run the full test suite in CI for every push to `develop` and to any `release/**` branch.

#### Scenario: Push to develop
- **WHEN** a commit is pushed to `develop` (including a pull request merge)
- **THEN** CI runs the full test suite on that commit

#### Scenario: Push to a release branch
- **WHEN** a commit is pushed to a `release/**` branch
- **THEN** CI runs the full test suite on that commit before the release pull request is merged

### Requirement: Superseded runs are cancelled
The system SHALL cancel an in-progress test run when a newer commit is pushed to the same branch or pull request.

#### Scenario: Two quick pushes to the same pull request
- **WHEN** a second commit is pushed to a pull request while the test run for the first commit is still in progress
- **THEN** the first run is cancelled and only the run for the latest commit completes
