## MODIFIED Requirements

### Requirement: Tell Claude how to start its emulator
When the repository declares the emulator provider, the context line printed by `shogun hook session-start` SHALL also say that the worktree's emulator is `ANDROID_SERIAL` and that `shogun emulator start` starts it, and warn that the adb server is shared with other worktrees' agents, so the agent must not restart it nor pick a device other than `ANDROID_SERIAL`.

#### Scenario: Context with the emulator
- **WHEN** a Claude Code session starts in a worktree whose serial is `emulator-5556`
- **THEN** the context line includes `ANDROID_SERIAL=emulator-5556` and mentions `shogun emulator start`

#### Scenario: Shared adb server warning
- **WHEN** a Claude Code session starts in a worktree of a repository that declares the emulator provider
- **THEN** the context line says not to run `adb kill-server` or `adb start-server` and not to pass `-s`, `-e` or `-d` to adb

### Requirement: Manage a worktree's iOS simulator from the command line
`shogun simulator start|stop|status [<path>] [--device <type>] [--runtime <runtime>]` SHALL act on the simulator of the worktree containing the path or the current directory, printing its UDID and state; `--device` and `--runtime` only apply when the simulator has to be created. `status` SHALL also say whether the repository's KMP tests are wired to it, only when the repository applies the Kotlin Multiplatform plugin. In a repository without the provider the commands SHALL fail with exit code 1 and say how to declare it.

#### Scenario: Start
- **WHEN** the user runs `shogun simulator start` in a worktree
- **THEN** its simulator boots, Xcode's simulator viewer shows it, the command prints its UDID and `booted`, and exits with code 0

#### Scenario: Status of an unwired repository
- **WHEN** the user runs `shogun simulator status` in a Kotlin Multiplatform repository and no build script mentions `IOS_SIMULATOR_UDID`
- **THEN** the output includes the state and says the KMP tests aren't wired, pointing to `shogun simulator gradle-init install`

#### Scenario: Status outside Kotlin Multiplatform
- **WHEN** the user runs `shogun simulator status` in a repository whose build scripts don't apply the Kotlin Multiplatform plugin
- **THEN** the output is the simulator's state alone, without any line about KMP tests

#### Scenario: Repository without the provider
- **WHEN** the user runs `shogun simulator start` in a repository without the provider
- **THEN** an error explains how to declare `"providers": { "ios-simulator": {} }` and the command exits with code 1
