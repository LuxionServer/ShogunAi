# Spec Delta

## Purpose

Lets a user, or someone helping them, check in one step that ShogunAi's installation and the tools it relies on are usable on this machine, and learn how to fix each problem, from the CLI and from the desktop app.

## ADDED Requirements

### Requirement: Run the installation check without changing anything
The system SHALL offer an installation check, as `shogun doctor` in the CLI and as "Check Installation…" in the desktop app's Help menu. It SHALL run every check in this specification, in the order listed, and SHALL NOT write, create or delete any file, install anything or start any program other than to read a version.

#### Scenario: Nothing changes
- **WHEN** the installation check runs on a machine where the hooks are outdated and a project's local file is missing
- **THEN** both are reported and no settings file, project, worktree or local file is modified

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
