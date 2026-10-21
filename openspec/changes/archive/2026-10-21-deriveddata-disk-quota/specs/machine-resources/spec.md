# Spec Delta

## ADDED Requirements

### Requirement: DerivedData usage against the quota
With a quota set, the Xcode DerivedData view SHALL show the usage against the quota, mark it as exceeded when it is, and list the folders released by the last application of the quota with the space freed. The sidebar summary SHALL show the usage and the quota. Without a quota, the view and the summary SHALL be unchanged.

#### Scenario: Within the quota
- **WHEN** the quota is 50 GB and the usage is 32 GB
- **THEN** the view shows 32 GB of 50 GB and the sidebar summary shows the same figures

#### Scenario: Exceeded by folders in use
- **WHEN** the usage is 60 GB over a 50 GB quota and the remaining folders belong to worktrees in use
- **THEN** the view marks the quota as exceeded and names those worktrees

#### Scenario: Released folders
- **WHEN** the quota released two folders on the last refresh
- **THEN** the view lists them with their worktree and the total space freed until the notice is dismissed

#### Scenario: Usage still being calculated
- **WHEN** the folder sizes are not yet known
- **THEN** the view shows a calculating state and nothing is released
