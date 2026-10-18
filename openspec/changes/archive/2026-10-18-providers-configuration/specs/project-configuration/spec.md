# Spec Delta

## ADDED Requirements

### Requirement: Turn repository providers on and off
The project settings SHALL show a "Repository configuration" section with a switch for each provider the platform supports: Gradle and Android emulator everywhere, and iOS simulator and Xcode on macOS. Each switch SHALL reflect whether `.shogunai.json` declares the provider. Changing a switch SHALL count as an unsaved change and take effect in the repository when the user saves.

#### Scenario: Turn Gradle on
- **WHEN** the repository declares no provider, the user turns on Gradle and saves
- **THEN** `.shogunai.json` declares `"providers": { "gradle": {} }`

#### Scenario: Turn a provider off
- **WHEN** the repository declares the emulator provider, the user turns it off and saves
- **THEN** `.shogunai.json` no longer contains `providers.emulator`, and its other providers are kept

### Requirement: Edit provider options
When a provider is on, the section SHALL show its options: Gradle's daemon heap, idle timeout and Kotlin daemon heap, the emulator's preferred AVD, the simulator's preferred device and runtime, and Xcode's compilation caching. An option left empty SHALL be omitted from the file. Invalid values SHALL be flagged next to the field and block saving.

#### Scenario: Gradle settings
- **WHEN** the user turns on Gradle, enters `2g` as daemon heap and `30` as idle timeout, and saves
- **THEN** `providers.gradle` is `{ "daemonMaxHeap": "2g", "idleTimeoutMinutes": 30 }`

#### Scenario: Invalid memory size
- **WHEN** the user enters `2 GB` as daemon heap
- **THEN** the field explains the expected format (for example `2g` or `1024m`) and saving is blocked until it's fixed

### Requirement: Edit the worktree environment
The section SHALL list the variables of `env` with their name and template, let the user add, edit and remove them, and edit the environment file path. Names SHALL follow the variable name rule, and the environment file SHALL be a relative path inside the worktree. The section SHALL explain the `${port}`, `${port:key}` and `${worktree}` templates.

#### Scenario: Add a port variable
- **WHEN** the user adds `PORT` with the template `${port}`, sets the environment file to `.env.shogunai` and saves
- **THEN** `.shogunai.json` contains `"env": { "PORT": "${port}" }` and `"envFile": ".env.shogunai"`

#### Scenario: Invalid variable name
- **WHEN** the user enters `1PORT` as a name
- **THEN** the field explains that names use letters, digits and `_`, not starting with a digit, and saving is blocked

#### Scenario: Environment file outside the worktree
- **WHEN** the user enters `../shared.env` as the environment file
- **THEN** the field explains it must be a path inside the worktree, and saving is blocked

### Requirement: Preview the repository file before its first write
Before the first save that writes `.shogunai.json` in a repository during this settings visit, the app SHALL show the resulting file and explain that it belongs to the repository and should be committed, and SHALL write it only if the user confirms.

#### Scenario: Confirm the preview
- **WHEN** the user turns on Gradle and saves
- **THEN** a dialog shows the resulting `.shogunai.json`, and the file is written when the user confirms

#### Scenario: Cancel the preview
- **WHEN** the user cancels the preview dialog
- **THEN** neither `.shogunai.json` nor the project is saved, and the edits stay in the form

### Requirement: Unreadable repository file blocks editing
When `.shogunai.json` exists but can't be read, the "Repository configuration" section SHALL say so with the reason and SHALL NOT offer any control that writes the file.

#### Scenario: Malformed file
- **WHEN** the repository's `.shogunai.json` has a syntax error
- **THEN** the section shows that the file can't be read and why, and no switch or field is editable

### Requirement: Machine-only settings are labeled
The emulator and iOS simulator settings stored with the project on this machine SHALL be labeled as applying to this machine only, distinct from the repository configuration.

#### Scenario: Two kinds of emulator settings
- **WHEN** the emulator provider is on and the user looks at the emulator settings
- **THEN** the preferred AVD is shown under "Repository configuration", and the AVD to use and headless mode are shown under the emulator section labeled "this machine only"
