## MODIFIED Requirements

### Requirement: Boot, shut down and report a worktree's simulator
Starting SHALL boot the worktree's simulator and wait until it has finished booting or a timeout passes; starting a booted one SHALL succeed without changes. Once booted, starting SHALL open the Simulator app showing that device, best effort: if the window can't be opened, the start still succeeds. Stopping SHALL shut it down, and stopping a shut-down one SHALL succeed without changes. The state SHALL be reported as shut down, booting or booted.

#### Scenario: Start and wait
- **WHEN** the user starts a worktree's shut-down simulator
- **THEN** it boots and the start returns once booting has completed

#### Scenario: Window shown
- **WHEN** a worktree's simulator finishes booting after a start
- **THEN** the Simulator app is opened showing that device

#### Scenario: Start of a booted simulator
- **WHEN** the user starts a worktree's simulator that is already booted
- **THEN** it isn't booted again and the Simulator app is brought up showing it

#### Scenario: Window can't be opened
- **WHEN** the simulator boots but opening the Simulator app fails
- **THEN** the start succeeds and reports the simulator as booted

#### Scenario: Stop
- **WHEN** the user stops a booted worktree simulator
- **THEN** that simulator shuts down and the others keep running

### Requirement: Wire KMP simulator tests
The system SHALL detect whether the repository's Gradle build scripts apply the Kotlin Multiplatform plugin and whether they mention `IOS_SIMULATOR_UDID`. Only in a Kotlin Multiplatform repository that doesn't mention it SHALL it show the snippet that sets the simulator test tasks' `device` from that variable, and offer a machine-wide Gradle init script that does the same for every project, installed only after explicit confirmation and removable. The init script SHALL change nothing when `IOS_SIMULATOR_UDID` isn't set.

#### Scenario: Repository already wired
- **WHEN** a `build.gradle.kts` of the repository reads `IOS_SIMULATOR_UDID`
- **THEN** no setup is suggested

#### Scenario: Not wired
- **WHEN** a build script applies the Kotlin Multiplatform plugin and no build script mentions the variable
- **THEN** the snippet is shown with the offer to install the init script instead

#### Scenario: Not a Kotlin Multiplatform repository
- **WHEN** no Gradle build script of the repository applies the Kotlin Multiplatform plugin, for example in an Xcode-only repository
- **THEN** no setup is suggested and KMP tests aren't mentioned

#### Scenario: Install after confirmation
- **WHEN** the user confirms installing the init script
- **THEN** it's written to the Gradle user home's `init.d`, and uninstalling removes it

#### Scenario: Tests use the worktree's simulator
- **WHEN** the repository is wired and an agent runs the iOS simulator tests in a worktree
- **THEN** they run on that worktree's simulator
