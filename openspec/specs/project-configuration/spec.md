# project-configuration Specification

## Purpose
Defines how a project is created and edited: its repository paths, the secret files copied into each worktree and the agent launch command.

## Requirements

### Requirement: Create a project
The system SHALL let the user create a new project by entering a name, base repository path, worktrees root path, and a list of secret files, matching the fields of `ProjectConfig`. Required fields (name, base repository path) SHALL show inline validation feedback next to the field itself, not only a disabled submit button.

#### Scenario: Successful project creation
- **WHEN** the user fills in name, base repository path, and worktrees root, and submits the form
- **THEN** the app saves a new `Project` via `ProjectRepository` and navigates to the worktree management screen for it

#### Scenario: Required field missing
- **WHEN** the user submits the form without a name or without a base repository path
- **THEN** the app shows a validation error and does not save the project

#### Scenario: Required field shows inline feedback
- **WHEN** the "Name" or "Base repository path" field is blank
- **THEN** the app marks that field with an error state and a supporting message explaining it is required, in addition to disabling "Save"

### Requirement: Edit an existing project
The system SHALL let the user edit an existing project's configuration from the project configuration screen.

#### Scenario: Successful edit
- **WHEN** the user opens an existing project's configuration, changes a field, and submits
- **THEN** the app persists the updated `ProjectConfig` for that project's id and navigates back to the worktree management screen

### Requirement: Add or remove secret files
The system SHALL let the user add or remove entries in the project's `secretFiles` list within the configuration form, shown as "Local files": exact paths, stored only on this machine, copied into each new worktree in addition to what the repository declares. The system SHALL NOT add a file name that is already present in the list.

#### Scenario: Add a secret file entry
- **WHEN** the user adds a file name to the secret files list and saves
- **THEN** the saved `ProjectConfig.secretFiles` includes the new entry

#### Scenario: Remove a secret file entry
- **WHEN** the user removes a file name from the secret files list and saves
- **THEN** the saved `ProjectConfig.secretFiles` no longer includes that entry

#### Scenario: Duplicate secret file entry is ignored
- **WHEN** the user adds a file name that is already present in the secret files list
- **THEN** the list is unchanged and no duplicate entry is added

#### Scenario: Existing projects keep their files
- **WHEN** the app loads a project saved by an earlier version with secret files
- **THEN** those entries appear as local files and are still copied into new worktrees

### Requirement: Configure the agent launch command
The system SHALL let the user set the project's `AgentLaunchConfig` from the project configuration screen: which agent command to run (default `claude`) and whether to wrap it with Headroom's `headroom wrap` context compressor (default enabled).

#### Scenario: Default is Headroom-wrapped claude
- **WHEN** the user creates a new project without touching the agent launch fields
- **THEN** the saved `ProjectConfig.agentLaunchConfig` has `agentCommand = "claude"` and `useHeadroom = true`

#### Scenario: Disable Headroom wrapping
- **WHEN** the user turns off the Headroom toggle in the configuration form and saves
- **THEN** the saved `ProjectConfig.agentLaunchConfig.useHeadroom` is `false`

#### Scenario: Use a different agent command
- **WHEN** the user changes the agent command field to a different value (e.g. a custom CLI or wrapper script) and saves
- **THEN** the saved `ProjectConfig.agentLaunchConfig.agentCommand` reflects that value

#### Scenario: Existing projects without a saved agent config default to Headroom-wrapped claude
- **WHEN** the app loads a `ProjectConfig` persisted before this feature existed (no `agentLaunchConfig` in the stored JSON)
- **THEN** the app treats it as `agentCommand = "claude"` and `useHeadroom = true` instead of failing to deserialize the project

### Requirement: Paths follow the base repository when it moves
The system SHALL keep secret files and the worktrees root pointing to the right place when the user changes the base repository path of a project (e.g. because the repository was moved on disk).

#### Scenario: Secret files follow the base repository
- **WHEN** a project has secret files inside its base repository (stored relative, or as absolute paths inside it) and the user changes the base repository path and saves
- **THEN** the saved `ProjectConfig.secretFiles` are relative to the base repository, so they resolve inside the new base repository path

#### Scenario: Secret files outside the base repository are kept
- **WHEN** a secret file entry is an absolute path outside the base repository
- **THEN** the entry is kept unchanged when the base repository path changes

#### Scenario: Worktrees root tied to the base repository follows it
- **WHEN** the worktrees root is inside the base repository, is its parent folder, or is a sibling folder, and the user changes the base repository path without editing the worktrees root
- **THEN** the worktrees root is updated to keep the same relative location with respect to the new base repository path

#### Scenario: Independent or manually edited worktrees root is kept
- **WHEN** the worktrees root is elsewhere (e.g. another disk), or the user has edited it by hand in the form
- **THEN** changing the base repository path does not modify the worktrees root

### Requirement: Confirm before discarding unsaved changes
The system SHALL ask for confirmation before discarding unsaved edits when the user cancels out of the project configuration form, if any field differs from its initial value.

