## MODIFIED Requirements

### Requirement: Column labels in device views
The Android emulators and iOS simulators views SHALL show a header row labeling their columns (device, worktree and state) in the section label style, using the same columns as the rows below it so each label sits above its column.

#### Scenario: Emulators listed
- **WHEN** the Android emulators view lists at least one emulator
- **THEN** a header row labels the device, worktree and state columns, each starting where its column starts in the rows below

## ADDED Requirements

### Requirement: Machine lists share the list row
Every list of the Machine views (Gradle and Maven daemons, Android emulators, iOS simulators, Xcode DerivedData, Compose projects and the desktop turn) SHALL be drawn with the shared list row, one line high, with its values in the data font and its states in the interface font.

#### Scenario: Gradle daemon row
- **WHEN** the Gradle daemons view lists an idle daemon owned by a worktree
- **THEN** its row is one line high, its PID and memory are in the data font, its state "idle 5 min" is in the interface font, and its "Stop" action sits at the row's end
