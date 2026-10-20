# machine-resources Specification

## Purpose

Shows the build and device resources that belong to the machine (Gradle daemons, Android emulators, iOS simulators, Xcode DerivedData) across every saved project, so the user can see what they cost and clean them up in one place.

## Requirements

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

### Requirement: Gradle daemons across projects
The Gradle daemons view SHALL list every Gradle and Kotlin daemon on the machine with its kind, version, process id, memory, state and owner, the total memory, and actions to stop one daemon or every idle one, following the existing stop rules. The result of a stop SHALL stay visible until dismissed.

#### Scenario: Stop idle daemons
- **WHEN** the user activates "Stop idle daemons" with two idle daemons and one busy
- **THEN** the two idle ones are stopped, the busy one is kept, the view says so and the list refreshes

### Requirement: Owners across saved projects
A Gradle daemon (by its last build's folder), a DerivedData folder (by its workspace path) and a ShogunAi simulator (by its recorded worktree) SHALL be attributed to the deepest worktree of any saved project that contains it, shown as project and worktree. Otherwise it SHALL be shown as belonging to a removed worktree when its folder no longer exists, or to a folder outside the saved projects.

#### Scenario: Daemon of another saved project
- **WHEN** a daemon's last build ran in a worktree `TASK-9` of the saved project `api`, and the user came from `main-repo`
- **THEN** the daemon is shown as belonging to `api` · `TASK-9`

#### Scenario: Folder outside every project
- **WHEN** a daemon's last build ran in an existing folder that isn't inside any saved project
- **THEN** the daemon is shown as belonging to a folder outside the saved projects

### Requirement: Go to the owner's worktree
Activating a resource's owner SHALL open "All worktrees" with that worktree selected.

#### Scenario: Jump to the worktree
- **WHEN** the user activates the owner `api` · `TASK-9` of a daemon
- **THEN** "All worktrees" opens with `TASK-9` of `api` selected and its detail shown

### Requirement: Gradle settings and build cache per project
The Gradle daemons view SHALL state, for each saved project that declares Gradle settings, the settings applied to its worktree builds. It SHALL also show the build cache notice for each project that declares the Gradle provider and doesn't have the build cache enabled.

#### Scenario: Two projects, one with settings
- **WHEN** `api` declares a 2g daemon heap and `main-repo` declares the provider without settings and with the build cache off
- **THEN** the view states `api`'s 2 GB heap and shows the build cache notice for `main-repo`

### Requirement: Android emulators across projects
The Android emulators view SHALL list the emulator of every worktree of the projects that declare the emulator provider, with its project, worktree, serial and state, and Start when stopped or not acquired, or Stop otherwise. A worktree emulator that isn't running SHALL show the AVD it will start instead of a serial, and one not acquired SHALL say so. It SHALL also list running emulators that no worktree owns, with a Stop action. Listing SHALL NOT reserve ports or start anything.

#### Scenario: Unowned emulator
- **WHEN** an emulator started from Android Studio is running and no worktree has its port reserved
- **THEN** it is listed as not owned by a worktree, with a Stop action

#### Scenario: Start from the machine view
- **WHEN** the user activates Start on a stopped worktree emulator in this view
- **THEN** the emulator starts as it would from the worktree's detail, and the state is shown in both places

#### Scenario: Stopped worktree emulator
- **WHEN** a worktree's emulator isn't running and the project uses the AVD `Pixel_8_API_35`
- **THEN** its row shows `Pixel_8_API_35` instead of "—", or "Automatic" when no AVD is set

#### Scenario: Not acquired
- **WHEN** a worktree never started its emulator
- **THEN** its row says it isn't acquired and offers Start, and no port is reserved for it

### Requirement: iOS simulators across projects
On macOS, the iOS simulators view SHALL list every simulator whose name starts with `ShogunAi · `, with its project and worktree, state, and Start or Stop. A worktree whose simulator doesn't exist yet SHALL show that it isn't created, with the configured device type when there is one. Orphaned simulators SHALL be listed apart with a "Delete orphans" action that asks for confirmation. Simulators not created by ShogunAi SHALL NOT be listed.

#### Scenario: Orphans
- **WHEN** two ShogunAi simulators belong to worktrees that no longer exist
- **THEN** they are listed as orphaned, and after the user confirms "Delete orphans" they are deleted and the view reports it

#### Scenario: Simulator not created yet
- **WHEN** a worktree of a project with the device type `iPhone 16` has no simulator yet
- **THEN** its row says it isn't created and names `iPhone 16`, instead of "—"

### Requirement: Xcode DerivedData across projects
On macOS, the DerivedData view SHALL list every DerivedData folder with its size and owner, the total size, the orphaned folders with their total and a "Delete orphans" action that asks for confirmation, folders on unavailable volumes as kept, and the compilation cache size when any saved project declares compilation caching.

#### Scenario: Delete orphans
- **WHEN** three orphaned folders take 4 GB and the user confirms "Delete orphans"
- **THEN** they are deleted, following the existing rules, and the view reports the space freed and refreshes

#### Scenario: No orphans
- **WHEN** there are no orphaned folders
- **THEN** the delete action is disabled

### Requirement: Refresh while shown
A machine view SHALL refresh its process and device states every 5 seconds while it is shown, and its disk sizes when it is opened and on View > Reload. Nothing SHALL be inspected for a resource whose view isn't shown, apart from the sidebar summaries, which refresh every 30 seconds.

#### Scenario: Daemon finishes a build
- **WHEN** a busy daemon becomes idle while the Gradle view is shown
- **THEN** it is shown as idle within a few seconds and its stop action becomes enabled

### Requirement: Column labels in device views
The Android emulators and iOS simulators views SHALL show a header row labeling their columns (device, worktree and state), aligned with the rows below it.

#### Scenario: Emulators listed
- **WHEN** the Android emulators view lists at least one emulator
- **THEN** a header row labels the device, worktree and state columns above the rows

### Requirement: Pool usage in the machine views
The Android emulators, iOS simulators and Gradle daemons views SHALL show their pool's usage against its effective limit and the active profile, with a link to Preferences' "Resources" section, and the sidebar summary SHALL include the usage against the limit. When a resource was released or stopped to make room or to stay within the limit, the view SHALL say which one and why until the user dismisses it.

#### Scenario: Usage shown
- **WHEN** the emulator limit is 2 and one worktree holds an emulator
- **THEN** the Android emulators view says 1 of 2 in use with the profile's name

#### Scenario: Release reported
- **WHEN** starting `TASK-2`'s emulator released `TASK-1`'s idle emulator
- **THEN** the Android emulators view says `TASK-1`'s emulator was released to make room for `TASK-2`

#### Scenario: Over the limit
- **WHEN** the Gradle limit is 2 and three busy daemons run
- **THEN** the Gradle daemons view shows 3 of 2 in use, marked as over the limit

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
