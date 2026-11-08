# Spec Delta

## RENAMED Requirements

- FROM: `### Requirement: Unknown daemon owner`
- TO: `### Requirement: Unknown owner in machine lists`

- FROM: `### Requirement: Stop buttons in the daemon rows`
- TO: `### Requirement: Ghost actions in machine rows`

## MODIFIED Requirements

### Requirement: Unknown owner in machine lists
A row of any Machine list whose owner isn't known (a Gradle daemon without a last build folder, a Kotlin daemon, or a Maven daemon that ShogunAi can't attribute) SHALL show "unknown" in its owner cell in the dimmed secondary text color, never blank and never in a status color.

#### Scenario: Daemon without a last build
- **WHEN** a Gradle daemon has no recorded last build folder
- **THEN** its owner cell reads "unknown" in the dimmed secondary text color

#### Scenario: Maven daemon without an owner
- **WHEN** a Maven daemon's working folder isn't known
- **THEN** its owner cell reads "unknown" in the dimmed secondary text color

### Requirement: Ghost actions in machine rows
The actions inside the rows of the Gradle daemons, Maven daemons, Android emulators and iOS simulators views (Stop, Start) SHALL be ghost buttons, without border or fill, whose label stays legible when disabled, and a disabled one SHALL give its reason in its tooltip. The actions that apply to a whole list keep their own style.

#### Scenario: Busy daemon
- **WHEN** a Gradle daemon is busy
- **THEN** its row shows a disabled ghost "Stop" button whose tooltip says it's busy

#### Scenario: Busy Maven daemon
- **WHEN** a Maven daemon is busy
- **THEN** its row shows a disabled ghost "Stop" button whose tooltip says only idle daemons can be stopped

#### Scenario: Maven daemon of unknown state
- **WHEN** a Maven daemon's state is unknown
- **THEN** its row shows a disabled ghost "Stop" button whose tooltip says its state is unknown

#### Scenario: Emulator and simulator rows
- **WHEN** the Android emulators view lists a stopped worktree emulator and a running unowned one, and the iOS simulators view lists a booted worktree simulator
- **THEN** their Start and Stop actions are ghost buttons

## ADDED Requirements

### Requirement: Why a list action is disabled
A disabled action that applies to a whole list in the Maven daemons, iOS simulators, Xcode DerivedData or Compose projects view SHALL give the reason it's disabled in its tooltip and, unless the reason is that the action is already running (which its progress indicator shows), SHALL also write it next to the action.

#### Scenario: No Maven daemon idle
- **WHEN** the Maven daemons view lists 2 busy daemons and 3 whose state is unknown
- **THEN** "Stop idle daemons" is disabled and the view says 2 are busy and the state of 3 is unknown, none is idle

#### Scenario: All Maven daemons busy
- **WHEN** the Maven daemons view lists 3 daemons and all of them are busy
- **THEN** "Stop idle daemons" is disabled and the view says "All 3 are busy, none is idle"

#### Scenario: Maven daemons being stopped
- **WHEN** Maven daemons are being stopped
- **THEN** "Stop idle daemons" is disabled, its tooltip says daemons are being stopped, and no reason is written next to it

#### Scenario: No orphaned DerivedData
- **WHEN** no DerivedData folder is orphaned
- **THEN** "Delete orphans" is disabled and the view says next to it that no folder is orphaned

#### Scenario: Simulators being deleted
- **WHEN** the orphaned simulators are being deleted
- **THEN** "Delete orphans" is disabled and its tooltip says they are being deleted

#### Scenario: No Compose project of a removed worktree
- **WHEN** every listed Compose project belongs to an existing worktree
- **THEN** "Take down the projects of removed worktrees" is disabled and the view says next to it that no project belongs to a removed worktree

#### Scenario: Only volumes left
- **WHEN** the only leftover Docker resources are volumes
- **THEN** "Remove networks and images" is disabled and the view says next to it that only volumes are left, while "Remove volumes too" stays enabled