#### Scenario: Cancel with unsaved changes
- **WHEN** the user has changed at least one field and activates "Cancel"
- **THEN** the app shows a confirmation dialog before discarding the changes and returning to the previous screen

#### Scenario: Cancel without changes
- **WHEN** the user activates "Cancel" without having changed any field
- **THEN** the app returns to the previous screen immediately without showing a confirmation dialog

### Requirement: Configure the project's main branch
The system SHALL let the user set an optional main branch on the project configuration screen, used to compare and mark as merged the project's worktrees. Leaving it empty means it is detected from the repository. Surrounding whitespace is trimmed and an empty value is stored as not set.

#### Scenario: Default is detected
- **WHEN** the user creates a new project without filling the main branch field
- **THEN** the saved project has no main branch set, so it is detected from the repository

#### Scenario: Set a main branch
- **WHEN** the user enters `develop` in the main branch field and saves
- **THEN** the saved project's main branch is `develop`

#### Scenario: Clear the main branch
- **WHEN** the user empties the main branch field of a project that had one, or leaves only whitespace, and saves
- **THEN** the saved project has no main branch set

#### Scenario: Existing projects without a main branch
- **WHEN** the app loads a project stored before this field existed
- **THEN** the project loads normally with no main branch set

#### Scenario: Changing the main branch counts as an unsaved change
- **WHEN** the user edits the main branch field and then activates "Cancel"
- **THEN** the app asks for confirmation before discarding the change

### Requirement: Show the repository's provisioning
The project configuration screen SHALL show, read-only, the patterns the base repository declares in `.shogunai.json` and in `.worktreeinclude`, each with the file it comes from, and SHALL refresh them when the base repository path changes. When `.shogunai.json` can't be read, the screen SHALL say so instead of listing its patterns.

#### Scenario: Repository with both files
- **WHEN** the user opens the configuration of a project whose repository has `.shogunai.json` with `local.properties` and `.worktreeinclude` with `.env`
- **THEN** the screen lists `local.properties` from `.shogunai.json` and `.env` from `.worktreeinclude`

#### Scenario: Repository without provisioning files
- **WHEN** the base repository has neither file
- **THEN** the screen says the repository declares nothing to copy

#### Scenario: Base repository path changes
- **WHEN** the user changes the base repository path in the form to another repository
- **THEN** the listed patterns are those of the new repository

#### Scenario: Unreadable repository file
- **WHEN** the repository's `.shogunai.json` is malformed
- **THEN** the screen shows that the file can't be read

### Requirement: Move local files into the repository
The project configuration screen SHALL offer to move the local files that are inside the base repository into `.shogunai.json`, as patterns anchored to the repository root. The move SHALL take effect when the user saves; the repository file SHALL NOT be written otherwise.

#### Scenario: Move relative entries
- **WHEN** a project has the local files `local.properties`, `app/google-services.json` and `/Users/me/keys/release.jks` (outside the repository), the user activates "Move to .shogunai.json" and saves
- **THEN** `.shogunai.json` contains the patterns `/local.properties` and `/app/google-services.json` in `copy`, and the project's local files only contain `/Users/me/keys/release.jks`

#### Scenario: Existing repository file is extended
- **WHEN** `.shogunai.json` already exists with patterns or other fields and the user moves local files into it
- **THEN** the new patterns are appended without duplicates and the existing patterns and fields are kept

#### Scenario: Cancel after moving
- **WHEN** the user activates "Move to .shogunai.json" and then cancels the form
- **THEN** the app asks for confirmation as with any unsaved change, and if confirmed neither `.shogunai.json` nor the project change

#### Scenario: Repository file can't be written
- **WHEN** the user saves after moving local files and writing `.shogunai.json` fails
- **THEN** the app shows an error, keeps the form open, and the project's local files are not changed

#### Scenario: Unreadable repository file blocks the move
- **WHEN** `.shogunai.json` exists but is malformed
- **THEN** "Move to .shogunai.json" is not available, so the file is never overwritten

#### Scenario: Nothing to move
- **WHEN** the project has no local files inside the base repository
- **THEN** "Move to .shogunai.json" is not available

### Requirement: Configure the emulator on this machine
For a project whose repository declares the emulator provider, the configuration screen SHALL offer an "Emulator" section with the AVD to use, chosen from the AVDs the SDK lists or left automatic, and an option to run it without a window. These settings SHALL be stored with the project on this machine, not in the repository, and SHALL NOT appear for other projects.

#### Scenario: Repository with the provider
- **WHEN** the user edits a project whose repository declares the emulator provider
- **THEN** an "Emulator" section offers "Automatic" or one of the machine's AVDs, and a "Run without a window" option

#### Scenario: Repository without the provider
- **WHEN** the user edits a project whose repository doesn't declare it
- **THEN** no emulator section appears and no Android tool runs

#### Scenario: SDK not found
- **WHEN** the repository declares the provider but no SDK is found
- **THEN** the section says the SDK wasn't found, names where it was looked for, and still lets the user save the project

#### Scenario: Settings saved per machine
- **WHEN** the user chooses `Medium_Phone_API_35` without a window and saves
- **THEN** the project keeps both settings and `.shogunai.json` is unchanged

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
