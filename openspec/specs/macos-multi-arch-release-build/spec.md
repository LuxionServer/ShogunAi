# macos-multi-arch-release-build Specification

## Purpose
Publishes native macOS installers for both Apple Silicon (arm64) and Intel (x86_64) Macs in every release, so users of either architecture can install the app.

## Requirements

### Requirement: Per-architecture macOS build
The system SHALL build and publish, in every release, one `.dmg` for Apple Silicon (arm64) and a separate `.dmg` for Intel Macs (x86_64), each running natively on its architecture without requiring Rosetta.

#### Scenario: Release with two macOS installers
- **WHEN** a new release is published
- **THEN** the release includes exactly two macOS `.dmg` assets, one for arm64 and one for x86_64, each with its architecture suffix (`arm64` or `x64`) in the file name

#### Scenario: Suffix matches the build architecture
- **WHEN** a macOS `.dmg` is built for a release
- **THEN** its architecture suffix is derived from the architecture of the machine that built it, so an Intel build can never be published with the `arm64` suffix or vice versa

#### Scenario: arm64 installer on an Apple Silicon Mac
- **WHEN** a user with an Apple Silicon Mac downloads and opens the `.dmg` with the `arm64` suffix
- **THEN** the app starts natively, without incompatible-architecture warnings

#### Scenario: x64 installer on an Intel Mac
- **WHEN** a user with an Intel Mac downloads and opens the `.dmg` with the `x64` suffix
- **THEN** the app starts natively, without needing Rosetta

#### Scenario: One macOS build fails
- **WHEN** either macOS build fails during a release
- **THEN** the release is not published (it stays in draft) and the failure is visible in CI
