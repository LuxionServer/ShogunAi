# Spec Delta

## ADDED Requirements

### Requirement: Manage a worktree's iOS simulator from the command line
`shogun simulator start|stop|status [<path>] [--device <type>] [--runtime <runtime>]` SHALL act on the simulator of the worktree containing the path or the current directory, printing its UDID and state; `--device` and `--runtime` only apply when the simulator has to be created. `status` SHALL also say whether the repository's KMP tests are wired to it. In a repository without the provider the commands SHALL fail with exit code 1 and say how to declare it.

#### Scenario: Start
- **WHEN** the user runs `shogun simulator start` in a worktree
- **THEN** its simulator boots, the command prints its UDID and `booted`, and exits with code 0

#### Scenario: Status of an unwired repository
- **WHEN** the user runs `shogun simulator status` and no build script mentions `IOS_SIMULATOR_UDID`
- **THEN** the output includes the state and says the KMP tests aren't wired, pointing to `shogun simulator gradle-init install`

#### Scenario: Repository without the provider
- **WHEN** the user runs `shogun simulator start` in a repository without the provider
- **THEN** an error explains how to declare `"providers": { "ios-simulator": {} }` and the command exits with code 1

### Requirement: Install the KMP init script from the command line
`shogun simulator gradle-init install` SHALL show what it will write and where and ask for confirmation, unless `--yes` is given, before writing the init script to the Gradle user home's `init.d`; `uninstall` SHALL remove it. Without confirmation nothing SHALL be written.

#### Scenario: Confirmed install
- **WHEN** the user runs `shogun simulator gradle-init install --yes`
- **THEN** the init script is written and its path is printed

#### Scenario: Declined
- **WHEN** the user runs `shogun simulator gradle-init install` and answers no
- **THEN** nothing is written and the command exits with code 0

### Requirement: Delete simulators when cleaning up from the command line
With `--yes`, `shogun clean --delete-simulators` SHALL delete the simulators of the removed worktrees and the orphaned ShogunAi simulators, and without `--yes` list them. Without the option no simulator SHALL be deleted.

#### Scenario: Simulators deleted
- **WHEN** the user runs `shogun clean --yes --delete-simulators` and a removed worktree had a simulator
- **THEN** it's deleted and the output says so

### Requirement: Tell Claude about its iOS simulator
When the repository declares the iOS simulator provider, the `shogun hook session-start` context line SHALL include `IOS_SIMULATOR_UDID`, how to use it with `xcodebuild -destination`, and that `shogun simulator start` boots it.

#### Scenario: Context with the simulator
- **WHEN** a Claude Code session starts in a worktree with a simulator
- **THEN** the context line includes its UDID, `-destination "id=$IOS_SIMULATOR_UDID"` and `shogun simulator start`
