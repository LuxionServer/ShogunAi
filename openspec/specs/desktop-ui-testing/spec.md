# desktop-ui-testing Specification

## Purpose
Headless Compose UI tests for the reusable selectors and the creation form guard, run by the regular test task.

## Requirements

### Requirement: Reusable selectors are covered by automated UI tests
The desktop app SHALL have Compose UI tests, run by `./gradlew test`, for `DropdownSelector` and `SegmentedSelector`.

#### Scenario: Disabled dropdown option ignores selection
- **WHEN** a UI test performs a click semantics action on a disabled option of an open `DropdownSelector`
- **THEN** the selection callback is not invoked and the menu stays open

#### Scenario: Enabled dropdown option selects and closes
- **WHEN** a UI test clicks an enabled option of an open `DropdownSelector`
- **THEN** the selection callback receives that option and the menu closes

#### Scenario: Segmented labels are rendered in full
- **WHEN** a UI test renders a `SegmentedSelector` with long labels
- **THEN** every label's text is displayed without truncation

### Requirement: Creation form guard is covered by an automated UI test
The "Create worktree" action in "Existing branch" mode SHALL be verified by a UI test to be enabled only for an available branch.

#### Scenario: Unavailable branch keeps the action disabled
- **WHEN** the selected existing branch already has a worktree
- **THEN** the "Create worktree" action is disabled

#### Scenario: Available branch enables the action
- **WHEN** the selected existing branch has no worktree
- **THEN** the "Create worktree" action is enabled

### Requirement: Button labels are covered by automated UI tests
The desktop app SHALL have Compose UI tests, run by `./gradlew test`, that render the shared buttons and the screens' action rows with the longest Spanish labels in a narrow width and check that every label is on one line.

#### Scenario: Shared buttons with long labels
- **WHEN** a UI test renders each shared button with a long Spanish label in a narrow container
- **THEN** the label's text layout has a single line and isn't truncated

#### Scenario: Dialog actions in Spanish
- **WHEN** a UI test renders the removal and cleanup dialogs in Spanish
- **THEN** every action's label has a single line

### Requirement: Shared form and state components are covered by automated UI tests
The desktop app SHALL have Compose UI tests, run by `./gradlew test`, for the shared field, dialog, empty state, error state, placeholder rows, in-place confirmation and disabled-reason components.

#### Scenario: Field shows error instead of help
- **WHEN** a UI test renders a field with help text and an error
- **THEN** the label and the error are displayed and the help text isn't

#### Scenario: Dialog title is a heading
- **WHEN** a UI test renders a dialog
- **THEN** its title node has heading semantics and the footer shows the dismiss action before the confirm action

#### Scenario: Error state folds the raw message
- **WHEN** a UI test renders an error state with a raw message
- **THEN** the raw message isn't displayed until "Technical details" is clicked

#### Scenario: In-place confirmation reverts
- **WHEN** a UI test clicks a copy control and advances the clock by 1.5 seconds
- **THEN** the control shows "Copied" right after the click and its original label afterwards

#### Scenario: Disabled reason is exposed
- **WHEN** a UI test renders a disabled control with a reason
- **THEN** the control is disabled and its reason is shown when the pointer rests on it

### Requirement: Notice grouping and headings are covered by automated tests
The grouping of notices SHALL be covered by unit tests, and the headings of views, group headers, form sections and dialogs by UI tests.

#### Scenario: Grouping by kind and source
- **WHEN** a test groups notices of two kinds from one source and one kind from another source
- **THEN** it gets one group per kind and source, each with its count and its notices in order

#### Scenario: Headings in the worktree view
- **WHEN** a UI test renders the worktree view header and a project group header
- **THEN** both titles have heading semantics

### Requirement: Row and inspector components are covered by automated tests
The desktop app SHALL have tests, run by `./gradlew test`, for the shared list row, the inspector section, the property list and the agent indicator, for the mapping of every source's agent state to the shared vocabulary, and for the order of the worktree detail's sections.

#### Scenario: Compact columns
- **WHEN** a UI test renders a row in compact mode with a hidable column and a shrinking column
- **THEN** the hidable column isn't displayed and the shrinking column has its compact width

#### Scenario: Section states
- **WHEN** a UI test renders an inspector section as loading, empty, with content and not applicable
- **THEN** it shows placeholder rows, the empty sentence, the content, and nothing at all, and its label has heading semantics in the first three

