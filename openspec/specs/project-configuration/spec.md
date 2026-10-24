# project-configuration Specification

## Purpose
Defines how a project is created and edited: its repository paths, the secret files copied into each worktree and the agent launch command.

## Requirements

### Requirement: Create a project
The system SHALL let the user create a new project by entering a name, base repository path, worktrees root path, and a list of secret files, matching the fields of `ProjectConfig`. Required fields (name, base repository path) SHALL show inline validation feedback next to the field itself once the user has edited that field or tried to save, and SHALL NOT show it on a form the user hasn't touched.

#### Scenario: Successful project creation
- **WHEN** the user fills in name, base repository path, and worktrees root, and saves
- **THEN** the app saves a new `Project` via `ProjectRepository` and shows the worktree view for it

#### Scenario: Required field missing
- **WHEN** the user tries to save without a name or without a base repository path
- **THEN** the app shows the validation error next to each missing field, moves focus to the first one, and does not save the project

#### Scenario: Required field shows inline feedback
- **WHEN** the user empties the "Name" or "Base repository path" field after editing it
- **THEN** the app marks that field with an error state and a supporting message explaining it is required

#### Scenario: Untouched new form
- **WHEN** the add-project form opens and the user hasn't edited any field or tried to save
- **THEN** no field is shown in an error state

### Requirement: Edit an existing project
The system SHALL let the user edit an existing project's configuration from the project configuration screen.

#### Scenario: Successful edit
- **WHEN** the user opens an existing project's configuration, changes a field, and submits
- **THEN** the app persists the updated `ProjectConfig` for that project's id and navigates back to the worktree management screen

### Requirement: Add or remove secret files
The system SHALL let the user add or remove entries in the project's `secretFiles` list within the configuration form, shown as "Local files": exact paths of files or folders, stored only on this machine, copied into each new worktree in addition to what the repository declares. The browse dialog SHALL allow selecting files and folders. The system SHALL NOT add an entry that is already present in the list.

#### Scenario: Add a secret file entry
- **WHEN** the user adds a file name to the secret files list and saves
- **THEN** the saved `ProjectConfig.secretFiles` includes the new entry

#### Scenario: Add a folder entry
- **WHEN** the user selects a folder inside the base repository with the browse dialog and saves
- **THEN** the saved `ProjectConfig.secretFiles` includes the folder's path relative to the base repository

#### Scenario: Remove a secret file entry
- **WHEN** the user removes a file name from the secret files list and saves
- **THEN** the saved `ProjectConfig.secretFiles` no longer includes that entry

#### Scenario: Duplicate secret file entry is ignored
- **WHEN** the user adds a file name that is already present in the secret files list
- **THEN** the list is unchanged and no duplicate entry is added

#### Scenario: Duplicate folder entry with a trailing separator is ignored
- **WHEN** the list contains `keys` and the user adds `keys/`
- **THEN** the list is unchanged and no duplicate entry is added

#### Scenario: Existing projects keep their files
- **WHEN** the app loads a project saved by an earlier version with secret files
- **THEN** those entries appear as local files and are still copied into new worktrees

### Requirement: Configure the agent launch command
The system SHALL let the user set the project's `AgentLaunchConfig` from the project configuration screen: which agent command to run (default `claude`) and whether to wrap it with Headroom's `headroom wrap` context compressor (default disabled). This first agent is the project's default agent.

#### Scenario: Default is plain claude
- **WHEN** the user creates a new project without touching the agent launch fields
- **THEN** the saved `ProjectConfig.agentLaunchConfig` has `agentCommand = "claude"` and `useHeadroom = false`

#### Scenario: Enable Headroom wrapping
- **WHEN** the user turns on the Headroom toggle in the configuration form and saves
- **THEN** the saved `ProjectConfig.agentLaunchConfig.useHeadroom` is `true`

#### Scenario: Default is Headroom-wrapped claude
- **WHEN** the app loads a project an earlier version saved with the agent launch fields untouched
- **THEN** its `ProjectConfig.agentLaunchConfig` has `agentCommand = "claude"` and `useHeadroom = true`, as when it was saved

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
The system SHALL keep secret files and folders and the worktrees root pointing to the right place when the user changes the base repository path of a project (e.g. because the repository was moved on disk).

#### Scenario: Secret files follow the base repository
- **WHEN** a project has secret files or folders inside its base repository (stored relative, or as absolute paths inside it) and the user changes the base repository path and saves
- **THEN** the saved `ProjectConfig.secretFiles` are relative to the base repository, so they resolve inside the new base repository path

#### Scenario: Secret files outside the base repository are kept
- **WHEN** a secret file or folder entry is an absolute path outside the base repository
- **THEN** the entry is kept unchanged when the base repository path changes

