# Spec Delta

## ADDED Requirements

### Requirement: DerivedData disk quota as a machine setting
The machine settings SHALL include an optional DerivedData disk quota, a positive whole number of gigabytes or no quota, with no quota as the default. It SHALL be independent of the capacity profile, applied by the desktop app and the `shogun` command alike, and SHALL NOT be read from `.shogunai.json`. Changing it SHALL NOT delete anything by itself.

#### Scenario: Default
- **WHEN** the app settings have never stored a quota
- **THEN** there is no DerivedData quota

#### Scenario: Profile change keeps the quota
- **WHEN** the quota is 50 GB and the user switches the capacity profile from Balanced to Light
- **THEN** the quota remains 50 GB

#### Scenario: Repository can't set the quota
- **WHEN** `.shogunai.json` contains a field trying to set a DerivedData quota
- **THEN** the quota is not changed

#### Scenario: Invalid value
- **WHEN** the user types `0`, a negative number or text as the quota
- **THEN** the value is rejected and the stored quota is unchanged

### Requirement: Edit the quota in Preferences
Preferences > Resources SHALL offer the DerivedData quota with a gigabytes field and a "No quota" option, on macOS only, and SHALL save the change immediately. The labels SHALL be available in English and Spanish.

#### Scenario: Set a quota
- **WHEN** the user turns off "No quota" and enters 40
- **THEN** the quota 40 GB is stored and shown the next time Preferences opens

#### Scenario: Not macOS
- **WHEN** the app runs on Linux or Windows
- **THEN** Preferences does not show the quota
