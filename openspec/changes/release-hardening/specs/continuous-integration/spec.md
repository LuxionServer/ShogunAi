# Spec Delta

## ADDED Requirements

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
