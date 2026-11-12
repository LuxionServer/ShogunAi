## MODIFIED Requirements

### Requirement: Attention color only when needed
An item SHALL use a status color, always through the theme's status mapping, only when it needs attention: the warning color when its pool is over the limit or DerivedData is over its quota, and the danger color when its last read failed for a reason other than a missing or optional tool. Otherwise it SHALL use the neutral text colors.

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
An item whose resource hasn't been measured yet SHALL show a dash as its figure instead of a "Calculating…" text, with a tooltip saying it isn't measured yet. An item whose last read failed SHALL give the reason in its tooltip and SHALL keep the last known figure when there is one. Its icon SHALL use the danger color, except when the read failed because a tool is missing or optional on this machine: then the item SHALL use the neutral colors.

#### Scenario: DerivedData sizes still being calculated
- **WHEN** a project declares the Xcode provider and the DerivedData folders are known but their sizes aren't yet
- **THEN** the DerivedData item shows a dash, its tooltip says it isn't measured yet, and the app stays responsive

#### Scenario: Docker unavailable
- **WHEN** a project declares the Compose provider and Docker isn't running
- **THEN** the Compose projects item shows a dash in the neutral colors and its tooltip says Docker isn't available and how to start it

#### Scenario: Android SDK not installed
- **WHEN** a project declares the emulator provider and the Android SDK isn't found
- **THEN** the Android emulators item shows a dash in the neutral colors and its tooltip says the SDK wasn't found

#### Scenario: iOS simulators not supported
- **WHEN** a project declares the iOS simulator provider on a machine that doesn't support iOS simulators
- **THEN** the iOS simulators item shows a dash in the neutral colors and its tooltip says why

#### Scenario: Real failure
- **WHEN** listing the Gradle daemons fails because the process listing command fails
- **THEN** the Gradle item's icon uses the danger color and its tooltip gives the reason

## ADDED Requirements

### Requirement: A Gradle pool held by running builds needs no attention
A Gradle pool over its limit with no idle daemon SHALL need no attention: its item SHALL use the neutral colors and its tooltip SHALL say it is over the limit because builds are running.

#### Scenario: Every daemon busy
- **WHEN** 9 Gradle daemons are in use, none of them idle, and the limit is 3
- **THEN** the Gradle item uses the neutral colors and its tooltip says over the limit because builds are running
