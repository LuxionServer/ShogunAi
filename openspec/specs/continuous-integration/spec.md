# continuous-integration Specification

## Purpose
Runs the automated test suite in CI on every branch and pull request that feeds a release, so regressions are caught at the change that introduced them.

## Requirements

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

### Requirement: Tests run on every target operating system
The test workflow SHALL run the full test suite on Linux, macOS and Windows for every run it already performs, and a failure on any of them SHALL fail the check.

#### Scenario: Windows-only failure
- **WHEN** a pull request to `develop` breaks a test only on Windows
- **THEN** the check fails and names the Windows job

### Requirement: A release builds only after its tests pass
The release workflow SHALL run the full test suite on the release commit, on every target operating system, before any installer is built or any release is created, whichever event started it.

#### Scenario: Release from a pushed tag
- **WHEN** a `vX.Y.Z` tag is pushed and a test fails on that commit
- **THEN** no installer is built and no release is created

#### Scenario: Release from a merged pull request
- **WHEN** the release pull request is merged and every test passes
- **THEN** the installers are built and published as before

### Requirement: Tests enforce coverage thresholds
The test workflow SHALL verify every module's coverage threshold after running the tests on its macOS job, the system the thresholds are measured on, and a module below its threshold SHALL fail the check.

#### Scenario: Pull request that lowers coverage
- **WHEN** a pull request to `develop` adds production code to `cli` that no test runs
- **THEN** the macOS job of the check fails, naming the coverage rule of `cli` that was violated

#### Scenario: Pull request that keeps coverage
- **WHEN** a pull request to `develop` passes every test and every module meets its threshold
- **THEN** the check succeeds
