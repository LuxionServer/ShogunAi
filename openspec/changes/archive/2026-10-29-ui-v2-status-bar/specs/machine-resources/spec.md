## REMOVED Requirements

### Requirement: Machine section in the sidebar
**Reason**: The sidebar has no room for the resource summaries, which were truncated, and the sidebar is kept for views, projects and multirepos. The summaries move to the bottom status bar (capability `machine-status-bar`).
**Migration**: Each resource's view opens from its status bar item or from the command palette; the views themselves are unchanged and the last shown machine view is still restored on launch.

## RENAMED Requirements

- FROM: `### Requirement: Desktop entry in the Machine section`
- TO: `### Requirement: Desktop entry in the status bar`

## MODIFIED Requirements

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

### Requirement: Pool usage in the machine views
The Android emulators, iOS simulators and Gradle daemons views SHALL show their pool's usage against its effective limit and the active profile, with a link to Preferences' "Resources" section, and the status bar item SHALL include the usage against the limit. When a resource was released or stopped to make room or to stay within the limit, the view SHALL say which one and why until the user dismisses it.

#### Scenario: Usage shown
- **WHEN** the emulator limit is 2 and one worktree holds an emulator
- **THEN** the Android emulators view says 1 of 2 in use with the profile's name

#### Scenario: Release reported
- **WHEN** starting `TASK-2`'s emulator released `TASK-1`'s idle emulator
- **THEN** the Android emulators view says `TASK-1`'s emulator was released to make room for `TASK-2`

#### Scenario: Over the limit
- **WHEN** the Gradle limit is 2 and three busy daemons run
- **THEN** the Gradle daemons view shows 3 of 2 in use, marked as over the limit

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
