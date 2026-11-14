## MODIFIED Requirements

### Requirement: Usage counted against the quota
The DerivedData usage SHALL be the total size of every folder found in the DerivedData location, whoever owns it, plus the `.build` folders of the worktrees of repositories that declare the provider. The shared compilation cache SHALL be shown separately and SHALL NOT count. The quota SHALL be exceeded when the usage is greater than the quota.

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

#### Scenario: Swift build folders count
- **WHEN** DerivedData folders add up to 30 GB and the `.build` folders of the worktrees of a Swift package repository declaring the provider add up to 8 GB
- **THEN** the usage is 38 GB

## ADDED Requirements

### Requirement: Find Swift build folders
For each worktree of a repository that declares the provider, the system SHALL read the worktree's `.build` folder when the worktree has a `Package.swift` at its root and `.build` is a folder. Its last access SHALL be the latest modification of `.build` or of any entry directly inside it. Worktrees of repositories that don't declare the provider SHALL NOT be read.

#### Scenario: Package worktree built
- **WHEN** a worktree `task-123` of a repository declaring the provider has `Package.swift` and a `.build` folder
- **THEN** `<worktrees>/task-123/.build` is listed as belonging to `task-123`

#### Scenario: Not a package
- **WHEN** a worktree has a `.build` folder but no `Package.swift` at its root
- **THEN** no `.build` folder is listed for it

#### Scenario: Never built
- **WHEN** a worktree has `Package.swift` but no `.build` folder
- **THEN** nothing is listed for it and nothing fails

#### Scenario: Provider not declared
- **WHEN** a saved repository with `Package.swift` doesn't declare the provider
- **THEN** its worktrees' `.build` folders are neither listed, counted nor deleted

#### Scenario: Last build
- **WHEN** `swift build` rewrote `.build/build.db` ten minutes ago
- **THEN** the folder's last access is ten minutes ago

### Requirement: Swift build folders go with their worktree
A `.build` folder SHALL always belong to the worktree that contains it and SHALL never be reported as orphaned. Removing the worktree SHALL remove it with the rest of the worktree's files.

#### Scenario: Worktree removed
- **WHEN** a worktree with a 3 GB `.build` folder is removed
- **THEN** the `.build` folder is gone and isn't listed afterwards, not even as an orphan

### Requirement: Packages of each Swift build folder
When a `.build` folder is measured, the system SHALL also measure its `checkouts` and `repositories` subfolders, where SwiftPM keeps the resolved packages, and report their sum as the folder's packages size, part of the folder's size. Its size and packages size SHALL be remembered and measured in parallel under the same rules as a DerivedData folder's.

#### Scenario: Package with dependencies
- **WHEN** a `.build` folder is 4 GB, its `checkouts` 600 MB and its `repositories` 200 MB
- **THEN** it is reported at 4 GB, of which 800 MB are packages

#### Scenario: Unchanged folder
- **WHEN** a `.build` folder was measured at 4 GB and nothing inside it changed since
- **THEN** its size is 4 GB without measuring it again

### Requirement: Show Swift build folders
`shogun xcode` SHALL list each `.build` folder of the repository's worktrees by its path, with its size and packages size, next to the worktrees' DerivedData folders. In its JSON every folder SHALL have a `kind`, `derived-data` or `swift-build`.

#### Scenario: Command output
- **WHEN** a worktree `task-123` has a 4 GB `.build` folder with 800 MB of packages and the user runs `shogun xcode`
- **THEN** a line shows `<worktrees>/task-123/.build` with both sizes

#### Scenario: JSON
- **WHEN** the user runs `shogun xcode --json` in a repository with both kinds of folders
- **THEN** each entry of `folders` has `kind` `derived-data` or `swift-build`, and the `.build` folders have their `worktree`

### Requirement: Release Swift build folders to honour the quota
When the quota is exceeded, a `.build` folder SHALL be releasable like a worktree's DerivedData folder: after the orphans, when its worktree is not in use, ordered together with the worktree DerivedData folders by last access. Its worktree SHALL be in use while its agent is working or blocked, or while the folder was modified within the last hour, and it SHALL be checked again just before deleting it.

#### Scenario: Oldest build released
- **WHEN** the quota is exceeded, there are no orphans, an unused worktree's DerivedData was last accessed on Tuesday and another unused worktree's `.build` on Monday, and releasing one is enough
- **THEN** the `.build` folder is deleted and the DerivedData folder is kept

#### Scenario: Build running
- **WHEN** a `.build` folder is the oldest candidate but was modified two minutes ago when the deletion runs
- **THEN** it is skipped and the next candidate is considered

#### Scenario: Command trim
- **WHEN** the user runs `shogun xcode trim` and a `.build` folder is released
- **THEN** its path is reported among the released folders with the space freed

### Requirement: Delete unused Swift build folders
`shogun xcode clean --swift-build` SHALL delete the `.build` folders of the repository's worktrees that are not in use, report each one deleted with the space freed and each one kept because its worktree is in use. A folder that can't be deleted SHALL be reported without stopping the rest, and the command SHALL then exit with a failure. Without `--swift-build`, `shogun xcode clean` SHALL keep deleting only orphaned DerivedData.

#### Scenario: Unused builds deleted
- **WHEN** two worktrees have `.build` folders of 3 GB and 1 GB, nobody has built them for a day and their agents are idle
- **THEN** both folders are deleted and the command reports 4 GB freed

#### Scenario: Worktree in use
- **WHEN** a worktree's agent is working
- **THEN** its `.build` folder is kept and reported as in use

#### Scenario: Plain clean
- **WHEN** the user runs `shogun xcode clean` without `--swift-build`
- **THEN** no `.build` folder is deleted

#### Scenario: JSON
- **WHEN** the user runs `shogun xcode clean --swift-build --json`
- **THEN** the output has `deleted`, `freedBytes`, `inUse` and `failures`
