## MODIFIED Requirements

### Requirement: Manage a worktree's emulator from the command line
`shogun emulator start [<path>] [--avd <name>] [--no-window]`, `shogun emulator stop [<path>]` and `shogun emulator status [<path>]` SHALL act on the emulator of the worktree containing the path or the current directory, printing its serial and state, or `not acquired` when the worktree holds no emulator. `start` SHALL acquire the emulator from the pool when needed, print on standard error any emulator released to make room, and print the `export ANDROID_SERIAL=…` line to run when the emulator was just acquired. In a repository that doesn't declare the emulator provider they SHALL fail with exit code 1 and say how to declare it.

#### Scenario: Start
- **WHEN** the user runs `shogun emulator start` in a worktree whose serial is `emulator-5556`
- **THEN** the emulator boots, the command prints `emulator-5556 running` and exits with code 0

#### Scenario: First start
- **WHEN** the user runs `shogun emulator start` in a worktree that holds no emulator and the pool has room
- **THEN** a console port is reserved, the emulator boots, and the output includes its serial and `export ANDROID_SERIAL=emulator-<port>`

#### Scenario: Pool full
- **WHEN** the user runs `shogun emulator start` and the emulator pool is full with every emulator running
- **THEN** the pool's error, naming the worktrees that hold emulators, is printed on standard error and the command exits with code 1

#### Scenario: Status
- **WHEN** the user runs `shogun emulator status` and the emulator is stopped
- **THEN** it prints `emulator-5556 stopped` and exits with code 0

#### Scenario: Status before the first start
- **WHEN** the user runs `shogun emulator status` in a worktree that never started its emulator
- **THEN** it prints `not acquired` and exits with code 0

#### Scenario: Repository without the provider
- **WHEN** the user runs `shogun emulator start` in a repository without the provider
- **THEN** an error on standard error explains how to declare `"providers": { "emulator": {} }` and the command exits with code 1

### Requirement: Tell Claude how to start its emulator
When the repository declares the emulator provider, the context line printed by `shogun hook session-start` SHALL say how to get the worktree's emulator: when the worktree holds one, that it is `ANDROID_SERIAL` and that `shogun emulator start` starts it; otherwise, that `shogun emulator start` acquires and starts it and prints the `ANDROID_SERIAL` to export. It SHALL also warn that the adb server is shared with other worktrees' agents, so the agent must not restart it nor pick a device other than `ANDROID_SERIAL`.

#### Scenario: Context with the emulator
- **WHEN** a Claude Code session starts in a worktree whose serial is `emulator-5556`
- **THEN** the context line includes `ANDROID_SERIAL=emulator-5556` and mentions `shogun emulator start`

#### Scenario: Context before the first start
- **WHEN** a Claude Code session starts in a worktree that holds no emulator
- **THEN** the context line has no `ANDROID_SERIAL`, says `shogun emulator start` acquires the emulator and prints the variable to export, and nothing is reserved

#### Scenario: Shared adb server warning
- **WHEN** a Claude Code session starts in a worktree of a repository that declares the emulator provider
- **THEN** the context line says not to run `adb kill-server` or `adb start-server` and not to pass `-s`, `-e` or `-d` to adb

### Requirement: Manage a worktree's iOS simulator from the command line
`shogun simulator start|stop|status [<path>] [--device <type>] [--runtime <runtime>]` SHALL act on the simulator of the worktree containing the path or the current directory, printing its UDID and state, or `not acquired` when the worktree holds none; `--device` and `--runtime` only apply when the simulator has to be created. `start` SHALL acquire the simulator from the pool when needed, print on standard error any simulator released to make room, and print the `export IOS_SIMULATOR_UDID=…` line to run when it was just acquired. `status` SHALL also say whether the repository's KMP tests are wired to it, only when the repository applies the Kotlin Multiplatform plugin. In a repository without the provider the commands SHALL fail with exit code 1 and say how to declare it.

#### Scenario: Start
- **WHEN** the user runs `shogun simulator start` in a worktree
- **THEN** its simulator boots, Xcode's simulator viewer shows it, the command prints its UDID and `booted`, and exits with code 0

#### Scenario: First start
- **WHEN** the user runs `shogun simulator start` in a worktree that holds no simulator and the pool has room
- **THEN** the simulator is created and booted, and the output includes `export IOS_SIMULATOR_UDID=<UDID>`

#### Scenario: Status of an unwired repository
- **WHEN** the user runs `shogun simulator status` in a Kotlin Multiplatform repository and no build script mentions `IOS_SIMULATOR_UDID`
- **THEN** the output includes the state and says the KMP tests aren't wired, pointing to `shogun simulator gradle-init install`

#### Scenario: Status outside Kotlin Multiplatform
- **WHEN** the user runs `shogun simulator status` in a repository whose build scripts don't apply the Kotlin Multiplatform plugin
- **THEN** the output is the simulator's state alone, without any line about KMP tests

#### Scenario: Repository without the provider
- **WHEN** the user runs `shogun simulator start` in a repository without the provider
- **THEN** an error explains how to declare `"providers": { "ios-simulator": {} }` and the command exits with code 1

### Requirement: Tell Claude about its iOS simulator
When the repository declares the iOS simulator provider, the `shogun hook session-start` context line SHALL say how to get the worktree's simulator: when the worktree holds one, its `IOS_SIMULATOR_UDID`, how to use it with `xcodebuild -destination`, and that `shogun simulator start` boots it; otherwise, that `shogun simulator start` creates and boots it and prints the `IOS_SIMULATOR_UDID` to export.

#### Scenario: Context with the simulator
- **WHEN** a Claude Code session starts in a worktree with a simulator
- **THEN** the context line includes its UDID, `-destination "id=$IOS_SIMULATOR_UDID"` and `shogun simulator start`

#### Scenario: Context before the first start
- **WHEN** a Claude Code session starts in a worktree that holds no simulator
- **THEN** the context line says `shogun simulator start` creates it and prints the variable to export, and no simulator is created

## ADDED Requirements

### Requirement: Show and trim the resource pools from the command line
`shogun pool status` SHALL print, for each resource kind, the profile's effective limit, how many are in use, and each one's project, worktree and state. `shogun pool trim` SHALL stop idle Gradle daemons over the Gradle limit and print what it stopped and kept. Both SHALL accept `--json`.

#### Scenario: Status
- **WHEN** the limit is 2 emulators and `main-repo` · `TASK-1` holds a running one
- **THEN** `shogun pool status` prints `emulators 1/2` followed by `main-repo · TASK-1 running`

#### Scenario: Trim
- **WHEN** the Gradle limit is 2 and three idle Gradle daemons run
- **THEN** `shogun pool trim` stops the longest-idle one and prints it, and exits with code 0

#### Scenario: JSON
- **WHEN** the user runs `shogun pool status --json`
- **THEN** it prints an object with each kind's limit, usage and holders
