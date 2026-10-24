# Spec Delta

## MODIFIED Requirements

### Requirement: Usage counted against the quota
The DerivedData usage SHALL be the total size of every folder found in the DerivedData location, whoever owns it. The shared compilation cache SHALL be shown separately and SHALL NOT count. The quota SHALL be exceeded when the usage is greater than the quota.

#### Scenario: Other projects count
- **WHEN** worktree folders add up to 20 GB and a folder of another project is 15 GB
- **THEN** the usage is 35 GB

#### Scenario: Compilation cache excluded
- **WHEN** project folders add up to 30 GB and the compilation cache is 10 GB
- **THEN** the usage is 30 GB

#### Scenario: No quota
- **WHEN** the user chose "No quota"
- **THEN** nothing is released whatever the usage

#### Scenario: Automatic quota
- **WHEN** the quota is Automatic and resolves to 40 GB while the usage is 55 GB
- **THEN** folders are released as with a fixed quota of 40 GB

## ADDED Requirements

### Requirement: Remember DerivedData sizes
The system SHALL remember each measured DerivedData folder's size, with the folder's last access when it was measured, in the user's ShogunAi data, shared by the desktop app and the `shogun` command. A folder whose last access is the same as when it was measured SHALL use its remembered size without being measured again. A folder with no remembered size, a different last access or no last access SHALL be measured. Remembered sizes of folders that no longer exist SHALL be forgotten.

#### Scenario: Unchanged folder
- **WHEN** a folder was measured at 12 GB and Xcode hasn't used its project since
- **THEN** its size is 12 GB without measuring it again

#### Scenario: Folder used again
- **WHEN** a folder was measured at 12 GB and Xcode has built its project since, updating its last access
- **THEN** the folder is measured again and its new size is remembered

#### Scenario: Folder deleted
- **WHEN** a remembered folder is deleted by Xcode, by "Delete orphans" or by the quota
- **THEN** its remembered size is forgotten the next time the sizes are read

#### Scenario: Remembered sizes unreadable
- **WHEN** the file of remembered sizes is missing or can't be read
- **THEN** every folder is measured and nothing fails

### Requirement: Measure DerivedData folders in parallel
Folders that have to be measured SHALL be measured several at a time, at most four at once, both in the desktop app and in the `shogun` command. A folder that can't be measured SHALL NOT stop the others.

#### Scenario: Many folders
- **WHEN** 40 folders have to be measured
- **THEN** up to four are measured at the same time until all are done

#### Scenario: One folder fails
- **WHEN** one folder can't be read while others are being measured
- **THEN** the other folders' sizes are still computed