#### Scenario: Worktrees root tied to the base repository follows it
- **WHEN** the worktrees root is inside the base repository, is its parent folder, or is a sibling folder, and the user changes the base repository path without editing the worktrees root
- **THEN** the worktrees root is updated to keep the same relative location with respect to the new base repository path

#### Scenario: Independent or manually edited worktrees root is kept
- **WHEN** the worktrees root is elsewhere (e.g. another disk), or the user has edited it by hand in the form
- **THEN** changing the base repository path does not modify the worktrees root

### Requirement: Confirm before discarding unsaved changes
The system SHALL ask for confirmation before discarding unsaved edits whenever the user leaves the project settings while any field differs from its initial value: by selecting another entry in the sidebar, through a shortcut or menu entry that changes the view, or by closing the window. Activating "Discard" in the save bar SHALL discard the edits and return to the previous view without asking again.

#### Scenario: Cancel with unsaved changes
- **WHEN** the user has changed at least one field and activates "Discard" in the save bar
- **THEN** the app discards the changes and returns to the previous view without showing a confirmation dialog

#### Scenario: Cancel without changes
- **WHEN** the user leaves the settings without having changed any field
- **THEN** the app shows the chosen view immediately without showing a confirmation dialog

#### Scenario: Leave through the sidebar
- **WHEN** the user has changed a field and selects another project in the sidebar
- **THEN** the app asks for confirmation, shows the other project only if the user confirms, and keeps the edits otherwise

#### Scenario: Close the window
- **WHEN** the user has changed a field and closes the main window
- **THEN** the app asks for confirmation before quitting

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
The project configuration screen SHALL show, read-only, the patterns the base repository declares in `.shogunai.json` (`copy` and `link`) and in `.worktreeinclude`, each with the file it comes from and `link` patterns marked as linked, and SHALL refresh them when the base repository path changes. When the repository declares post-creation commands, the screen SHALL say how many there are and where they're listed. When `.shogunai.json` can't be read, the screen SHALL say so instead of listing its patterns.

#### Scenario: Repository with both files
- **WHEN** the user opens the configuration of a project whose repository has `.shogunai.json` with `local.properties` and `.worktreeinclude` with `.env`
- **THEN** the screen lists `local.properties` from `.shogunai.json` and `.env` from `.worktreeinclude`

#### Scenario: Linked patterns
- **WHEN** the repository's `.shogunai.json` declares `node_modules` under `link`
- **THEN** the screen lists `node_modules` from `.shogunai.json`, marked as linked

#### Scenario: Only post-creation commands
- **WHEN** the repository's `.shogunai.json` declares two post-creation commands and no patterns
- **THEN** the screen says two commands run after creation and points to the repository configuration section, and doesn't say the repository declares nothing

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

