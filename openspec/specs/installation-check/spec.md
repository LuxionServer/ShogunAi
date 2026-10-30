# installation-check Specification

## Purpose
Lets a user, or someone helping them, check in one step that ShogunAi's installation and the tools it relies on are usable on this machine, and learn how to fix each problem, from the CLI and from the desktop app.

## Requirements

### Requirement: Run the installation check without changing anything
The system SHALL offer an installation check, as `shogun doctor` in the CLI and as "Check Installation…" in the desktop app's Help menu. It SHALL run every check in this specification, in the order listed, and SHALL NOT write, create or delete any file, install anything or start any program other than to read a version or list what is installed (Android Virtual Devices, simulator runtimes, a repository's worktrees).

#### Scenario: Nothing changes
- **WHEN** the installation check runs on a machine where the hooks are outdated and a project's local file is missing
- **THEN** both are reported and no settings file, project, worktree or local file is modified

#### Scenario: Missing multirepo folder isn't forgotten
- **WHEN** a known multirepo's folder no longer has its declaration
- **THEN** the check reports it and the multirepo stays in ShogunAi's list of known multirepos

### Requirement: Every check has a state and a fix
Each check SHALL report its state: passed, warning, failed, or not found for an optional tool. A check that didn't pass SHALL say what is wrong and the fix to apply. A failure SHALL be something that stops ShogunAi from working; a warning or a missing optional tool SHALL NOT.

#### Scenario: Passed check
- **WHEN** Git 2.45 is found
- **THEN** the Git check passes, naming the version and where Git was found, with no fix

#### Scenario: Failed check
- **WHEN** a check fails
- **THEN** its result says what is wrong and how to fix it

### Requirement: Check Git
The check SHALL find Git on the host's `PATH` and read its version. It SHALL fail when Git isn't found, when its version can't be read, or when it is older than 2.29, the oldest version with every Git command ShogunAi runs; the fix SHALL be to install or update Git.

#### Scenario: Git too old
- **WHEN** `git --version` prints `git version 2.25.1`
- **THEN** the Git check fails, naming 2.25.1 and 2.29, with the fix to update Git

#### Scenario: Git missing
- **WHEN** no `git` is found on the `PATH`
- **THEN** the Git check fails with the fix to install Git

#### Scenario: Platform suffix
- **WHEN** `git --version` prints `git version 2.39.5 (Apple Git-154)` or `git version 2.45.1.windows.1`
- **THEN** the Git check passes with version 2.39.5 or 2.45.1

### Requirement: Check where the PATH comes from
The check SHALL say where the `PATH` used to find tools comes from: the login shell (passes), the process that started ShogunAi (passes), or a fallback because the login shell couldn't be read (warning, naming the shell, with the fix to make the shell start without errors or prompts within 5 seconds).

#### Scenario: Login shell unreadable
- **WHEN** the desktop app couldn't read the login shell's `PATH`
- **THEN** the check warns that the usual install folders were used instead, naming the shell

#### Scenario: Command line
- **WHEN** `shogun doctor` runs from a terminal
- **THEN** the check passes, saying the `PATH` is the one of the process that started it

### Requirement: Check the shogun command
The check SHALL look for the `shogun` command. It SHALL pass when it is found in a folder of the `PATH`, fail when it isn't found (fix: install it from the desktop app's Settings or the installation guide), fail when it is a launcher installed by the app whose target no longer exists (fix: install it again from Settings), and warn when it was found outside the `PATH` (fix: add its folder to the `PATH`, or restart the terminal on Windows when the folder was just added).

#### Scenario: Not on the PATH
- **WHEN** `shogun` exists in `~/.local/bin` and that folder isn't on the `PATH`
- **THEN** the check warns, naming the folder, with the fix to add it to the `PATH`

#### Scenario: Broken launcher
- **WHEN** the app's launcher link for `shogun` points to an app that was deleted
- **THEN** the check fails with the fix to install the command again from Settings

