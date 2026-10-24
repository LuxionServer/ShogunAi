# Spec Delta

## MODIFIED Requirements

### Requirement: DerivedData disk quota as a machine setting
The machine settings SHALL include a DerivedData disk quota in one of three modes: Automatic, a fixed positive whole number of gigabytes, or no quota, with Automatic as the default. It SHALL be independent of the capacity profile, applied by the desktop app and the `shogun` command alike, and SHALL NOT be read from `.shogunai.json`. Changing it SHALL NOT delete anything by itself.

#### Scenario: Default
- **WHEN** the app settings have never stored a quota
- **THEN** the DerivedData quota is Automatic

#### Scenario: Settings from before the automatic quota
- **WHEN** the stored settings have no quota mode and a quota of 50 GB
- **THEN** the quota is fixed at 50 GB

#### Scenario: Settings from before the automatic quota without a quota
- **WHEN** the stored settings have no quota mode and no quota
- **THEN** the quota is Automatic

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
Preferences > Resources SHALL offer the DerivedData quota as a choice between Automatic, a fixed size with a gigabytes field, and "No quota", on macOS only, and SHALL save the change immediately. Automatic SHALL explain how its size is computed. The labels SHALL be available in English and Spanish.

#### Scenario: Set a quota
- **WHEN** the user chooses a fixed size and enters 40
- **THEN** the quota 40 GB is stored and shown the next time Preferences opens

#### Scenario: Back to Automatic
- **WHEN** the quota is fixed at 40 GB and the user chooses Automatic
- **THEN** the quota is Automatic, and choosing a fixed size again offers 40 GB

#### Scenario: Choose no quota
- **WHEN** the user chooses "No quota"
- **THEN** no DerivedData quota is applied from then on

#### Scenario: Not macOS
- **WHEN** the app runs on Linux or Windows
- **THEN** Preferences does not show the quota

## ADDED Requirements

### Requirement: Automatic DerivedData quota from the disk
The Automatic quota SHALL be a quarter of the space DerivedData can use, which is the free space on the volume of the DerivedData location plus the current DerivedData usage, kept between 20 GB and 200 GB. It SHALL be computed each time the quota is applied, once the folder sizes are known. When the free space can't be read, no folder SHALL be released and the quota SHALL be reported as unknown.

#### Scenario: Typical disk
- **WHEN** the volume has 100 GB free and DerivedData uses 60 GB
- **THEN** the Automatic quota is 40 GB

#### Scenario: Nearly full disk
- **WHEN** the volume has 10 GB free and DerivedData uses 30 GB
- **THEN** the Automatic quota is 20 GB, the minimum

#### Scenario: Large free disk
- **WHEN** the volume has 2 TB free
- **THEN** the Automatic quota is 200 GB, the maximum

#### Scenario: Releasing doesn't move the quota
- **WHEN** folders are released while applying an Automatic quota of 40 GB
- **THEN** the quota stays 40 GB during that application, since the space freed moves from usage to free space

#### Scenario: Free space unknown
- **WHEN** the free space of the DerivedData volume can't be read
- **THEN** nothing is released and the quota is shown as unknown
