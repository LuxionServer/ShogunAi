## MODIFIED Requirements

### Requirement: Android emulators across projects
The Android emulators view SHALL list the emulator of every worktree of the projects that declare the emulator provider, with its project, worktree, serial and state, and Start when stopped or Stop otherwise. A worktree emulator that isn't running SHALL show the AVD it will start instead of a serial. It SHALL also list running emulators that no worktree owns, with a Stop action. Listing SHALL NOT reserve ports or start anything.

#### Scenario: Unowned emulator
- **WHEN** an emulator started from Android Studio is running and no worktree has its port reserved
- **THEN** it is listed as not owned by a worktree, with a Stop action

#### Scenario: Start from the machine view
- **WHEN** the user activates Start on a stopped worktree emulator in this view
- **THEN** the emulator starts as it would from the worktree's detail, and the state is shown in both places

#### Scenario: Stopped worktree emulator
- **WHEN** a worktree's emulator isn't running and the project uses the AVD `Pixel_8_API_35`
- **THEN** its row shows `Pixel_8_API_35` instead of "—", or "Automatic" when no AVD is set

### Requirement: iOS simulators across projects
On macOS, the iOS simulators view SHALL list every simulator whose name starts with `ShogunAi · `, with its project and worktree, state, and Start or Stop. A worktree whose simulator doesn't exist yet SHALL show that it isn't created, with the configured device type when there is one. Orphaned simulators SHALL be listed apart with a "Delete orphans" action that asks for confirmation. Simulators not created by ShogunAi SHALL NOT be listed.

#### Scenario: Orphans
- **WHEN** two ShogunAi simulators belong to worktrees that no longer exist
- **THEN** they are listed as orphaned, and after the user confirms "Delete orphans" they are deleted and the view reports it

#### Scenario: Simulator not created yet
- **WHEN** a worktree of a project with the device type `iPhone 16` has no simulator yet
- **THEN** its row says it isn't created and names `iPhone 16`, instead of "—"

## ADDED Requirements

### Requirement: Column labels in device views
The Android emulators and iOS simulators views SHALL show a header row labeling their columns (device, worktree and state), aligned with the rows below it.

#### Scenario: Emulators listed
- **WHEN** the Android emulators view lists at least one emulator
- **THEN** a header row labels the device, worktree and state columns above the rows
