## ADDED Requirements

### Requirement: Swift build folders in the DerivedData view
On macOS, the DerivedData view SHALL list the `.build` folders of the worktrees of saved projects that declare the Xcode provider, each as a row of its project and worktree marked as `.build`, with its size, packages size and last access, counted in the total, the usage and the quota. A "Delete unused .build" action SHALL ask for confirmation and delete the folders whose worktree is not in use.

#### Scenario: Package worktree shown
- **WHEN** a worktree `task-123` of a saved Swift package project has a 4 GB `.build` folder
- **THEN** the view shows a `.build` row for `task-123` with 4 GB, and the total includes it

#### Scenario: Delete unused builds
- **WHEN** two `.build` folders take 5 GB, one worktree's agent is working and the user confirms "Delete unused .build"
- **THEN** only the other worktree's folder is deleted, the view reports the space freed and refreshes

#### Scenario: No builds
- **WHEN** no saved project's worktree has a `.build` folder
- **THEN** the "Delete unused .build" action is disabled

### Requirement: Quota releases unused worktree folders in the desktop app
When the desktop app applies the DerivedData quota, the DerivedData folders and `.build` folders of the worktrees of saved projects that declare the Xcode provider SHALL be releasable when their worktree is not in use, not only the orphaned folders.

#### Scenario: Unused worktree folder released
- **WHEN** the quota is exceeded, there are no orphans and a worktree of a saved project has an unused DerivedData folder
- **THEN** the app releases that folder and reports it