### Requirement: Settings organized in sections
The project settings SHALL be organized in sections: General (name, base repository, worktrees folder, main branch), Worktree files (the repository's provisioning and the local files), Agent, and, when the repository declares them, Android emulator and iOS simulator. On wide windows a section index SHALL let the user jump to each section.

#### Scenario: Jump to a section
- **WHEN** the user activates "Agent" in the section index
- **THEN** the view scrolls to the Agent section

#### Scenario: Repository without providers
- **WHEN** the repository declares neither the emulator nor the iOS simulator provider
- **THEN** neither section nor its index entry is shown

### Requirement: Save bar while there are unsaved changes
While any field differs from its initial value, the project settings SHALL show a save bar at the bottom of the view with "Discard" and "Save", and Cmd/Ctrl+S SHALL save. Without changes, the save bar SHALL NOT be shown, except while adding a project, where "Add project" and "Cancel" are always shown.

#### Scenario: Edit a field
- **WHEN** the user changes the agent command of an existing project
- **THEN** the save bar appears, and pressing Cmd/Ctrl+S saves the project and hides it

#### Scenario: Revert the edit
- **WHEN** the user changes a field and then restores its original value
- **THEN** the save bar disappears

### Requirement: Add a project from its repository folder
Adding a project SHALL start by choosing the repository folder. Once chosen, the form SHALL prefill the name with the folder's name, suggest a worktrees folder named `<folder>-worktrees` next to the repository, and show the main branch detected in the repository as the main branch field's placeholder. A folder that isn't a Git repository SHALL be rejected with a message, and the user SHALL be able to choose another.

#### Scenario: Choose a repository
- **WHEN** the user adds a project and chooses `~/projects/main-repo`, whose default branch is `main`
- **THEN** the form shows the name `main-repo`, the worktrees folder `~/projects/main-repo-worktrees` and `main` as the detected main branch

#### Scenario: Not a repository
- **WHEN** the user chooses a folder that isn't a Git repository
- **THEN** the app says the folder isn't a Git repository and asks for another, without opening the rest of the form

### Requirement: Drop a repository folder to add it
Dropping a folder on the main window SHALL start adding a project from that folder, as if it had been chosen in "Add project". Dropping anything else SHALL be ignored.

#### Scenario: Drop a repository
- **WHEN** the user drops `~/projects/api` on the main window
- **THEN** the add-project form opens prefilled from `~/projects/api`

#### Scenario: Drop a file
- **WHEN** the user drops a file that isn't a folder
- **THEN** nothing happens

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
The section SHALL list the variables of `env` with their name and template, let the user add, edit and remove them, and edit the environment file path. Names SHALL follow the variable name rule and SHALL NOT be reserved names, templates SHALL NOT contain line breaks, other control characters or invisible format characters, and the environment file SHALL be a relative path inside the worktree. The section SHALL explain the `${port}`, `${port:key}` and `${worktree}` templates.

#### Scenario: Add a port variable
- **WHEN** the user adds `PORT` with the template `${port}`, sets the environment file to `.env.shogunai` and saves
- **THEN** `.shogunai.json` contains `"env": { "PORT": "${port}" }` and `"envFile": ".env.shogunai"`

#### Scenario: Invalid variable name
- **WHEN** the user enters `1PORT` as a name
- **THEN** the field explains that names use letters, digits and `_`, not starting with a digit, and saving is blocked

#### Scenario: Environment file outside the worktree
- **WHEN** the user enters `../shared.env` as the environment file
- **THEN** the field explains it must be a path inside the worktree, and saving is blocked

#### Scenario: Reserved name
- **WHEN** the user enters `XDG_CONFIG_HOME` or `https_proxy` as a name
- **THEN** the field explains that the name is reserved because it changes how other tools behave, and saving is blocked

#### Scenario: Line break in a template
- **WHEN** the user pastes a template that contains a line break
- **THEN** the field explains that templates can't contain line breaks or control characters, and saving is blocked

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

### Requirement: Editing the base path keeps unsaved edits
While the user types the base repository path in project settings, the app SHALL NOT discard unsaved repository settings. It SHALL read the repository at the new path only when the path settles (after a short pause, on leaving the field or after choosing a folder), and SHALL keep the user's unsaved repository edits when the repository is the same.

#### Scenario: Fix a typo in the path
- **WHEN** the user changes the Gradle memory, then edits the base path and returns it to the same repository
- **THEN** the Gradle memory change is still there and the save bar still shows unsaved changes

#### Scenario: Different repository
- **WHEN** the user points the path to another repository that has its own `.shogunai.json`
- **THEN** the app asks whether to discard the unsaved repository edits before loading the other repository's settings

### Requirement: Validate the project form
Saving project settings SHALL require a base path that is the top folder of a Git repository, checked the same way as "Add project", a non-blank worktrees folder and a non-blank agent command, and SHALL reject a repository that another project already uses.

#### Scenario: Subfolder chosen
- **WHEN** the user chooses a subfolder of a repository with Browse in project settings
- **THEN** the form says the folder isn't the repository's top folder and can't be saved

#### Scenario: Repository already added
- **WHEN** the base path is the repository of another project
- **THEN** the form names that project and can't be saved

#### Scenario: Blank agent command
- **WHEN** the agent command is blank
- **THEN** the form says it is required and can't be saved

### Requirement: Preview again after reloading the repository file
After the repository file was reloaded from disk, for example after a "changed on disk" conflict, the next save that writes it SHALL show the preview again.

#### Scenario: Conflict, reload and save
- **WHEN** a save fails because `.shogunai.json` changed on disk, the user reloads it, edits again and saves
- **THEN** the preview dialog is shown before the file is written

### Requirement: IDE for a project on this machine
The project settings' Agent section SHALL let the user choose the IDE the project's worktrees open in on this machine, among the detected ones, or leave it on the default from Preferences. The choice SHALL be stored with the project on this machine, not in the repository.

#### Scenario: Override the IDE
- **WHEN** the user chooses Android Studio for a project and saves
- **THEN** that project's worktrees open in Android Studio while other projects keep the default IDE

#### Scenario: Overridden IDE uninstalled
- **WHEN** the IDE chosen for a project is no longer installed
- **THEN** the settings say it wasn't found, and the worktrees open in the default IDE

### Requirement: Settings title names the project
The project settings view of an existing project SHALL show a title that names the project as saved, and SHALL keep the "Add project" title while adding one.

#### Scenario: Existing project
- **WHEN** the user opens the settings of the project `main-repo`
- **THEN** the title reads "Settings · main-repo"

#### Scenario: Name edited but not saved
- **WHEN** the user changes the name field to `other-name` without saving
- **THEN** the title still names `main-repo`

### Requirement: Uninstall the KMP init script from settings
When the KMP init script is installed, project settings SHALL offer to uninstall it, with the same effect as `shogun simulator gradle-init uninstall`.

#### Scenario: Uninstall
- **WHEN** the init script is installed and the user chooses to uninstall it in project settings
- **THEN** the script is removed and the section shows that KMP tests no longer use the worktree's simulator

### Requirement: Several agents per project
The project settings' Agent section SHALL let the user add, edit, remove and reorder agents, each with a name, a command with its default arguments and the Headroom option, and choose which one is the default. Agent names SHALL be unique and non-blank within the project; an agent without a name SHALL be shown by its command's executable name. A project SHALL keep at least one agent.

#### Scenario: Add a second agent
- **WHEN** the user adds an agent named "Codex" with the command `codex --full-auto` and saves
- **THEN** the project has Claude as its default agent and Codex as a second one

#### Scenario: Make another agent the default
- **WHEN** the user activates "Make default" on Codex and saves
- **THEN** Codex is the project's default agent and Claude stays as another agent

#### Scenario: Duplicate name
- **WHEN** two agents of the project have the name "Claude"
- **THEN** saving is refused with the duplicated name marked

#### Scenario: Stored project with one agent
- **WHEN** the app loads a project saved before this change
- **THEN** it has a single agent, its stored agent command, and its settings look as before

### Requirement: Suggest the providers of the detected stacks
The "Repository configuration" section SHALL show, for the providers the platform supports and the repository doesn't declare, a suggestion for each stack detected in the repository, with the files that revealed it and an action to turn the provider on. Using it SHALL turn the switch on as an unsaved change. Nothing SHALL be written to the repository until the user saves and confirms the preview. Nothing is suggested when there is nothing to suggest.

#### Scenario: Suggestion shown
- **WHEN** the user opens the settings of a project whose repository has `pom.xml` and declares no provider
- **THEN** the section suggests the Maven provider and names `pom.xml` as the evidence

#### Scenario: Accept a suggestion
- **WHEN** the user uses the Maven suggestion
- **THEN** the Maven switch is on, the change counts as unsaved and `.shogunai.json` is unchanged until the user saves

#### Scenario: Nothing to suggest
- **WHEN** every detected stack already has its provider on
- **THEN** the section shows no suggestion

#### Scenario: Suggestion for another platform
- **WHEN** the repository has an Xcode project and the app runs on Windows
- **THEN** no iOS or Xcode suggestion is shown

### Requirement: Turn on the Maven and Docker Compose providers
The section SHALL offer a switch for the Maven and Docker Compose providers on every operating system. With Maven on it SHALL show the heap option, and with Docker Compose on it SHALL show the list of port variables, both following the rules of option editing: an empty option is omitted from the file and an invalid value is flagged and blocks saving.

#### Scenario: Maven heap
- **WHEN** the user turns on Maven, enters `1g` as heap and saves
- **THEN** `providers.maven` is `{ "mavenMaxHeap": "1g" }`

#### Scenario: Compose port variables
- **WHEN** the user turns on Docker Compose, adds `DB_PORT` and `API_PORT` and saves
- **THEN** `providers.docker-compose` is `{ "portVariables": ["DB_PORT", "API_PORT"] }`

#### Scenario: Invalid variable name
- **WHEN** the user adds `1DB` as a port variable
- **THEN** the field explains that names use letters, digits and `_`, not starting with a digit, and saving is blocked

#### Scenario: Turn a provider off
- **WHEN** the repository declares Maven, the user turns it off and saves
- **THEN** `.shogunai.json` no longer contains `providers.maven` and its other providers are kept

### Requirement: Projects saved by earlier versions keep Headroom
An agent that an earlier version stored without `useHeadroom` SHALL be read as wrapped with Headroom, since earlier versions left the field out when it was on. The system SHALL always write `useHeadroom` and the default agent, so a project saved by this version reads back the same.

#### Scenario: Agent stored without the field
- **WHEN** the app loads a project whose stored agents (the default one and the additional ones) have `agentCommand` but no `useHeadroom`
- **THEN** every one of them has `useHeadroom = true`, and the copied launch command is `headroom wrap <command>`

#### Scenario: Agent stored with Headroom off
- **WHEN** the stored agent has `useHeadroom: false`
- **THEN** it reads as `useHeadroom = false`

#### Scenario: New project round trip
- **WHEN** the user saves a new project with the default agent and the app loads it again
- **THEN** the stored JSON has the agent with `useHeadroom: false` and it reads back without Headroom

#### Scenario: Saving keeps the legacy agent's Headroom
- **WHEN** the app loads a project saved by an earlier version, the user changes its name and saves
- **THEN** the stored agent has `useHeadroom: true` written explicitly
