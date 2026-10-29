## MODIFIED Requirements

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

## ADDED Requirements

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
