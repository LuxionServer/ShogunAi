# ios-simulator-provider Specification

## Purpose
Defines the iOS simulator provider: an opt-in provider, for macOS, that gives each worktree of a KMP or iOS repository its own iOS simulator, exports its UDID, boots and shuts it down on demand, deletes it with the worktree, and wires KMP's simulator tests to it, without affecting repositories that don't declare it.

## Requirements

### Requirement: Opt-in iOS simulator provider
Everything the iOS simulator provider does SHALL apply only to repositories whose `.shogunai.json` declares `"providers": { "ios-simulator": { … } }`, and only on macOS. Otherwise the system SHALL NOT run `xcrun`, create simulators, add `IOS_SIMULATOR_UDID`, nor show simulator settings, states or actions.

#### Scenario: Repository without the provider
- **WHEN** a repository doesn't declare the provider and its worktrees, configuration or environment are used
- **THEN** no simulator information or action appears, no Apple tool runs, and the environment has no `IOS_SIMULATOR_UDID`

#### Scenario: Not macOS
- **WHEN** the repository declares the provider and ShogunAi runs on Linux or Windows
- **THEN** the environment has no `IOS_SIMULATOR_UDID`, nothing iOS-related is shown, and the simulator commands fail with an error saying they need macOS

### Requirement: Choose the device type and runtime
The system SHALL choose a worktree simulator's device type and runtime in this order: the ones given for that operation, the project's machine settings, the repository's preferences if they're available on the machine, and a default of the newest installed iOS runtime with the newest iPhone device type it supports. No iOS runtime installed SHALL fail with an error that says how to install one.

#### Scenario: Machine setting wins over the repository's preference
- **WHEN** the repository prefers `iPhone 16` and the machine setting is `iPhone 16 Pro`
- **THEN** the worktree's simulator is an `iPhone 16 Pro`

#### Scenario: Preference not available here
- **WHEN** the repository prefers runtime `iOS 17.5` and the machine only has iOS 18.5 and 26.3
- **THEN** the default runtime, iOS 26.3, is used

#### Scenario: No runtime
- **WHEN** no iOS simulator runtime is installed
- **THEN** creating the simulator fails with an error suggesting to install one from Xcode's Components settings

### Requirement: One simulator per worktree
For a repository that declares the provider, the system SHALL create a new simulator for a worktree, never cloning an existing one, the first time the worktree's environment is resolved with reservation, named `ShogunAi · <repository folder> · <worktree folder>`, keep its UDID with the worktree's reservations, and add `IOS_SIMULATOR_UDID=<UDID>` to the worktree's environment.

#### Scenario: Created with the environment
- **WHEN** a worktree of a repository with the provider is created
- **THEN** a new simulator named `ShogunAi · main-repo · TASK-123` exists and the worktree's environment contains its UDID

#### Scenario: Two worktrees
- **WHEN** two worktrees resolve their environment
- **THEN** each has its own simulator with a different UDID

#### Scenario: Stable
- **WHEN** a worktree's environment is resolved again
- **THEN** the same simulator and UDID are used

### Requirement: Recreate a lost simulator and honour a declared UDID
A recorded simulator that no longer exists SHALL be created again the next time the worktree's environment is resolved with reservation. A variable named `IOS_SIMULATOR_UDID` declared in `env` SHALL take precedence over the provider's.

#### Scenario: Deleted outside ShogunAi
- **WHEN** a worktree's simulator was deleted in Xcode and its environment is resolved again
- **THEN** a new simulator is created and its UDID replaces the old one

#### Scenario: Declared variable wins
- **WHEN** `env` declares `IOS_SIMULATOR_UDID`
- **THEN** the worktree's environment uses the declared value

### Requirement: Boot, shut down and report a worktree's simulator
Starting SHALL boot the worktree's simulator and wait until it has finished booting or a timeout passes; starting a booted one SHALL succeed without changes. Once booted, starting SHALL open Xcode's simulator viewer (the Simulator app, or Device Hub where Xcode no longer ships Simulator) showing that device, best effort: if the window can't be opened, the start still succeeds. Stopping SHALL shut it down, and stopping a shut-down one SHALL succeed without changes. The state SHALL be reported as shut down, booting or booted.

#### Scenario: Start and wait
- **WHEN** the user starts a worktree's shut-down simulator
- **THEN** it boots and the start returns once booting has completed

#### Scenario: Window shown
- **WHEN** a worktree's simulator finishes booting after a start
- **THEN** Xcode's simulator viewer is opened showing that device

#### Scenario: Start of a booted simulator
- **WHEN** the user starts a worktree's simulator that is already booted
- **THEN** it isn't booted again and Xcode's simulator viewer is brought up showing it

#### Scenario: Xcode without the Simulator app
- **WHEN** a simulator boots on a machine whose Xcode ships Device Hub instead of the Simulator app
- **THEN** Device Hub is opened, listing the worktree's simulator

#### Scenario: Window can't be opened
- **WHEN** the simulator boots but opening Xcode's simulator viewer fails
- **THEN** the start succeeds and reports the simulator as booted

#### Scenario: Stop
- **WHEN** the user stops a booted worktree simulator
- **THEN** that simulator shuts down and the others keep running

### Requirement: Delete simulators with their worktrees
The system SHALL delete a worktree's simulator when the cleanup removes the worktree and the user chose to. It SHALL treat as orphaned any simulator whose name starts with `ShogunAi · ` and whose UDID no worktree on the machine has recorded, and offer to delete orphans in the cleanup.

#### Scenario: Removed worktree
- **WHEN** the cleanup removes a worktree with a simulator and the option is on
- **THEN** that simulator is deleted, freeing its disk

#### Scenario: Worktree removed by another tool
- **WHEN** a worktree with a simulator was removed with `git worktree remove`
- **THEN** its simulator is reported as orphaned and the cleanup can delete it

#### Scenario: Simulators not created by ShogunAi
- **WHEN** the machine has simulators whose names don't start with `ShogunAi · `
- **THEN** they're never reported as orphaned nor deleted

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
