# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: Machine item opens the overview
The status bar's "Machine" item SHALL show the Machine label (only its icon in the compact sidebar layout), be a button reachable with the keyboard with a tooltip, open the Machine overview when activated, and be marked as selected while the overview is shown.

#### Scenario: Open Machine with nothing declared
- **WHEN** no saved project declares a provider and the user activates the "Machine" item
- **THEN** the content area shows the Machine overview and the "Machine" item is marked as selected

#### Scenario: Narrow window
- **WHEN** the window is narrow enough for the sidebar to become the rail
- **THEN** the "Machine" item shows its icon without its label, and its tooltip still names it
