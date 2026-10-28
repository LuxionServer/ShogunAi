# Spec Delta

## ADDED Requirements

### Requirement: Swift packages of each DerivedData folder
When a DerivedData folder is measured, the system SHALL also measure its `SourcePackages` subfolder, where Xcode keeps the Swift packages resolved for that project, and report it as the folder's packages size, part of the folder's size. A folder without `SourcePackages` SHALL have a packages size of zero.

#### Scenario: Worktree with packages
- **WHEN** a worktree's DerivedData folder is 6 GB and its `SourcePackages` is 800 MB
- **THEN** the folder is reported at 6 GB, of which 800 MB are packages

#### Scenario: Project without packages
- **WHEN** a folder has no `SourcePackages`
- **THEN** its packages size is zero

### Requirement: Remember the packages size
The packages size SHALL be remembered with the folder's size and used under the same rule. A remembered size without a packages size, from an earlier version, SHALL count as unknown and the folder SHALL be measured again.

#### Scenario: Unchanged folder
- **WHEN** a folder was measured at 6 GB with 800 MB of packages and Xcode hasn't used its project since
- **THEN** both sizes are taken from what was remembered

#### Scenario: Size remembered by an earlier version
- **WHEN** a folder's remembered entry has a size but no packages size
- **THEN** the folder is measured again

### Requirement: Show the packages size
`shogun xcode` SHALL show each worktree folder's packages size next to its size, and its JSON SHALL include `sourcePackagesBytes` for every folder. The desktop app's DerivedData view SHALL show it next to each worktree's size.

#### Scenario: Command output
- **WHEN** a worktree's folder is 6 GB with 800 MB of packages and the user runs `shogun xcode`
- **THEN** its line shows both sizes

#### Scenario: Packages size unknown
- **WHEN** a folder hasn't been measured yet in the desktop app
- **THEN** no packages size is shown for it

### Requirement: Shared package caches shown apart
The system SHALL show the size of SwiftPM's shared cache (`~/Library/Caches/org.swift.swiftpm`) and of CocoaPods' (`~/Library/Caches/CocoaPods`), each only when it exists, in `shogun xcode` (`swiftPmCacheBytes` and `cocoaPodsCacheBytes` in JSON) and in the DerivedData view. They SHALL NOT count against the quota and SHALL NOT be deleted by any action.

#### Scenario: Both caches present
- **WHEN** the SwiftPM cache is 2 GB and the CocoaPods cache is 3 GB
- **THEN** both sizes are shown and the DerivedData usage is unchanged

#### Scenario: CocoaPods not used
- **WHEN** `~/Library/Caches/CocoaPods` doesn't exist
- **THEN** no CocoaPods cache is shown and the JSON has `cocoaPodsCacheBytes` null

#### Scenario: Quota exceeded
- **WHEN** the quota is exceeded and the shared package caches are large
- **THEN** only DerivedData folders are released and the caches are kept
