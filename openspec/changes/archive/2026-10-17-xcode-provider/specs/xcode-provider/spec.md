## Purpose

Defines the Xcode provider: how ShogunAi finds Xcode's DerivedData folders on a Mac, ties each one to a worktree or marks it as orphaned, deletes only orphaned folders, and turns on Xcode's compilation caching for worktree builds through the per-worktree environment.

## ADDED Requirements

### Requirement: Provider only on macOS
The Xcode provider SHALL only act on macOS and only for repositories that declare it. Elsewhere, or without the declaration, the system SHALL NOT read Xcode's preferences or DerivedData, show anything Xcode-related, or add variables to the environment.

#### Scenario: Repository without the provider
- **WHEN** a repository doesn't declare `"xcode"` in `providers`
- **THEN** no DerivedData is read and the worktree screen has no Xcode section

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

### Requirement: DerivedData on the worktree screen
The worktree screen of a project whose repository declares the provider SHALL show an "Xcode DerivedData" section with each worktree's folder size, the total for the repository, the number and size of orphaned folders on the machine with an action to delete them, and the compilation cache size when caching is declared.

#### Scenario: Section shown
- **WHEN** the user opens the worktree screen of a repository that declares the provider and two worktrees have DerivedData
- **THEN** the section lists both with their sizes and the total

#### Scenario: Orphans
- **WHEN** three orphaned folders take 4 GB
- **THEN** the section shows them with a "Delete orphans" action, and after deleting it reports the space freed and refreshes

#### Scenario: No orphans
- **WHEN** there are no orphaned folders
- **THEN** the delete action is disabled

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