### Requirement: Check Claude Code's hooks
The check SHALL read ShogunAi's hooks in Claude Code's user settings (`CLAUDE_CONFIG_DIR` or `~/.claude`). It SHALL pass when they are installed and the `shogun` they call is usable, fail when they are installed but `shogun` is missing or a broken launcher (fix: the `shogun` command's fix), warn when they are outdated or missing (fix: `shogun hooks install`), and fail when the settings file can't be parsed (fix: correct the file).

#### Scenario: Hooks call a missing launcher
- **WHEN** the hooks are installed and `shogun` isn't found
- **THEN** the hooks check fails, saying the hooks call a `shogun` that doesn't exist

#### Scenario: Hooks not installed
- **WHEN** the user settings have no ShogunAi hooks
- **THEN** the hooks check warns with the fix `shogun hooks install`

#### Scenario: Invalid settings
- **WHEN** the user settings file isn't valid JSON
- **THEN** the hooks check fails, naming the file and the parse error

### Requirement: Check the agent tools
The check SHALL look for Claude Code, Herdr and Orca where ShogunAi looks for them when it uses them. A tool that is found SHALL pass, naming where; a tool that isn't found SHALL be reported as not found, with the fix to install it, and SHALL NOT fail the check.

#### Scenario: Orca not installed
- **WHEN** Orca isn't installed
- **THEN** Orca is reported as not found with where to get it, and the check doesn't fail because of it

### Requirement: Check each project's local files
For each registered project, the check SHALL fail when its base folder doesn't exist (fix: restore it or remove the project), and otherwise SHALL check the local files that creating a worktree would copy or link, with the same reasons and fixes as the check before creating a worktree. A project whose files are all available SHALL pass, naming how many files were checked.

#### Scenario: Broken link in a project
- **WHEN** a project's `.shogunai.json` copies `*.properties` and its `keys.properties` is a link to a file that doesn't exist
- **THEN** that project's check fails, naming `keys.properties`, the link's missing target, and the fix to restore it, replace the link or stop matching it

#### Scenario: No projects
- **WHEN** no project is registered
- **THEN** no project check is reported

### Requirement: shogun doctor output
`shogun doctor` SHALL print one line per check with its state and summary and, under a check that didn't pass, its fix. With `--json` it SHALL print every check with its id, state, summary and fix. It SHALL exit with 1 when any check failed and with 0 otherwise.

#### Scenario: Warnings only
- **WHEN** the only problem is that the hooks aren't installed
- **THEN** `shogun doctor` prints the warning with its fix and exits with 0

#### Scenario: A failure
- **WHEN** Git isn't found
- **THEN** `shogun doctor` exits with 1

#### Scenario: JSON
- **WHEN** `shogun doctor --json` runs
- **THEN** it prints an object with a `checks` array, each with `id`, `state`, `summary` and `fix`, and `failed` true when any check failed

### Requirement: Check Installation in the desktop app
The desktop app's "Check Installation…" SHALL open a window that runs the check, shows each result with its state, summary and fix in the selected language, offers to run it again, and offers to copy the diagnostic report.

#### Scenario: Open the window
- **WHEN** the user chooses Help › Check Installation… with Spanish selected
- **THEN** a window shows each check's state, summary and fix in Spanish

#### Scenario: Run again
- **WHEN** the user fixes the hooks and chooses to check again
- **THEN** the hooks check passes in the window

### Requirement: Check the tools of each project's providers
For each registered project whose base folder exists, the check SHALL read the providers its repository declares and report one check per tool they need: the Gradle wrapper or `gradle` for `gradle`, the Maven wrapper or `mvn` for `maven`, the Android SDK and an AVD for `emulator`, the simulators and an iOS runtime for `ios-simulator`, Xcode for `xcode` and `pod` when it declares CocoaPods, `docker compose` for `docker-compose`, and `flutter` (or `fvm`) for `flutter`.

#### Scenario: Tools found
- **WHEN** a project declares `docker-compose` and `docker compose version` runs
- **THEN** a check for that project's Docker Compose passes, naming where `docker` was found

#### Scenario: No providers
- **WHEN** a project's `.shogunai.json` declares no providers, or the repository has none
- **THEN** no provider tool check is reported for it

#### Scenario: Wrapper in the repository
- **WHEN** a project declares `gradle`, its base folder has `gradlew` and `gradle` isn't on the `PATH`
- **THEN** the Gradle check passes, naming the wrapper

### Requirement: A missing provider tool is a warning with its fix
A provider tool that is missing or unusable SHALL be a warning, not a failure, naming the project, the tool and what is wrong, with a fix to install or set it up. The check of a project whose providers can't be read SHALL warn with the reason and the fix to correct its `.shogunai.json`.

#### Scenario: Android SDK not found
- **WHEN** a project declares `emulator` and no Android SDK is found
- **THEN** the check warns, naming the places searched, with the fix to install the SDK with Android Studio or set `ANDROID_HOME`, and `shogun doctor` still exits with 0 if nothing else failed

#### Scenario: No Android Virtual Device
- **WHEN** the Android SDK is found and it has no AVD
- **THEN** the check warns, with the fix to create one in Android Studio's Device Manager

#### Scenario: Only the Command Line Tools
- **WHEN** a project declares `xcode` and `xcodebuild -version` fails because only the Command Line Tools are installed
- **THEN** the check warns that Xcode isn't usable, with its output, and the fix to install Xcode and select it with `xcode-select`

#### Scenario: No iOS runtime
- **WHEN** a project declares `ios-simulator` and the simulators have no iOS runtime
- **THEN** the check warns, with the fix to install one from Xcode's Settings › Components

#### Scenario: Not macOS
- **WHEN** a project declares `xcode` or `ios-simulator` on Linux or Windows
- **THEN** the check warns that the provider needs macOS with Xcode

#### Scenario: Docker not running
- **WHEN** a project declares `docker-compose` and `docker` isn't found or `docker compose version` fails
- **THEN** the check warns, with the fix to install Docker with its Compose plugin

### Requirement: Check each multirepo
For each multirepo ShogunAi knows, the check SHALL report one check that passes, naming its repositories and how many groups it has, or that fails when its declaration is missing or invalid or a declared repository isn't a Git repository, and warns when it isn't trusted on this machine. Each problem SHALL be listed with its fix.

#### Scenario: Healthy multirepo
- **WHEN** a trusted multirepo declares `api` and `app`, both Git repositories, with one group
- **THEN** its check passes, naming both repositories and one group

#### Scenario: Repository moved
- **WHEN** a multirepo declares `api` at a folder that doesn't exist
- **THEN** its check fails, naming `api` and the folder, with the fix to restore it or point the alias at its new folder with `shogun multirepo add-repo`

#### Scenario: Invalid declaration
- **WHEN** a multirepo's `.shogunai-multirepo.json` isn't valid
- **THEN** its check fails, naming the file and the problem, with the fix to correct it

#### Scenario: Declaration gone
- **WHEN** a known multirepo's folder no longer has `.shogunai-multirepo.json`
- **THEN** its check warns that ShogunAi will forget it, with the fix to restore the folder or ignore the warning

#### Scenario: Not trusted
- **WHEN** a multirepo's declaration changed since it was trusted on this machine
- **THEN** its check warns, with the fix `shogun multirepo trust <folder>`

#### Scenario: No multirepos
- **WHEN** ShogunAi knows no multirepo
- **THEN** no multirepo check is reported

### Requirement: Check each multirepo's groups
For each group of a multirepo whose repositories can be read, the check SHALL warn when the group's worktree in one of its repositories points to a folder that no longer exists, naming the group, the repository and the folder, with the fix to restore the folder or run `git worktree prune` in that repository.

#### Scenario: Group worktree folder deleted
- **WHEN** the group `feature/TASK-123` has worktrees in `api` and `app`, and the `app` worktree's folder was deleted
- **THEN** the multirepo's check warns, naming `feature/TASK-123`, `app` and the folder, with the fix

#### Scenario: Repository can't be listed
- **WHEN** listing the worktrees of one of the multirepo's repositories fails
- **THEN** the multirepo's check warns, naming the repository and the error, and the groups of the other repositories are still checked
