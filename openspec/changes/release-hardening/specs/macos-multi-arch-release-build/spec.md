# Spec Delta

## ADDED Requirements

### Requirement: macOS installers carry the release version
Each macOS `.dmg` SHALL be named `ShogunAi-<release version>-<arch>.dmg`. The bundle version inside it SHALL be derived from the release version by one rule shared by the build and the release pipeline, accepted by macOS (first component at least 1) and strictly increasing with the release version.

#### Scenario: Pre-1.0 release
- **WHEN** version `0.9.0` is released
- **THEN** the assets are `ShogunAi-0.9.0-arm64.dmg` and `ShogunAi-0.9.0-x64.dmg`, and the app installs and upgrades over `0.8.0`

#### Scenario: First major releases
- **WHEN** versions `1.0.0` and then `2.0.0` are released
- **THEN** each release finds and publishes its own two `.dmg` files, and each installs over the previous version
