# xcode-provider Specification

## Purpose
Defines the Xcode provider: how ShogunAi finds Xcode's DerivedData folders on a Mac, ties each one to a worktree or marks it as orphaned, deletes only orphaned folders, and turns on Xcode's compilation caching for worktree builds through the per-worktree environment.

## Requirements

### Requirement: Provider only on macOS
The Xcode provider SHALL only act on macOS and only for repositories that declare it. Elsewhere, or without the declaration, the system SHALL NOT read Xcode's preferences or DerivedData, show anything Xcode-related, or add variables to the environment.

#### Scenario: Repository without the provider
- **WHEN** a repository doesn't declare `"xcode"` in `providers`
- **THEN** no DerivedData is read for that repository

#### Scenario: Other operating system
- **WHEN** the repository declares the provider and ShogunAi runs on Linux or Windows
- **THEN** the provider is ignored without an error

### Requirement: Find DerivedData folders
The system SHALL look for DerivedData in the custom absolute location set in Xcode's preferences, or else in `~/Library/Developer/Xcode/DerivedData`. A folder SHALL count only if its `info.plist` records a `WorkspacePath`; anything else there, such as Xcode's shared caches, SHALL be neither listed nor deleted.

#### Scenario: Default location
- **WHEN** Xcode has no custom DerivedData location
- **THEN** the folders of `~/Library/Developer/Xcode/DerivedData` are read

#### Scenario: Custom location
- **WHEN** Xcode's preferences set DerivedData to `/Volumes/Fast/DerivedData`
- **THEN** the folders of that location are read instead

#### Scenario: Shared caches skipped
- **WHEN** the location contains `CompilationCache.noindex` and `ModuleCache.noindex`, which have no `info.plist`
- **THEN** they aren't listed, counted or deleted

#### Scenario: Location missing
- **WHEN** the DerivedData location doesn't exist
- **THEN** no folder is listed and nothing fails

### Requirement: Attribute DerivedData to worktrees
For a given repository, the system SHALL report each folder, with its size and last access, as belonging to the worktree that contains its `WorkspacePath` (the deepest one when worktrees are nested), to another project, or as orphaned when that path no longer exists.

#### Scenario: Worktree project
- **WHEN** a folder's `WorkspacePath` is `<worktrees>/task-123/iosApp/iosApp.xcodeproj`
- **THEN** the folder belongs to the worktree `task-123`

#### Scenario: Another project
- **WHEN** a folder's `WorkspacePath` is outside every worktree of the repository and still exists
- **THEN** the folder belongs to another project

#### Scenario: Project gone
- **WHEN** a folder's `WorkspacePath` no longer exists
- **THEN** the folder is orphaned

### Requirement: Unmounted volumes are not orphans
A folder whose `WorkspacePath` lies on a volume under `/Volumes` that isn't mounted SHALL be reported as on an unavailable volume, not as orphaned, and SHALL NOT be deleted.

#### Scenario: External disk unplugged
- **WHEN** a folder's `WorkspacePath` is `/Volumes/Work/app/App.xcodeproj` and `/Volumes/Work` isn't mounted
- **THEN** the folder is reported on an unavailable volume and kept

### Requirement: Delete only orphaned DerivedData
The system SHALL delete a DerivedData folder only if it is orphaned when the deletion runs, checking its `WorkspacePath` again just before deleting it. Orphans are looked for across the whole DerivedData location, whichever project created them. A folder that can't be deleted SHALL be reported without stopping the rest.

#### Scenario: Orphan deleted
- **WHEN** the user deletes orphans and a folder's project no longer exists
- **THEN** the folder is deleted and the result reports the space freed

#### Scenario: Live worktree kept
- **WHEN** a folder belongs to an existing worktree
- **THEN** no action deletes it

#### Scenario: Project restored in the meantime
- **WHEN** a folder was orphaned when listed but its `WorkspacePath` exists when the deletion runs
- **THEN** it isn't deleted

### Requirement: Compilation caching through the environment
When the provider declares `"compilationCache": true`, the system SHALL write a machine-level `.xcconfig` that enables compilation caching and Swift and Clang prefix mapping, and every worktree's environment SHALL contain `XCODE_XCCONFIG_FILE` with its path. A variable with that name declared in `env` SHALL take precedence; without the option, no variable SHALL be added.

#### Scenario: Caching declared
- **WHEN** the repository declares `{ "xcode": { "compilationCache": true } }` and a worktree's environment is resolved
- **THEN** it contains `XCODE_XCCONFIG_FILE` pointing to a file that sets `COMPILATION_CACHE_ENABLE_CACHING`, `SWIFT_ENABLE_PREFIX_MAPPING`, `SWIFT_ENABLE_PROJECT_PREFIX_MAPPING`, `CLANG_ENABLE_PREFIX_MAPPING` and `CLANG_ENABLE_PROJECT_PREFIX_MAPPING` to `YES`

#### Scenario: Caching not declared
- **WHEN** the repository declares `{ "xcode": {} }`
- **THEN** the environment has no `XCODE_XCCONFIG_FILE` from the provider

#### Scenario: Declared variable wins
- **WHEN** caching is declared and `env` sets `XCODE_XCCONFIG_FILE`
- **THEN** the worktree's `XCODE_XCCONFIG_FILE` is the one declared in `env`

### Requirement: Worktree builds reuse each other's compilations
With compilation caching declared, an `xcodebuild` of an Xcode project started from a shell with the worktree's environment SHALL be able to reuse compilations made in another worktree of the same project.

#### Scenario: Second worktree
- **WHEN** a worktree built the project and an agent then builds it in a new worktree after `eval "$(shogun env)"`
- **THEN** the new worktree's build reports compilation cache hits for the unchanged sources

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
`shogun xcode` SHALL show each worktree folder's packages size next to its size when it has packages, and its JSON SHALL include `sourcePackagesBytes` for every folder. The desktop app's DerivedData view SHALL show it next to each worktree's size in the same way.

#### Scenario: Command output
- **WHEN** a worktree's folder is 6 GB with 800 MB of packages and the user runs `shogun xcode`
- **THEN** its line shows both sizes

#### Scenario: Folder without packages
- **WHEN** a worktree's folder has no Swift packages
- **THEN** its line shows only its size and its JSON has `sourcePackagesBytes` 0

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
