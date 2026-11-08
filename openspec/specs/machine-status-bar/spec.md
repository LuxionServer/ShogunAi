# machine-status-bar Specification

## Purpose
Summarizes the machine's shared resources (desktop turn, daemons, devices, DerivedData, Compose projects) in a single line at the bottom of the main window, so their state stays visible from any view and each one opens its detailed view.

## Requirements

### Requirement: Status bar at the bottom of the main window
The main window SHALL show a single-line status bar along its bottom edge, below the sidebar and the content area, starting with a "Machine" item followed by one item per Machine resource that applies on this machine, in this order: Desktop, Gradle daemons, Android emulators, iOS simulators, Xcode DerivedData, Maven daemons, Compose projects. The bar SHALL be shown even when no resource applies, with only its "Machine" item.

#### Scenario: Gradle and emulators declared
- **WHEN** one saved project declares the Gradle provider and another the emulator provider
- **THEN** the status bar shows the "Machine" item, a Gradle daemons item and an Android emulators item, and nothing else

#### Scenario: Nothing applies
- **WHEN** no saved project declares a provider and nobody holds or waits for the desktop turn
- **THEN** the status bar shows only its "Machine" item

#### Scenario: Visible from every view
- **WHEN** the status bar has items and the user moves from "Active" to a project's view and to Project settings
- **THEN** the status bar stays in place with the same items

### Requirement: Status bar item content
Each item SHALL show the resource's icon, a short name and its figure in the data typeface: the pool usage against its limit for Gradle daemons, emulators and simulators when a limit applies (the count otherwise), the DerivedData size against its quota (the size when there is no quota), the count for Maven daemons and Compose projects, and the holder and the waiters for the desktop turn. In the compact sidebar layout the short name SHALL be omitted.

#### Scenario: Gradle within the pool
- **WHEN** 2 Gradle daemons are in use and the pool limit is 3
- **THEN** the Gradle item shows `2/3`

#### Scenario: DerivedData against the quota
- **WHEN** DerivedData uses 1.2 GB with a 20.0 GB quota
- **THEN** the DerivedData item shows `1.2 GB / 20.0 GB`

#### Scenario: Desktop turn held
- **WHEN** `TASK-1` holds the desktop turn and two worktrees wait
- **THEN** the Desktop item shows `TASK-1` and 2 waiting

#### Scenario: Narrow window
- **WHEN** the window is narrow enough for the sidebar to become the rail
- **THEN** each status bar item shows its icon and figure without its short name

### Requirement: Full text in a tooltip
Each item SHALL have a tooltip with the resource's full name and its full summary (the count, the memory or size, the pool usage and, when it applies, "over the limit" or the reason of a failure), so no information depends on truncated text. Item texts SHALL NOT be truncated with an ellipsis.

#### Scenario: Over the limit
- **WHEN** 9 Gradle daemons are in use, the limit is 3 and they take 1.2 GB
- **THEN** the Gradle item's tooltip reads "Gradle daemons", the 10 daemons and 1.2 GB, `9/3` in use and "over the limit"

### Requirement: Attention color only when needed
An item SHALL use a status color, always through the theme's status mapping, only when it needs attention: the warning color when its pool is over the limit or DerivedData is over its quota, and the danger color when its last read failed for a reason other than a missing or optional tool. Otherwise it SHALL use the neutral text colors. A Gradle pool over its limit with no idle daemon needs no attention: its item SHALL use the neutral colors and its tooltip SHALL say it is over the limit because builds are running.

#### Scenario: Pool over the limit
- **WHEN** 9 Gradle daemons are in use and the limit is 3
- **THEN** the Gradle item's icon and figure use the warning color

#### Scenario: Within the limit
- **WHEN** 1 emulator is in use and the limit is 2
- **THEN** the Android emulators item uses the neutral colors

#### Scenario: Gradle pool held by running builds
- **WHEN** 9 Gradle daemons are in use, none of them idle, and the limit is 3
- **THEN** the Gradle item uses the neutral colors and its tooltip says over the limit because builds are running

### Requirement: Not yet measured and failed items
An item whose resource hasn't been measured yet SHALL show a dash as its figure instead of a "Calculating…" text, with a tooltip saying it isn't measured yet. An item whose last read failed SHALL give the reason in its tooltip and SHALL keep the last known figure when there is one. Its icon SHALL use the danger color, except when the read failed because a tool is missing or optional on this machine (the Android SDK isn't found, Docker isn't available, iOS simulators aren't supported here): then the item SHALL use the neutral colors.

#### Scenario: DerivedData sizes still being calculated
- **WHEN** a project declares the Xcode provider and the DerivedData folders are known but their sizes aren't yet
- **THEN** the DerivedData item shows a dash, its tooltip says it isn't measured yet, and the app stays responsive

#### Scenario: Docker unavailable
- **WHEN** a project declares the Compose provider and Docker isn't running
- **THEN** the Compose projects item shows a dash in the neutral colors and its tooltip says Docker isn't available and how to start it

#### Scenario: Android SDK not installed
- **WHEN** a project declares the emulator provider and the Android SDK isn't found
- **THEN** the Android emulators item shows a dash in the neutral colors and its tooltip says the SDK wasn't found

#### Scenario: Real failure
- **WHEN** listing the Gradle daemons fails because the process listing command fails
- **THEN** the Gradle item's icon uses the danger color and its tooltip gives the reason

### Requirement: Items open their view
Each item SHALL be a button labelled for assistive technologies with its full text, reachable with the keyboard, showing the theme's focus ring, and activating it SHALL open that resource's view. The item of the view being shown SHALL be marked as selected.

#### Scenario: Open the Gradle view
- **WHEN** the user activates the Gradle daemons item
- **THEN** the content area shows the Gradle daemons view and the item is marked as selected

#### Scenario: Keyboard
- **WHEN** the user moves the focus with Tab to the Android emulators item and presses Enter
- **THEN** the item shows the focus ring and the Android emulators view opens

### Requirement: Machine item opens the overview
The status bar's "Machine" item SHALL show the Machine label (only its icon in the compact sidebar layout), be a button reachable with the keyboard with a tooltip, open the Machine overview when activated, and be marked as selected while the overview is shown.

#### Scenario: Open Machine with nothing declared
- **WHEN** no saved project declares a provider and the user activates the "Machine" item
- **THEN** the content area shows the Machine overview and the "Machine" item is marked as selected

#### Scenario: Narrow window
- **WHEN** the window is narrow enough for the sidebar to become the rail
- **THEN** the "Machine" item shows its icon without its label, and its tooltip still names it
