# Spec Delta

## Purpose

Defines what every published release guarantees: a validated version, verifiable assets and a pipeline that holds no more privileges than each step needs.

## ADDED Requirements

### Requirement: Release version is validated before building
The release pipeline SHALL take the version from the `release/X.Y.Z` branch or the `vX.Y.Z` tag, SHALL fail before building anything when it doesn't match three dot-separated non-negative integers, and SHALL pass it to every script as data instead of interpolating it into script text.

#### Scenario: Valid version
- **WHEN** the release pull request from `release/1.4.0` is merged
- **THEN** the pipeline builds and publishes version `1.4.0`

#### Scenario: Invalid version
- **WHEN** a branch `release/1.4` or `release/1.4.0-rc1` triggers the pipeline
- **THEN** it fails in its first job with a message naming the expected format, and no release, tag or asset is created

### Requirement: Release assets have published checksums
Every published release SHALL include a `SHA256SUMS` file listing the SHA-256 checksum of each installer and archive it publishes, and the package manifests updated by the release SHALL use those same checksums.

#### Scenario: Verify a download
- **WHEN** a user downloads `ShogunAi-1.4.0-arm64.dmg` and `SHA256SUMS` from the release
- **THEN** `shasum -a 256 -c SHA256SUMS --ignore-missing` reports the `.dmg` as OK

#### Scenario: Manifests match
- **WHEN** the release updates the Homebrew cask, the Scoop manifest and the winget package
- **THEN** each checksum they declare equals the one in `SHA256SUMS`

### Requirement: Release jobs hold only the privileges they need
The release workflow SHALL grant no repository write permission by default, SHALL grant `contents: write` only to the jobs that create or publish the release, SHALL NOT persist checkout credentials in jobs that don't push, and SHALL reference every action outside `actions/*` and `gradle/*` by a full commit SHA.

#### Scenario: Build job
- **WHEN** the job that runs Gradle to package an installer runs
- **THEN** its token can't write to the repository and its checkout left no credentials in the Git configuration

#### Scenario: Third-party action
- **WHEN** the workflow uses an action that receives a secret, such as the winget publisher
- **THEN** it is referenced by a 40-character commit SHA