#### Scenario: Property copy
- **WHEN** a UI test activates a property's copy action
- **THEN** the value is handed to the copy callback and the action shows "Copied"

#### Scenario: State vocabulary
- **WHEN** a unit test maps blocked, working, done, idle and unknown states from Herdr, Orca and Claude Code hooks
- **THEN** each maps to needs you, working, done, idle and running, and the tooltip names the source

#### Scenario: Inspector order
- **WHEN** a UI test renders the detail of a worktree with status, an agent, ports, an emulator and provisioning
- **THEN** the section headings appear in the order Status, Agent, Environment, Resources, Provisioning

### Requirement: Desktop flows are covered by end-to-end tests
The desktop app SHALL have end-to-end tests, run by `./gradlew test` on every operating system CI covers, that drive the main window's shell through its interface with the app's real wiring, against real Git repositories in temporary folders.

#### Scenario: Guided tour
- **WHEN** a test starts the tour from the first-launch state and goes through it
- **THEN** the sample project and its worktrees are shown, and ending the tour removes the project and the sample's folder

#### Scenario: Adding a project
- **WHEN** a test adds a project from the interface choosing a real repository's folder and declaring a local file
- **THEN** the project is listed in the sidebar and stored with that local file

#### Scenario: Creating a worktree with local files
- **WHEN** a test creates a worktree from the board's dialog for a project that declares local files
- **THEN** the branch exists in the repository, the worktree's folder exists and each local file is copied into it with its content

#### Scenario: Claude Code hooks section
- **WHEN** a test installs the hooks from the Claude Code hooks section with a `shogun` launcher on the `PATH`, and then removes them
- **THEN** Claude Code's user settings file contains ShogunAi's hooks after the install and none after the removal, and the section shows each state

#### Scenario: Guided tour with the command installed
- **WHEN** a test starts the tour with a `shogun` launcher on the `PATH`
- **THEN** the tour creates the sample project without showing its command step

#### Scenario: Guided tour without the command
- **WHEN** a test starts the tour with no `shogun` launcher
- **THEN** the tour shows its command step saying the command isn't installed, and skipping it creates the sample project

#### Scenario: Claude Code hooks section without the command
- **WHEN** a test opens the Claude Code hooks section with no `shogun` launcher
- **THEN** the section says the command isn't installed and installing the hooks is disabled

### Requirement: End-to-end tests run in an isolated home
The end-to-end tests SHALL run with their own home folder, temporary folder and Git configuration, without Claude Code's configuration folder override and without the user's login shell (a test may use a fake one of its own), so they never read or write the user's `~/.shogunai`, `~/.claude` or Git settings, and they SHALL fail if they run outside that environment.

#### Scenario: Files stay in the isolated home
- **WHEN** the end-to-end tests run
- **THEN** every file they create, including the app's settings, its project catalog and Claude Code's settings, is inside the test task's folder

#### Scenario: Running outside the isolated task
- **WHEN** an end-to-end test runs without the isolated environment, for example from an IDE's default test run
- **THEN** it fails before building the app, with a message naming the task to run

### Requirement: End-to-end tests decide whether the command is installed
The desktop app's end-to-end tests SHALL give the app a host environment whose `PATH` and usual install folders contain a `shogun` launcher only when the test installs one, so the result never depends on what the machine running them has installed.

#### Scenario: Command installed on the machine
- **WHEN** the end-to-end tests run on a machine with `shogun` in `/opt/homebrew/bin` and a test doesn't install a launcher
- **THEN** the app reads the `shogun` command as not installed

### Requirement: The app opened from the Dock is covered end to end
On macOS and Linux an end-to-end test SHALL start the app with the Dock's minimal `PATH` and a login shell whose `PATH` holds the folder where `shogun` is, and SHALL check that the app doesn't report the command as outside the `PATH`.

#### Scenario: Homebrew command opened from the Dock
- **WHEN** the app is started with `PATH` `/usr/bin:/bin:/usr/sbin:/sbin`, `shogun` is in a usual install folder and the login shell's `PATH` includes that folder
- **THEN** the Claude Code hooks section says the command is available and installing the hooks is enabled, and the tour creates the sample project without showing its command step
