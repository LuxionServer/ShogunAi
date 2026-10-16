# Spec Delta

## ADDED Requirements

### Requirement: Configure the iOS simulator on this machine
For a project whose repository declares the iOS simulator provider, on macOS, the configuration screen SHALL offer an "iOS simulator" section with the device type and runtime, each chosen from the ones installed or left automatic, stored with the project on this machine and never in the repository. It SHALL NOT appear for other projects.

#### Scenario: Repository with the provider
- **WHEN** the user edits a project whose repository declares the provider
- **THEN** the "iOS simulator" section offers "Automatic" or each installed iPhone and iPad type, and "Automatic" or each installed iOS runtime

#### Scenario: Repository without the provider
- **WHEN** the repository doesn't declare it
- **THEN** no iOS section appears and no Apple tool runs

#### Scenario: Settings saved per machine
- **WHEN** the user chooses `iPhone 16 Pro` and `iOS 18.5` and saves
- **THEN** the project keeps both settings and `.shogunai.json` is unchanged

### Requirement: Show whether KMP tests use the worktree's simulator
The "iOS simulator" section SHALL say whether the repository's KMP simulator tests use the worktree's simulator and, if not, show the snippet to add with a copy action and an "Install for every project on this machine" action that asks for confirmation before installing the init script.

#### Scenario: KMP tests not wired
- **WHEN** the repository's build scripts don't mention `IOS_SIMULATOR_UDID` and the init script isn't installed
- **THEN** the section shows the snippet, its copy action and the install action

#### Scenario: Install confirmed
- **WHEN** the user activates the install action and confirms
- **THEN** the init script is installed and the section says the tests are wired

#### Scenario: Wired
- **WHEN** a build script mentions the variable or the init script is installed
- **THEN** the section says the tests use the worktree's simulator
