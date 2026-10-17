## ADDED Requirements

### Requirement: Show the current state on every visit
Each time the project configuration screen is opened, the system SHALL start from the project as saved and read the base repository again, so the provisioning patterns and the emulator and iOS simulator sections reflect what the repository declares at that moment. Edits that were neither saved nor confirmed as discarded in a previous visit SHALL NOT reappear.

#### Scenario: Provider declared after a previous visit
- **WHEN** the user opens a project's configuration, goes back, the repository then declares the emulator provider, and the user opens the configuration again
- **THEN** the screen shows the "Emulator" section

#### Scenario: Provisioning file changed between visits
- **WHEN** `.shogunai.json` gains a `copy` pattern after the configuration screen was last opened
- **THEN** opening the screen again lists the new pattern

#### Scenario: Unsaved edits from a previous visit
- **WHEN** the user changes the project name, leaves the screen discarding the change, and opens it again
- **THEN** the screen shows the saved name

### Requirement: Pick hidden files as local files
The file picker used to add local files SHALL list hidden files, such as those whose name starts with a dot, so they can be chosen like any other file.

#### Scenario: Pick a dotfile
- **WHEN** the user browses the base repository to add a local file and the repository contains `.env`
- **THEN** `.env` is listed and can be selected

## MODIFIED Requirements

### Requirement: Show whether KMP tests use the worktree's simulator
The "iOS simulator" section SHALL say whether the repository's KMP simulator tests use the worktree's simulator and, if not, show the snippet to add with a copy action and an "Install for every project on this machine" action that asks for confirmation before installing the init script. In a repository whose Gradle build scripts don't apply the Kotlin Multiplatform plugin, the section SHALL say nothing about KMP tests.

#### Scenario: KMP tests not wired
- **WHEN** the repository's build scripts apply the Kotlin Multiplatform plugin, don't mention `IOS_SIMULATOR_UDID`, and the init script isn't installed
- **THEN** the section shows the snippet, its copy action and the install action

#### Scenario: Install confirmed
- **WHEN** the user activates the install action and confirms
- **THEN** the init script is installed and the section says the tests are wired

#### Scenario: Wired
- **WHEN** a build script mentions the variable or the init script is installed
- **THEN** the section says the tests use the worktree's simulator

#### Scenario: Repository without Kotlin Multiplatform
- **WHEN** the repository declares the iOS simulator provider but none of its Gradle build scripts applies the Kotlin Multiplatform plugin, or it has no Gradle build scripts
- **THEN** the section shows neither the snippet nor a statement about KMP tests
