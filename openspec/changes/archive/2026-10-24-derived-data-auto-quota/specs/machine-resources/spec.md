# Spec Delta

## MODIFIED Requirements

### Requirement: Cancellable size calculation
DerivedData sizes SHALL be computed in the background, several folders at a time, and SHALL keep being computed when the user leaves the view so the sidebar summary can show the total. Reloading the view SHALL stop the running calculation and measure every folder read again, and the calculation SHALL stop when no saved project declares the Xcode provider anymore.

#### Scenario: Leave the view
- **WHEN** sizes are still being calculated and the user shows another view
- **THEN** the calculation goes on without blocking the app, and the sidebar summary shows the total once every size is known

#### Scenario: Reload
- **WHEN** the user reloads the DerivedData view while sizes are being calculated
- **THEN** the running calculation stops, the folders are read again and every folder is measured again, including those with a remembered size

#### Scenario: Provider no longer declared
- **WHEN** sizes are being calculated and the only project that declared the Xcode provider is removed
- **THEN** the calculation stops

### Requirement: DerivedData usage against the quota
Unless the user chose "No quota", the Xcode DerivedData view SHALL show the usage against the quota, say when the quota is automatic, mark it as exceeded when it is, and list the folders released by the last application of the quota with the space freed. The sidebar summary SHALL show the usage and the quota. With "No quota", the view and the summary SHALL show only the usage.

#### Scenario: Within the quota
- **WHEN** the quota is 50 GB and the usage is 32 GB
- **THEN** the view shows 32 GB of 50 GB and the sidebar summary shows the same figures

#### Scenario: Automatic quota
- **WHEN** the quota is Automatic and resolves to 40 GB with a usage of 32 GB
- **THEN** the view shows 32 GB of 40 GB and says the quota is automatic

#### Scenario: Exceeded by folders in use
- **WHEN** the usage is 60 GB over a 50 GB quota and the remaining folders belong to worktrees in use
- **THEN** the view marks the quota as exceeded and names those worktrees

#### Scenario: Released folders
- **WHEN** the quota released two folders on the last refresh
- **THEN** the view lists them with their worktree and the total space freed until the notice is dismissed

#### Scenario: Usage still being calculated
- **WHEN** the folder sizes are not yet known
- **THEN** the view shows a calculating state and nothing is released

## ADDED Requirements

### Requirement: Remembered sizes shown first
When the DerivedData view or the sidebar summary reads the folders, each folder with a remembered size SHALL show it at once, and the totals SHALL include it. On a reload, a folder being measured again SHALL keep showing its remembered size until the new one is known.

#### Scenario: App started again
- **WHEN** the app starts and 180 of 200 DerivedData folders have remembered sizes still valid
- **THEN** those 180 sizes are shown when the folders are listed and only the other 20 show a calculating state

#### Scenario: Reload keeps the previous figures
- **WHEN** the user reloads the view and a folder's remembered size is 8 GB
- **THEN** the folder shows 8 GB while it is measured again, and then its new size
