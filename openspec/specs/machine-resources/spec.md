# machine-resources Specification

## Purpose

Shows the build and device resources that belong to the machine (Gradle daemons, Android emulators, iOS simulators, Xcode DerivedData) across every saved project, so the user can see what they cost and clean them up in one place.

## Requirements

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

### Requirement: Pause while the window is hidden
While the main window is minimized, in the tray or the app is hidden, neither the shown machine view nor the sidebar summaries SHALL refresh, except for trimming Gradle daemons to the pool limit. Both SHALL refresh as soon as the window is shown again.

#### Scenario: Window minimized
- **WHEN** the Gradle daemons view is shown, a project declares the emulator provider and the user minimizes the window for a minute
- **THEN** no daemon, emulator or other machine state is read during that minute except to trim idle Gradle daemons beyond the limit, and the view and the summaries refresh when the window is shown again

### Requirement: Column labels in device views
The Android emulators and iOS simulators views SHALL show a header row labeling their columns (device, worktree and state) in the section label style, using the same columns as the rows below it so each label sits above its column.

#### Scenario: Emulators listed
- **WHEN** the Android emulators view lists at least one emulator
- **THEN** a header row labels the device, worktree and state columns, each starting where its column starts in the rows below

### Requirement: Pool usage in the machine views
The Android emulators, iOS simulators and Gradle daemons views SHALL show their pool's usage against its effective limit, in the data font, and the active profile, in the interface font, with a link to Preferences' "Resources" section, and the status bar item SHALL include the usage against the limit. When a resource was released or stopped to make room or to stay within the limit, the view SHALL say which one and why until the user dismisses it.

#### Scenario: Usage shown
- **WHEN** the emulator limit is 2 and one worktree holds an emulator
- **THEN** the Android emulators view says 1 of 2 in use, in the data font, followed by the profile's name, such as "Automatic", in the interface font

#### Scenario: Release reported
- **WHEN** starting `TASK-2`'s emulator released `TASK-1`'s idle emulator
- **THEN** the Android emulators view says `TASK-1`'s emulator was released to make room for `TASK-2`

#### Scenario: Over the limit
- **WHEN** the Gradle limit is 2 and three busy daemons run
- **THEN** the Gradle daemons view shows 3 of 2 in use, marked "Over the limit"

### Requirement: DerivedData listed before its sizes
The DerivedData view SHALL show its folders, their owners and the orphans as soon as the folders are read, without waiting for any size. Each folder whose size isn't known yet, the totals that include it and the compilation cache size SHALL show a calculating state, replaced by the size once computed. The app SHALL stay responsive while sizes are computed.

#### Scenario: Many large folders
- **WHEN** the user opens the DerivedData view and the location holds 200 folders taking 150 GB
- **THEN** the 200 folders are listed with their owners before any size is known, each size and total reads as calculating, and the sizes appear as they are computed while the rest of the app keeps responding

#### Scenario: Delete orphans before sizes are known
- **WHEN** the orphans are listed but their sizes are still being calculated and the user activates "Delete orphans"
- **THEN** the confirmation names the number of orphans, and their total size once it's known

### Requirement: Cancellable size calculation
DerivedData sizes SHALL be computed in the background, several folders at a time, and SHALL keep being computed when the user leaves the view so the status bar item can show the total. Reloading the view SHALL stop the running calculation and measure every folder read again, and the calculation SHALL stop when no saved project declares the Xcode provider anymore.

#### Scenario: Leave the view
- **WHEN** sizes are still being calculated and the user shows another view
- **THEN** the calculation goes on without blocking the app, and the status bar item shows the total once every size is known

#### Scenario: Reload
- **WHEN** the user reloads the DerivedData view while sizes are being calculated
- **THEN** the running calculation stops, the folders are read again and every folder is measured again, including those with a remembered size

#### Scenario: Provider no longer declared
- **WHEN** sizes are being calculated and the only project that declared the Xcode provider is removed
- **THEN** the calculation stops

