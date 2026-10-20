## MODIFIED Requirements

### Requirement: Machine section in the sidebar
The sidebar SHALL show a "Machine" section with an entry for each resource whose provider is declared by at least one saved project: Gradle daemons, Android emulators, and, on macOS, iOS simulators and Xcode DerivedData. Each entry SHALL show a summary: daemon count and memory, running emulators, booted simulators, DerivedData total size.

#### Scenario: Only Gradle declared
- **WHEN** one saved project declares the Gradle provider and none declares another provider
- **THEN** the "Machine" section only shows "Gradle daemons", with the number of daemons and their memory

#### Scenario: No provider declared
- **WHEN** no saved project declares any provider
- **THEN** the sidebar shows no "Machine" section

#### Scenario: DerivedData size still being calculated
- **WHEN** a saved project declares the Xcode provider and the DerivedData folders are known but their sizes aren't yet
- **THEN** the DerivedData entry shows the number of folders and a calculating state instead of a size, and the app stays responsive

## ADDED Requirements

### Requirement: DerivedData listed before its sizes
The DerivedData view SHALL show its folders, their owners and the orphans as soon as the folders are read, without waiting for any size. Each folder whose size isn't known yet, the totals that include it and the compilation cache size SHALL show a calculating state, replaced by the size once computed. The app SHALL stay responsive while sizes are computed.

#### Scenario: Many large folders
- **WHEN** the user opens the DerivedData view and the location holds 200 folders taking 150 GB
- **THEN** the 200 folders are listed with their owners before any size is known, each size and total reads as calculating, and the sizes appear as they are computed while the rest of the app keeps responding

#### Scenario: Delete orphans before sizes are known
- **WHEN** the orphans are listed but their sizes are still being calculated and the user activates "Delete orphans"
- **THEN** the confirmation names the number of orphans, and their total size once it's known

### Requirement: Cancellable size calculation
DerivedData sizes SHALL be computed in the background, one folder at a time, and SHALL keep being computed when the user leaves the view so the sidebar summary can show the total. Reloading the view SHALL stop the running calculation and start it over from the folders read again, and the calculation SHALL stop when no saved project declares the Xcode provider anymore.

#### Scenario: Leave the view
- **WHEN** sizes are still being calculated and the user shows another view
- **THEN** the calculation goes on without blocking the app, and the sidebar summary shows the total once every size is known

#### Scenario: Reload
- **WHEN** the user reloads the DerivedData view while sizes are being calculated
- **THEN** the running calculation stops, the folders are read again and their sizes are calculated from scratch

#### Scenario: Provider no longer declared
- **WHEN** sizes are being calculated and the only project that declared the Xcode provider is removed
- **THEN** the calculation stops

### Requirement: Orphans found without measuring every folder
Looking for orphaned DerivedData in a project's worktree view and before a cleanup SHALL measure only the orphaned folders, not every folder in the DerivedData location.

#### Scenario: Cleanup dialog
- **WHEN** the user opens the cleanup dialog of an Xcode project and the location holds 200 folders of which 2 are orphaned
- **THEN** only the 2 orphaned folders are measured to show their total
