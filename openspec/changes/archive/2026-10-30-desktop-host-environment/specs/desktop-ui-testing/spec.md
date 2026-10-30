## MODIFIED Requirements

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

## ADDED Requirements

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