### Requirement: Orphans found without measuring every folder
Looking for orphaned DerivedData in a project's worktree view and before a cleanup SHALL measure only the orphaned folders, not every folder in the DerivedData location.

#### Scenario: Cleanup dialog
- **WHEN** the user opens the cleanup dialog of an Xcode project and the location holds 200 folders of which 2 are orphaned
- **THEN** only the 2 orphaned folders are measured to show their total

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

### Requirement: Remembered sizes shown first
When the DerivedData view or the sidebar summary reads the folders, each folder with a remembered size SHALL show it at once, and the totals SHALL include it. On a reload, a folder being measured again SHALL keep showing its remembered size until the new one is known.

#### Scenario: App started again
- **WHEN** the app starts and 180 of 200 DerivedData folders have remembered sizes still valid
- **THEN** those 180 sizes are shown when the folders are listed and only the other 20 show a calculating state

#### Scenario: Reload keeps the previous figures
- **WHEN** the user reloads the view and a folder's remembered size is 8 GB
- **THEN** the folder shows 8 GB while it is measured again, and then its new size

### Requirement: Leftover Docker resources in the Compose view
The Compose view of the Machine section SHALL list, after the projects, the volumes, networks and images left by the projects of removed worktrees of every saved project that declares the provider, each with its kind, name and project, and SHALL offer an action to remove them that keeps volumes, and a separate one that also removes volumes and asks for confirmation first. The outcome SHALL be shown with each failure and its reason.

#### Scenario: Leftovers listed
- **WHEN** a removed worktree of a saved project left a network and a volume
- **THEN** the Compose view lists both with their kind and project after the projects

#### Scenario: Remove keeping volumes
- **WHEN** the user activates the removal that keeps volumes
- **THEN** the network is removed, the volume is still listed, and the view says what was removed

#### Scenario: Remove volumes after confirming
- **WHEN** the user activates the removal of volumes and confirms
- **THEN** the volume is removed too

#### Scenario: Nothing left
- **WHEN** no removed worktree left any resource
- **THEN** the view shows no leftovers section and no removal action

### Requirement: Desktop entry in the status bar
While a worktree holds the desktop turn or any worktree waits for it, the status bar SHALL show a "Desktop" item, also when no saved project declares a provider, with a summary naming the holder and the number of waiters. Its view SHALL show the holder with project, worktree, since when and until when, and "Release" with a confirmation naming it, then the waiters in order with since when each waits.

#### Scenario: Held with waiters
- **WHEN** `api` · `TASK-1` holds the turn and two worktrees wait
- **THEN** the status bar shows "Desktop" with `api` · `TASK-1` and 2 waiting, and its view lists the holder and the two waiters in order

#### Scenario: Desktop free
- **WHEN** nobody holds the turn and nobody waits
- **THEN** the status bar shows no "Desktop" item

#### Scenario: No provider declared
- **WHEN** no saved project declares any provider and a worktree holds the turn
- **THEN** the status bar shows only the "Desktop" item

#### Scenario: Holder outside the saved projects
- **WHEN** the holder's worktree isn't in any saved project
- **THEN** the view shows its folder as outside the saved projects

#### Scenario: Go to the holder
- **WHEN** the user activates the holder `api` · `TASK-1` in the Desktop view
- **THEN** "All worktrees" opens with `TASK-1` of `api` selected

#### Scenario: Shown while open
- **WHEN** the Desktop view is shown and the holder releases the turn from the command line
- **THEN** the view shows the change without the user refreshing it

### Requirement: Reading machine resources never blocks the interface
The Machine section SHALL read and act on machine resources (daemon lists, pool usage, the Gradle daemon trim, device states, DerivedData, Compose projects, the desktop turn) without blocking the interface: a slow disk, settings file or command SHALL delay only that entry's figures, never the window's response to input.

