## MODIFIED Requirements

### Requirement: Manage a worktree's iOS simulator from the command line
`shogun simulator start|stop|status [<path>] [--device <type>] [--runtime <runtime>]` SHALL act on the simulator of the worktree containing the path or the current directory, printing its UDID and state; `--device` and `--runtime` only apply when the simulator has to be created. `status` SHALL also say whether the repository's KMP tests are wired to it, only when the repository applies the Kotlin Multiplatform plugin. In a repository without the provider the commands SHALL fail with exit code 1 and say how to declare it.

#### Scenario: Start
- **WHEN** the user runs `shogun simulator start` in a worktree
- **THEN** its simulator boots, the Simulator app shows it, the command prints its UDID and `booted`, and exits with code 0

#### Scenario: Status of an unwired repository
- **WHEN** the user runs `shogun simulator status` in a Kotlin Multiplatform repository and no build script mentions `IOS_SIMULATOR_UDID`
- **THEN** the output includes the state and says the KMP tests aren't wired, pointing to `shogun simulator gradle-init install`

#### Scenario: Status outside Kotlin Multiplatform
- **WHEN** the user runs `shogun simulator status` in a repository whose build scripts don't apply the Kotlin Multiplatform plugin
- **THEN** the output is the simulator's state alone, without any line about KMP tests

#### Scenario: Repository without the provider
- **WHEN** the user runs `shogun simulator start` in a repository without the provider
- **THEN** an error explains how to declare `"providers": { "ios-simulator": {} }` and the command exits with code 1
