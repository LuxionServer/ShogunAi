# Spec Delta

## ADDED Requirements

### Requirement: Usage counted against the quota
The DerivedData usage SHALL be the total size of every folder found in the DerivedData location, whoever owns it. The shared compilation cache SHALL be shown separately and SHALL NOT count. The quota SHALL be exceeded when the usage is greater than the quota.

#### Scenario: Other projects count
- **WHEN** worktree folders add up to 20 GB and a folder of another project is 15 GB
- **THEN** the usage is 35 GB

#### Scenario: Compilation cache excluded
- **WHEN** project folders add up to 30 GB and the compilation cache is 10 GB
- **THEN** the usage is 30 GB

#### Scenario: No quota
- **WHEN** no quota is set
- **THEN** nothing is released whatever the usage

### Requirement: Release folders to honour the quota
When the quota is exceeded, the system SHALL delete releasable folders until the usage is within the quota or none is left, and SHALL stop as soon as it is within. Releasable folders SHALL be orphaned folders first, then folders of worktrees that are not in use, least recently accessed first, a folder without a last access counting as the least recent. Folders of other projects and folders on unavailable volumes SHALL never be released.

#### Scenario: Orphans first
- **WHEN** the quota is exceeded and there are an orphaned folder and an old unused worktree folder
- **THEN** the orphan is deleted first and the worktree folder only if the usage is still over the quota

#### Scenario: Least recently accessed first
- **WHEN** two unused worktree folders were last accessed on Monday and on Tuesday and releasing one is enough
- **THEN** Monday's is deleted and Tuesday's is kept

#### Scenario: Stops when within the quota
- **WHEN** deleting the first candidate brings the usage under the quota
- **THEN** no other folder is deleted

#### Scenario: Foreign folders kept
- **WHEN** the quota is exceeded and the only remaining folders belong to another project or to an unmounted volume
- **THEN** nothing is deleted and the result says the quota is still exceeded

### Requirement: Folders in use are never released
A worktree SHALL be in use while its agent is working or blocked, or while its DerivedData folder was accessed within the last hour. Its folder SHALL NOT be released. If the quota is still exceeded after every releasable folder is gone, the result SHALL say so and name the worktrees in use.

#### Scenario: Agent working
- **WHEN** the agent of `TASK-1` is working and its folder is the oldest
- **THEN** that folder is skipped and the next oldest unused folder is released

#### Scenario: Recently accessed
- **WHEN** a worktree's folder was accessed 10 minutes ago and its agent state is unknown
- **THEN** its folder is not released

#### Scenario: Only folders in use remain
- **WHEN** the quota is exceeded and every worktree folder is in use
- **THEN** nothing is deleted and the result names those worktrees as the reason the quota is still exceeded

### Requirement: Check again before releasing
Just before deleting a folder to honour the quota, the system SHALL read its owner and last access again and SHALL skip it if it is no longer releasable. A folder that cannot be deleted SHALL be reported without stopping the rest.

#### Scenario: Accessed in the meantime
- **WHEN** a candidate folder is accessed by Xcode between planning and deleting
- **THEN** it is skipped and the next candidate is considered

#### Scenario: Deletion fails
- **WHEN** one candidate cannot be deleted
- **THEN** the failure is reported and the following candidates are still processed

### Requirement: Apply the quota in the desktop app
With a quota set, the desktop app SHALL apply it once the sizes of the DerivedData folders are known, when the DerivedData is first measured after startup and on every refresh, and SHALL show what was released until dismissed. It SHALL NOT start a second release while one is running.

#### Scenario: Over the quota after a refresh
- **WHEN** a quota of 50 GB is set and the measured usage is 62 GB with enough unused folders
- **THEN** folders are released until the usage is within 50 GB and the view reports the folders and the space freed

#### Scenario: Within the quota
- **WHEN** the measured usage is under the quota
- **THEN** nothing is deleted and no notice is shown