#### Scenario: Slow settings file during the summary refresh
- **WHEN** the summary refresh trims Gradle daemons and reading the resource limits takes 20 seconds
- **THEN** the window keeps responding during those 20 seconds and the summaries update when the read finishes

#### Scenario: Shown view with a slow command
- **WHEN** the Gradle daemons view is shown and listing the daemons takes several seconds
- **THEN** the window keeps responding and the list updates when the command finishes

### Requirement: Maven daemons across projects
The Maven daemons view SHALL list every Maven daemon on the machine with its process id, memory, state and owner, the total memory, and actions to stop one daemon or every idle one, following the Maven provider's stop rules. The result of a stop SHALL stay visible until dismissed.

#### Scenario: State shown
- **WHEN** one Maven daemon is idle since 12:57 and another is busy
- **THEN** the first row says it's idle with how long, and the second says it's busy

#### Scenario: Stop idle daemons
- **WHEN** the user activates "Stop idle daemons" with two idle Maven daemons and one busy
- **THEN** the two idle ones are stopped, the busy one is kept, the view says so and the list refreshes

#### Scenario: Stop one daemon
- **WHEN** the user stops a Maven daemon whose state is unknown
- **THEN** it isn't stopped and the view says its state is unknown

### Requirement: Release and trim reports are grouped per view
A machine view SHALL group its release and trim reports into a single banner: reports of the same kind are summarized together (the total of daemons stopped to stay within the limit, the number of resources released to make room) with their count, and each report stays readable in the banner's details until the user dismisses the banner.

#### Scenario: Repeated trims
- **WHEN** 1, 2 and 1 idle Gradle daemons were stopped in three trims to stay within a limit of 3
- **THEN** the Gradle daemons view shows one banner saying 4 idle Gradle daemons were stopped to stay within the limit of 3, with a count of 3 and the three reports in its details

#### Scenario: Releases and trims together
- **WHEN** the Gradle daemons view has one release report and two trim reports
- **THEN** it shows one banner that summarizes both kinds, and its details list the three reports

### Requirement: Docker unavailable in the Compose view
When Docker isn't available, the Compose projects view SHALL show an error state saying Docker isn't available, with "Try again" reading the view again, and the reason Docker gave folded under "Technical details".

#### Scenario: Socket unreachable
- **WHEN** listing Compose projects fails because Docker's socket can't be reached
- **THEN** the view shows the error state, and the socket path appears only in its technical details

### Requirement: Machine lists share the list row
Every list of the Machine views (Gradle and Maven daemons, Android emulators, iOS simulators, Xcode DerivedData, Compose projects and the desktop turn) SHALL be drawn with the shared list row, one line high, with its values in the data font and its states in the interface font.

#### Scenario: Gradle daemon row
- **WHEN** the Gradle daemons view lists an idle daemon owned by a worktree
- **THEN** its row is one line high, its PID and memory are in the data font, its state "idle 5 min" is in the interface font, and its "Stop" action sits at the row's end

### Requirement: Modes and states in the device column
A machine view's device column SHALL write data (a serial, an AVD, a simulator's name or device type) in the data font and modes or states ("Automatic", "Not created") in the interface font.

#### Scenario: Automatic AVD
- **WHEN** a worktree emulator isn't running and its project sets no AVD
- **THEN** its device cell reads "Automatic" in the interface font

#### Scenario: Simulator not created
- **WHEN** a worktree of a project with the device type `iPhone 16` has no simulator yet
- **THEN** its device cell reads "Not created" in the interface font and `iPhone 16` in the data font

### Requirement: State chips in sentence case
Every state chip of the machine views and of the inspector's Resources section SHALL take its text from the shared state mapping, written in sentence case, in English and Spanish.

#### Scenario: Stopped emulator
- **WHEN** a worktree emulator was acquired and is stopped
- **THEN** its chip reads "Stopped", like "Not acquired" for one never started

#### Scenario: Simulator states
- **WHEN** simulators are shut down, booting and booted
- **THEN** their chips read "Shut down", "Booting" and "Booted"
