# Spec Delta

## MODIFIED Requirements

### Requirement: Gradle settings and build cache per project
The Gradle daemons view SHALL state, for each saved project that declares Gradle settings, the settings applied to its worktree builds. It SHALL also show one build cache notice for the projects that declare the Gradle provider and don't have the build cache enabled: naming the project when there is one, and saying how many projects when there are several, with their names under "Details".

#### Scenario: Two projects, one with settings
- **WHEN** `api` declares a 2g daemon heap and `main-repo` declares the provider without settings and with the build cache off
- **THEN** the view states `api`'s 2 GB heap and shows the build cache notice for `main-repo`

#### Scenario: Several projects with the build cache off
- **WHEN** `api`, `main-repo` and `web` declare the Gradle provider with the build cache off
- **THEN** the view shows a single build cache notice saying it's off in 3 projects, and its details list `api`, `main-repo` and `web`

## ADDED Requirements

### Requirement: Gradle daemon state column only when known
The Gradle daemons view SHALL leave out the state of its rows when no listed daemon has a known state (busy or idle), and SHALL show it for every row otherwise.

#### Scenario: Every state unknown
- **WHEN** the view lists four daemons and the state of all of them is unknown
- **THEN** no row shows a state

#### Scenario: One state known
- **WHEN** the view lists four daemons, one idle and three of unknown state
- **THEN** every row shows its state, the three unknown ones saying "state unknown"

### Requirement: Why "Stop idle daemons" is disabled
When "Stop idle daemons" is disabled because no daemon can be stopped, the Gradle daemons view SHALL say why next to it, counting the daemons that are busy and those whose state is unknown.

#### Scenario: All busy
- **WHEN** the view lists 9 Gradle daemons and all of them are busy
- **THEN** "Stop idle daemons" is disabled and the view says "All 9 are busy, none is idle"

#### Scenario: Busy and unknown
- **WHEN** the view lists 2 busy Gradle daemons and 3 whose state is unknown
- **THEN** "Stop idle daemons" is disabled and the view says 2 are busy and the state of 3 is unknown, none is idle

#### Scenario: All unknown
- **WHEN** the view lists 4 Gradle daemons and the state of all of them is unknown
- **THEN** "Stop idle daemons" is disabled and the view says the state of all 4 is unknown, none can be stopped safely

### Requirement: Unknown daemon owner
A Gradle daemon whose owner isn't known (no last build folder, or a Kotlin daemon) SHALL show "unknown" in its owner cell in the dimmed secondary text color, never in a status color.

#### Scenario: Daemon without a last build
- **WHEN** a Gradle daemon has no recorded last build folder
- **THEN** its owner cell reads "unknown" in the dimmed secondary text color

### Requirement: Stop buttons in the daemon rows
Each Gradle daemon row's "Stop" SHALL be a ghost button, without border or fill, whose label stays legible when it's disabled, and whose disabled reason is given in its tooltip.

#### Scenario: Busy daemon
- **WHEN** a Gradle daemon is busy
- **THEN** its row shows a disabled ghost "Stop" button whose tooltip says it's busy
