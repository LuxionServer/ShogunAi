# command-line-tool Specification

## Purpose
Defines the `shogun` command, which runs ShogunAi's worktree operations from a terminal, a script or an agent hook without opening the desktop app.

## Requirements

### Requirement: Find the repository and its project
Every command SHALL work on the Git repository that contains the current directory, or the path it's given, resolved to its main worktree even when run from a linked worktree. When the desktop app has a project for that repository, the command SHALL use its worktrees folder, local files and main branch.

#### Scenario: Run from a linked worktree
- **WHEN** the user runs `shogun status` inside a linked worktree of `~/projects/main-repo`
- **THEN** the command works on `~/projects/main-repo` and lists all its worktrees

#### Scenario: Repository with a project
- **WHEN** the repository has a project in the desktop app with worktrees folder `~/projects/worktrees` and main branch `develop`
- **THEN** `shogun new` creates worktrees under `~/projects/worktrees` and `shogun status` compares against `develop`

#### Scenario: Outside a Git repository
- **WHEN** the user runs a command outside any Git repository
- **THEN** it prints an error saying no repository was found and exits with code 1

### Requirement: Create a worktree from the command line
`shogun new <task-id> [--fix] [--from <ref>]` SHALL create a worktree on a new `feature/` (or `fix/`) branch, and `shogun new --branch <branch>` one from an existing local branch, with the same folder, provisioning, validation and rollback as the desktop app. The new branch SHALL start at `<ref>` when `--from` is given, and otherwise at the HEAD of the worktree containing the current directory. On success it SHALL print the worktree's absolute path alone on standard output.

#### Scenario: New branch
- **WHEN** the user runs `shogun new TASK-123` in a repository with a project
- **THEN** a worktree on `feature/TASK-123` is created in the project's worktrees folder, provisioned, and its path is the only line on standard output

#### Scenario: New branch from a linked worktree
- **WHEN** the user runs `shogun new TASK-123` inside a linked worktree whose HEAD is on `epic/redesign`, while the main checkout is on `develop`
- **THEN** `feature/TASK-123` starts at the commit `epic/redesign` points to, not at `develop`

#### Scenario: Explicit start point
- **WHEN** the user runs `shogun new TASK-123 --from epic/redesign` from the main checkout
- **THEN** `feature/TASK-123` starts at the commit `epic/redesign` points to

#### Scenario: Unknown start point
- **WHEN** the user runs `shogun new TASK-123 --from missing-branch`
- **THEN** the command prints that `missing-branch` doesn't name a commit, creates no branch or folder and exits with code 1

#### Scenario: Start point with an existing branch
- **WHEN** the user runs `shogun new --branch hotfix/login --from develop`
- **THEN** the command prints that `--from` only applies to a new branch and exits with code 2

#### Scenario: Existing branch
- **WHEN** the user runs `shogun new --branch hotfix/login`
- **THEN** a worktree for the existing branch is created and provisioned, and its path is printed

#### Scenario: No project and no worktrees folder
- **WHEN** the repository has no project in the desktop app and the user runs `shogun new TASK-123` without `--worktrees-root`
- **THEN** the command explains that a worktrees folder is needed, creates nothing and exits with code 2

#### Scenario: Creation fails
- **WHEN** creation fails, e.g. because the destination folder already exists
- **THEN** the error is printed on standard error and the command exits with code 1

### Requirement: Provision an existing worktree from the command line
`shogun provision [<path>]` SHALL apply the project's local files and the repository's provisioning to the worktree at `<path>`, or the current directory when omitted, and print each copied file's relative path.

#### Scenario: Worktree created by another tool
- **WHEN** a worktree was created with `git worktree add` and the user runs `shogun provision <its path>`
- **THEN** the files the repository and the project declare are copied into it and listed

#### Scenario: Path is not a worktree of the repository
- **WHEN** `<path>` is the main worktree or not one of the repository's worktrees
- **THEN** nothing is copied and the command exits with code 1 explaining why

### Requirement: Show worktree status from the command line
`shogun status` SHALL print one line per worktree of the repository with its path, branch (or detached), origin (ShogunAi, Claude Code, Herdr or other), uncommitted changes, divergence from its upstream and whether it's merged into the main branch, the same information the desktop app shows. `shogun status --json` SHALL print the same information, plus each worktree's HEAD, its divergence from the main branch and its last commit time, as a single JSON document on standard output, whose fields later versions only add to and never rename, remove or change the type of.

#### Scenario: Several worktrees
- **WHEN** the repository has the main worktree, a merged worktree and one with uncommitted changes
- **THEN** each one has a line, the merged one says it's merged and the other shows its number of changes

#### Scenario: Missing folder
- **WHEN** a worktree's folder no longer exists
- **THEN** its line says the folder is missing

#### Scenario: Worktree created by Herdr
- **WHEN** a worktree is inside Herdr's worktrees folder
- **THEN** its line shows `origin=herdr`

#### Scenario: JSON output
- **WHEN** the user runs `shogun status --json` in a repository with the main worktree, a merged worktree and one with two uncommitted changes
- **THEN** standard output is one JSON object with `repository`, `mainBranch` and a `worktrees` array with one object per worktree, where the main one has `main: true` and `origin: null`, the merged one has `status.merged: true`, and the other has `status.uncommittedChanges: 2`

#### Scenario: JSON output for a missing folder or detached worktree
- **WHEN** the user runs `shogun status --json` and a worktree's folder no longer exists, and another worktree has a detached HEAD
- **THEN** the first has `folderMissing: true` and `status: null`, and the second has `branch: null`; every field is present in every worktree object

#### Scenario: JSON output outside a repository
- **WHEN** the user runs `shogun status --json` outside a Git repository
- **THEN** nothing is printed on standard output, the error is printed as text on standard error and the command exits with code 1

#### Scenario: Unknown option
- **WHEN** the user runs `shogun status --yaml`
- **THEN** it prints that the option is unknown for `status` and exits with code 2

### Requirement: Clean up worktrees from the command line
`shogun clean` SHALL select the same worktrees as the desktop app's cleanup (clean and merged, or with a missing folder; never the main or a locked one) and only list them. With `--yes` it SHALL remove them, and with `--delete-branches` also delete their local branches when merged, reporting what was removed, kept and failed.

#### Scenario: Without confirmation
- **WHEN** the user runs `shogun clean` and two worktrees qualify
- **THEN** both are listed, nothing is removed, and the output says to add `--yes`

#### Scenario: With confirmation
- **WHEN** the user runs `shogun clean --yes --delete-branches`
- **THEN** the listed worktrees are removed, their merged branches deleted, and the result is reported

#### Scenario: Nothing to clean
- **WHEN** no worktree qualifies
- **THEN** the command says so and exits with code 0

#### Scenario: A removal fails
- **WHEN** removing one of the worktrees fails
- **THEN** the others are still removed, the failure is reported on standard error and the command exits with code 1

### Requirement: Usage, version and exit codes
`shogun help` (or no arguments in the standalone distribution) SHALL print the commands and options; `shogun --version` the version. An unknown command or option, or a missing argument, SHALL print a short usage error on standard error and exit with code 2.

#### Scenario: Version
- **WHEN** the user runs `shogun --version`
- **THEN** it prints the same version as the desktop app it ships with

#### Scenario: Unknown command
- **WHEN** the user runs `shogun frobnicate`
- **THEN** it prints that the command is unknown, points to `shogun help` and exits with code 2

### Requirement: Install the command on each platform
The `shogun` command SHALL be available from each release: on macOS and Linux through the desktop app's launcher, which runs the command instead of the window when it receives arguments; on Windows through the Scoop manifest, which runs the command with the runtime bundled in the app; and on any platform with Java 21 or later through a `shogun-<version>.zip` attached to the release.

#### Scenario: Desktop app launched with arguments
- **WHEN** the desktop app's executable is run with `status` as argument
- **THEN** it prints the status and exits without opening a window

#### Scenario: Desktop app launched without arguments
- **WHEN** the desktop app is opened normally
- **THEN** the window opens as before

#### Scenario: Release assets
- **WHEN** a release is published
- **THEN** it includes `shogun-<version>.zip` with launch scripts for Unix and Windows

#### Scenario: Windows through Scoop
- **WHEN** a Windows user installs ShogunAi with `scoop install shogunai`
- **THEN** `shogun status` works from a new terminal without Java installed

### Requirement: Create worktrees for Claude Code's WorktreeCreate hook
`shogun hook worktree-create` SHALL read the hook's JSON from standard input and create a provisioned worktree named after its `name` field, on a `feature/<name>` branch starting at the HEAD of the worktree containing its `cwd` field, in the repository containing that `cwd`. It SHALL print the absolute worktree path as the last line of standard output, with any other message on standard error, and exit non-zero on failure.

#### Scenario: Repository with a project
- **WHEN** Claude Code runs the hook with `{"name": "TASK-123", "cwd": "~/projects/main-repo", …}` and the repository has a ShogunAi project
- **THEN** a worktree on `feature/TASK-123` is created in the project's worktrees folder, provisioned from `.shogunai.json`, `.worktreeinclude` and the project's local files, and its path is printed

#### Scenario: Session in a linked worktree
- **WHEN** the hook's `cwd` is a linked worktree whose HEAD is on `epic/redesign`, while the main checkout is on `develop`
- **THEN** `feature/<name>` starts at the commit `epic/redesign` points to

#### Scenario: Repository without a project
- **WHEN** the repository has no ShogunAi project
- **THEN** the worktree is created in `<repository>/.claude/worktrees/<name>`, provisioned from the repository's files, and its path is printed

#### Scenario: Resuming an existing worktree
- **WHEN** the worktree for `name` already exists as a worktree of the repository
- **THEN** its path is printed and the command succeeds without creating or provisioning anything

#### Scenario: Invalid input
- **WHEN** standard input isn't JSON or lacks `name`
- **THEN** the command prints an error on standard error and exits with code 2, so Claude Code fails the creation

#### Scenario: Creation fails
- **WHEN** creating the worktree fails, e.g. because `.shogunai.json` is unreadable
- **THEN** the error is printed on standard error, nothing is printed on standard output and the command exits with code 1

### Requirement: Remove worktrees for Claude Code's WorktreeRemove hook
`shogun hook worktree-remove` SHALL read the hook's JSON from standard input and remove the linked worktree at its `worktree_path` field even if it has changes, as Claude Code's own fallback does, then delete the worktree's branch only if it is merged into the project's main branch, keeping it otherwise.

#### Scenario: Merged branch
- **WHEN** the hook runs for a worktree whose branch is merged into the project's main branch
- **THEN** the worktree and its branch are removed and the command exits with code 0

#### Scenario: Branch with unmerged work
- **WHEN** the worktree's branch has commits that aren't merged into the main branch
- **THEN** the worktree is removed, the branch is kept, a note saying so goes to standard error, and the command exits with code 0

#### Scenario: Branch merged only into its upstream
- **WHEN** the branch was pushed and is merged into its own upstream but not into the main branch
- **THEN** the branch is kept and a note saying so goes to standard error

#### Scenario: Not a worktree of the repository
- **WHEN** `worktree_path` is the main worktree or not a linked worktree
- **THEN** nothing is removed and the command exits with code 1

### Requirement: List Gradle daemons from the command line
`shogun gradle` SHALL print one line per Gradle or Kotlin daemon with its process id, kind, Gradle version, memory, state and, inside a Git repository, its worktree, followed by the total memory. It SHALL work outside a Git repository. Inside a repository that declares the Gradle provider it SHALL also print the build cache notice when it applies.

#### Scenario: Daemons running
- **WHEN** the user runs `shogun gradle` in a repository while an idle and a busy Gradle daemon are running
- **THEN** each one has a line with its state and worktree, and the total memory is printed

#### Scenario: No daemons
- **WHEN** no daemon is running
- **THEN** the command says so and exits with code 0

#### Scenario: Outside a repository
- **WHEN** the user runs `shogun gradle` outside any Git repository
- **THEN** the daemons are listed without a worktree column

#### Scenario: Build cache off
- **WHEN** the repository declares the Gradle provider and doesn't enable the build cache
- **THEN** the output ends with the build cache notice

### Requirement: Stop Gradle daemons from the command line
`shogun gradle stop` SHALL stop every idle daemon, and `shogun gradle stop <pid>...` only the given ones, following the Gradle provider's rules for idle daemons. It SHALL report what was stopped and what was kept with the reason, and exit with code 1 when a given process id isn't a daemon.

#### Scenario: Stop all idle
- **WHEN** the user runs `shogun gradle stop` with two idle Gradle daemons and a busy one
- **THEN** the two idle ones are stopped, the busy one is reported as kept, and the command exits with code 0

#### Scenario: Stop a busy daemon by id
- **WHEN** the user runs `shogun gradle stop 4242` and daemon 4242 is busy
- **THEN** it isn't stopped, the output says it's busy and the command exits with code 0

#### Scenario: Not a daemon
- **WHEN** the user runs `shogun gradle stop 99999` and no daemon has that process id
- **THEN** an error on standard error says so and the command exits with code 1

### Requirement: Stop Gradle daemons when cleaning up from the command line
With `--yes`, `shogun clean --stop-gradle-daemons` SHALL also stop the idle Gradle daemons whose last build was in a removed worktree, following the cleanup rules of the desktop app, and report how many were stopped and kept. Without the option no daemon SHALL be stopped.

#### Scenario: Daemons of removed worktrees
- **WHEN** the user runs `shogun clean --yes --stop-gradle-daemons` and an idle daemon's last build was in a removed worktree
- **THEN** that daemon is stopped and the output says so

#### Scenario: Without the option
- **WHEN** the user runs `shogun clean --yes`
- **THEN** no daemon is stopped

#### Scenario: Option without confirmation
- **WHEN** the user runs `shogun clean --stop-gradle-daemons` without `--yes`
- **THEN** nothing is removed or stopped, and the output also lists the daemons that would be stopped

### Requirement: Print a worktree's environment from the command line
`shogun env [<path>] [--format sh|dotenv|json]` SHALL resolve the environment of the worktree containing the path, or the current directory, and print it: `sh` (the default) as `export NAME='value'` lines safe for `eval`, `dotenv` as `NAME=value` lines, `json` as an object. An empty environment SHALL print nothing and exit with code 0.

#### Scenario: Shell format
- **WHEN** the user runs `eval "$(shogun env)"` inside a worktree whose `PORT` resolves to 20001
- **THEN** `PORT` is 20001 in that shell

#### Scenario: Values with special characters
- **WHEN** a value contains a space, a quote or `$`
- **THEN** the `sh` output keeps it exactly once evaluated

#### Scenario: Linked worktree path
- **WHEN** the user runs `shogun env ../TASK-123 --format json` from the main worktree
- **THEN** it prints the environment of `TASK-123`, not the main worktree's

#### Scenario: Not a repository
- **WHEN** the path isn't inside a Git repository
- **THEN** an error is printed on standard error and the command exits with code 1

### Requirement: Deliver the environment through Claude Code's SessionStart hook
`shogun hook session-start` SHALL read the hook's JSON from standard input, resolve the environment of the worktree containing its `cwd` field, append one `export` line per variable to the file named by `CLAUDE_ENV_FILE`, and print a single line of context naming the variables and their values. With an empty environment it SHALL write and print nothing and exit with code 0.

#### Scenario: Session in a worktree
- **WHEN** Claude Code starts a session in a worktree whose `PORT` resolves to 20001
- **THEN** `export PORT='20001'` is appended to `$CLAUDE_ENV_FILE` and the context line says this worktree uses `PORT=20001`

#### Scenario: No environment file variable
- **WHEN** `CLAUDE_ENV_FILE` isn't set
- **THEN** only the context line is printed and the command exits with code 0

#### Scenario: Repository without environment
- **WHEN** the repository declares no `env`
- **THEN** nothing is written or printed and the command exits with code 0

#### Scenario: Invalid declaration
- **WHEN** `.shogunai.json` can't be read
- **THEN** the error is printed on standard error and the command exits with code 1, without writing to `$CLAUDE_ENV_FILE`

### Requirement: Manage a worktree's emulator from the command line
`shogun emulator start [<path>] [--avd <name>] [--no-window]`, `shogun emulator stop [<path>]` and `shogun emulator status [<path>]` SHALL act on the emulator of the worktree containing the path or the current directory, printing its serial and state, or `not acquired` when the worktree holds no emulator. `start` SHALL acquire the emulator from the pool when needed, print on standard error any emulator released to make room, and print the `export ANDROID_SERIAL=…` line to run when the emulator was just acquired. In a repository that doesn't declare the emulator provider they SHALL fail with exit code 1 and say how to declare it.

#### Scenario: Start
- **WHEN** the user runs `shogun emulator start` in a worktree whose serial is `emulator-5556`
- **THEN** the emulator boots, the command prints `emulator-5556 running` and exits with code 0

#### Scenario: First start
- **WHEN** the user runs `shogun emulator start` in a worktree that holds no emulator and the pool has room
- **THEN** a console port is reserved, the emulator boots, and the output includes its serial and `export ANDROID_SERIAL=emulator-<port>`

#### Scenario: Pool full
- **WHEN** the user runs `shogun emulator start` and the emulator pool is full with every emulator running
- **THEN** the pool's error, naming the worktrees that hold emulators, is printed on standard error and the command exits with code 1

#### Scenario: Status
- **WHEN** the user runs `shogun emulator status` and the emulator is stopped
- **THEN** it prints `emulator-5556 stopped` and exits with code 0

#### Scenario: Status before the first start
- **WHEN** the user runs `shogun emulator status` in a worktree that never started its emulator
- **THEN** it prints `not acquired` and exits with code 0

#### Scenario: Repository without the provider
- **WHEN** the user runs `shogun emulator start` in a repository without the provider
- **THEN** an error on standard error explains how to declare `"providers": { "emulator": {} }` and the command exits with code 1

### Requirement: Stop emulators when cleaning up from the command line
With `--yes`, `shogun clean --stop-emulators` SHALL also stop the running emulators of the removed worktrees, and without `--yes` list them. Without the option no emulator SHALL be stopped. A failure to stop one SHALL be reported on standard error without changing the exit code.

#### Scenario: Emulator of a removed worktree
- **WHEN** the user runs `shogun clean --yes --stop-emulators` and a removed worktree's emulator is running
- **THEN** it's stopped and the output says so

#### Scenario: Without the option
- **WHEN** the user runs `shogun clean --yes`
- **THEN** no emulator is stopped

### Requirement: Tell Claude how to start its emulator
When the repository declares the emulator provider, the context line printed by `shogun hook session-start` SHALL say how to get the worktree's emulator: when the worktree holds one, that it is `ANDROID_SERIAL` and that `shogun emulator start` starts it; otherwise, that `shogun emulator start` acquires and starts it and prints the `ANDROID_SERIAL` to export. It SHALL also warn that the adb server is shared with other worktrees' agents, so the agent must not restart it nor pick a device other than `ANDROID_SERIAL`.

#### Scenario: Context with the emulator
- **WHEN** a Claude Code session starts in a worktree whose serial is `emulator-5556`
- **THEN** the context line includes `ANDROID_SERIAL=emulator-5556` and mentions `shogun emulator start`

#### Scenario: Context before the first start
- **WHEN** a Claude Code session starts in a worktree that holds no emulator
- **THEN** the context line has no `ANDROID_SERIAL`, says `shogun emulator start` acquires the emulator and prints the variable to export, and nothing is reserved

#### Scenario: Shared adb server warning
- **WHEN** a Claude Code session starts in a worktree of a repository that declares the emulator provider
- **THEN** the context line says not to run `adb kill-server` or `adb start-server` and not to pass `-s`, `-e` or `-d` to adb

### Requirement: Manage a worktree's iOS simulator from the command line
`shogun simulator start|stop|status [<path>] [--device <type>] [--runtime <runtime>]` SHALL act on the simulator of the worktree containing the path or the current directory, printing its UDID and state, or `not acquired` when the worktree holds none; `--device` and `--runtime` only apply when the simulator has to be created. `start` SHALL acquire the simulator from the pool when needed, print on standard error any simulator released to make room, and print the `export IOS_SIMULATOR_UDID=…` line to run when it was just acquired. `status` SHALL also say whether the repository's KMP tests are wired to it, only when the repository applies the Kotlin Multiplatform plugin. In a repository without the provider the commands SHALL fail with exit code 1 and say how to declare it.

#### Scenario: Start
- **WHEN** the user runs `shogun simulator start` in a worktree
- **THEN** its simulator boots, Xcode's simulator viewer shows it, the command prints its UDID and `booted`, and exits with code 0

#### Scenario: First start
- **WHEN** the user runs `shogun simulator start` in a worktree that holds no simulator and the pool has room
- **THEN** the simulator is created and booted, and the output includes `export IOS_SIMULATOR_UDID=<UDID>`

#### Scenario: Status of an unwired repository
- **WHEN** the user runs `shogun simulator status` in a Kotlin Multiplatform repository and no build script mentions `IOS_SIMULATOR_UDID`
- **THEN** the output includes the state and says the KMP tests aren't wired, pointing to `shogun simulator gradle-init install`

#### Scenario: Status outside Kotlin Multiplatform
- **WHEN** the user runs `shogun simulator status` in a repository whose build scripts don't apply the Kotlin Multiplatform plugin
- **THEN** the output is the simulator's state alone, without any line about KMP tests

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
When the repository declares the iOS simulator provider, the `shogun hook session-start` context line SHALL say how to get the worktree's simulator: when the worktree holds one, its `IOS_SIMULATOR_UDID`, how to use it with `xcodebuild -destination`, and that `shogun simulator start` boots it; otherwise, that `shogun simulator start` creates and boots it and prints the `IOS_SIMULATOR_UDID` to export.

#### Scenario: Context with the simulator
- **WHEN** a Claude Code session starts in a worktree with a simulator
- **THEN** the context line includes its UDID, `-destination "id=$IOS_SIMULATOR_UDID"` and `shogun simulator start`

#### Scenario: Context before the first start
- **WHEN** a Claude Code session starts in a worktree that holds no simulator
- **THEN** the context line says `shogun simulator start` creates it and prints the variable to export, and no simulator is created

### Requirement: List DerivedData from the command line
`shogun xcode` SHALL print, inside a repository that declares the Xcode provider on macOS, one line per DerivedData folder of its worktrees with the worktree and size, then the number and size of orphaned folders on the machine, folders on unavailable volumes, and the compilation cache size when caching is declared. Elsewhere it SHALL say the provider isn't active and exit with code 0.

#### Scenario: Worktrees with DerivedData
- **WHEN** the user runs `shogun xcode` in a repository with the provider and two worktrees have DerivedData
- **THEN** each has a line with its worktree and size, followed by the orphans summary

#### Scenario: Provider not declared
- **WHEN** the repository doesn't declare the provider
- **THEN** the command says so and exits with code 0

### Requirement: Delete orphaned DerivedData from the command line
`shogun xcode clean` SHALL delete the orphaned DerivedData folders on the machine following the Xcode provider's deletion rules, print each folder deleted and the space freed, and report folders that couldn't be deleted on standard error with exit code 1.

#### Scenario: Orphans deleted
- **WHEN** the user runs `shogun xcode clean` and two folders are orphaned
- **THEN** both are deleted and the output gives the space freed

#### Scenario: Nothing to delete
- **WHEN** no folder is orphaned
- **THEN** the command says so and exits with code 0

### Requirement: Delete DerivedData when cleaning up from the command line
With `--yes`, `shogun clean --delete-derived-data` SHALL delete the orphaned DerivedData after removing the worktrees, the removed worktrees' folders included, and without `--yes` list the orphans already found. Without the option no DerivedData SHALL be deleted.

#### Scenario: Cleanup with the option
- **WHEN** the user runs `shogun clean --yes --delete-derived-data` and a removed worktree had DerivedData
- **THEN** that folder is deleted and the output says so

#### Scenario: Without the option
- **WHEN** the user runs `shogun clean --yes`
- **THEN** no DerivedData is deleted

### Requirement: Check a worktree's provisioned files from the command line
`shogun provision --check [<path>]` SHALL compare the provisioned files of the worktree at `<path>`, or the current directory when omitted, with the base repository's, without changing anything. It SHALL print one line per reported file with its relative path and state (missing, differs or not linked), and exit with code 0 when nothing is reported and code 3 otherwise.

#### Scenario: Stale secret
- **WHEN** the worktree's `.env` differs from the base repository's and the user runs `shogun provision --check`
- **THEN** a line reports `.env` as differing, no file is copied or linked, and the command exits with code 3

#### Scenario: Up to date
- **WHEN** every declared file of the worktree matches the base repository's
- **THEN** nothing is printed and the command exits with code 0

#### Scenario: Path is not a worktree of the repository
- **WHEN** `<path>` is the main worktree or not one of the repository's worktrees
- **THEN** the command explains why and exits with code 1

### Requirement: Report linked files when provisioning from the command line
`shogun provision [<path>]` SHALL list linked files alongside copied ones, marking each linked file, and marking a file declared in `link` that was copied because links aren't available.

#### Scenario: Linked and copied files
- **WHEN** the repository declares `.env` in `copy` and `local.properties` in `link`, and the user runs `shogun provision`
- **THEN** both files are listed, and `local.properties` is marked as linked

### Requirement: Report a failed post-creation command from the command line
When a post-creation command fails, `shogun new` and `shogun provision` SHALL print the error, naming the command, its exit code and its error output, on standard error and exit with code 1. `shogun new` SHALL still print the kept worktree's path on standard output.

#### Scenario: New worktree with a failing command
- **WHEN** the user runs `shogun new TASK-123` and the repository's post-creation command fails
- **THEN** the worktree's path is printed on standard output, the failure on standard error, and the command exits with code 1

#### Scenario: Provisioning with a failing command
- **WHEN** the user runs `shogun provision` and a post-creation command fails
- **THEN** the failure is printed on standard error and the command exits with code 1

### Requirement: Keep Claude Code's worktree when a post-creation command fails
When creating a worktree for `shogun hook worktree-create` succeeds but a post-creation command fails, the hook SHALL print the failure on standard error, print the worktree's path as the last line of standard output and exit with code 0, so Claude Code still uses the worktree.

#### Scenario: Failing setup command
- **WHEN** Claude Code runs the hook in a repository whose post-creation `npm install` fails
- **THEN** the failure is on standard error, the worktree's path is the last line of standard output, and the command exits with code 0

### Requirement: Unexpected failures are reported in one line
Any failure a command doesn't handle specifically SHALL be printed on standard error as one line starting with `shogun:` that says what failed, and the command SHALL exit with code 1, never printing a stack trace.

#### Scenario: Corrupted projects file
- **WHEN** `~/.shogunai/projects.json` isn't valid JSON and the user runs `shogun status`
- **THEN** it prints one line naming the file and saying it can't be read, and exits with code 1

#### Scenario: Hook with an unexpected failure
- **WHEN** `shogun hook session-start` can't write to `CLAUDE_ENV_FILE`
- **THEN** it prints one line naming the file and exits with code 1

### Requirement: Explain frequent Git failures
When a Git command fails for a cause the desktop app recognizes (branch already exists, lock file present, and the other recognized causes), the CLI SHALL print the same explanation and suggested action after Git's own message.

#### Scenario: Branch already exists
- **WHEN** `shogun new TASK-123` fails because `feature/TASK-123` already exists
- **THEN** the error says the branch exists and suggests `shogun new --branch feature/TASK-123`

### Requirement: Help for each command
`shogun <command> --help` SHALL print that command's usage and options on standard output and exit with code 0.

#### Scenario: Help for new
- **WHEN** the user runs `shogun new --help`
- **THEN** it prints the usage of `new` with `--fix`, `--branch`, `--from` and `--worktrees-root`, and exits with code 0

### Requirement: Say why a worktree was skipped
`shogun status` SHALL show the reason next to a worktree whose status can't be read, and `shogun clean` SHALL list worktrees it skipped because their status couldn't be read, with the reason. The dry-run hint of `shogun clean` SHALL repeat the options it was given and describe only what would actually be removed.

#### Scenario: Unreadable status
- **WHEN** a worktree's folder exists but Git can't read its status
- **THEN** `shogun status` shows `status-unavailable` with Git's reason, and `shogun clean` lists it as skipped with that reason

#### Scenario: Dry run with options
- **WHEN** the user runs `shogun clean --delete-branches` and only orphaned DerivedData qualifies
- **THEN** the hint suggests `shogun clean --delete-branches --yes` and mentions DerivedData, not worktrees

### Requirement: Recognize the repository's Git layout
Commands SHALL work in submodules and in repositories whose Git folder is separate from the working tree. In a bare repository they SHALL fail with an error that says bare repositories aren't supported yet, and in a repository without commits with an error that says a first commit is needed.

#### Scenario: Submodule
- **WHEN** the user runs `shogun status` inside a submodule
- **THEN** it works on the submodule's repository

#### Scenario: Bare repository
- **WHEN** the user runs `shogun new TASK-123` in a bare repository or one of its worktrees
- **THEN** it prints that bare repositories aren't supported yet and exits with code 1

#### Scenario: No commits
- **WHEN** the user runs `shogun new TASK-123` in a repository without commits
- **THEN** it prints that the repository needs a first commit and exits with code 1

### Requirement: Create a worktree from a remote branch on the command line
`shogun new --branch <name>` SHALL accept a branch that exists only on `origin`, given as `<name>` or `origin/<name>`, creating a local branch `<name>` that tracks it, with the same folder, provisioning and rollback as the desktop app. A local branch with that name SHALL take precedence.

#### Scenario: Remote-only branch
- **WHEN** the user runs `shogun new --branch feature/TASK-9` and only `origin/feature/TASK-9` exists
- **THEN** a local `feature/TASK-9` tracking it is created with its worktree, and the worktree's path is printed

#### Scenario: Branch nowhere
- **WHEN** the user runs `shogun new --branch feature/missing` and neither a local nor an `origin/` branch has that name
- **THEN** the command prints that the branch doesn't exist, creates nothing and exits with code 1

### Requirement: Fetch before creating on the command line
`shogun new --fetch` SHALL fetch from `origin` before resolving `--from` or `--branch`, without waiting for credentials. When the fetch fails, the command SHALL print Git's error, create nothing and exit with code 1.

#### Scenario: Fetch then create
- **WHEN** the user runs `shogun new TASK-123 --from origin/develop --fetch`
- **THEN** the command fetches from `origin` and the new branch starts at the fetched `origin/develop`

#### Scenario: Fetch fails
- **WHEN** the user runs `shogun new TASK-123 --fetch` without network
- **THEN** the command prints the fetch error on standard error, creates nothing and exits with code 1

### Requirement: Remove a worktree from the command line
`shogun remove <target>` SHALL remove the linked worktree identified by a task id, a path or a branch name. It SHALL refuse a worktree with uncommitted or untracked changes, or with a running agent, unless `--force` is given; it SHALL never remove the main worktree. With `--delete-branch` it SHALL delete the branch only when merged into the main branch, and keep it otherwise, saying why; with `--force-delete-branch` it SHALL delete the branch even if unmerged. A kept branch SHALL NOT make the command fail. With `--json`, `branchDeleted` SHALL name the deleted branch or be `null`, and `branchKeptReason` SHALL say why a requested deletion didn't happen or be `null`. It SHALL stop the resources the repository's providers keep for that worktree, as cleanup does.

#### Scenario: Remove by task id
- **WHEN** the user runs `shogun remove TASK-123` and the worktree is clean
- **THEN** the worktree is removed, its emulator and simulator are stopped if the providers are on, its branch is kept, and the command exits with code 0

#### Scenario: Changes without force
- **WHEN** the worktree has uncommitted changes and the user runs `shogun remove TASK-123`
- **THEN** nothing is removed, the error says the worktree has changes and suggests `--force`, and the command exits with code 1

#### Scenario: Delete a merged branch
- **WHEN** the user runs `shogun remove TASK-123 --delete-branch` and the branch is merged into the main branch
- **THEN** the worktree and its branch are removed

#### Scenario: Unmerged branch kept
- **WHEN** the user runs `shogun remove TASK-123 --delete-branch` and the branch isn't merged into the main branch
- **THEN** the worktree is removed, the branch is kept with a message saying it isn't merged, and the command exits with code 0

#### Scenario: Force-delete an unmerged branch
- **WHEN** the user runs `shogun remove TASK-123 --force-delete-branch` and the branch isn't merged into the main branch
- **THEN** the worktree and the branch are removed

#### Scenario: Kept branch in JSON
- **WHEN** the user runs `shogun remove TASK-123 --delete-branch --json` and the branch is kept
- **THEN** the object has `branchDeleted: null` and `branchKeptReason` with the reason

#### Scenario: Ambiguous or unknown target
- **WHEN** the target matches no worktree, or matches more than one
- **THEN** nothing is removed, the error lists the candidates if any, and the command exits with code 1

#### Scenario: Main worktree
- **WHEN** the target is the main worktree
- **THEN** nothing is removed and the command exits with code 1

### Requirement: Repair worktrees from the command line
`shogun repair` SHALL run the same repair the desktop app runs after a repository or its worktrees folder moves, and report the worktrees it re-linked.

#### Scenario: Repository moved
- **WHEN** the repository was moved and the user runs `shogun repair` in it
- **THEN** the worktrees found at their old relative location under the worktrees folder are re-linked and listed, and the command exits with code 0

### Requirement: Machine-readable output for every result command
`shogun new`, `provision`, `trust`, `clean`, `gradle`, `gradle stop`, `xcode`, `xcode clean`, `emulator`, `simulator`, `remove` and `repair` SHALL accept `--json`. With it, a command SHALL print one compact JSON object on a single line on standard output instead of its text, with the same exit code. Every documented field SHALL be present, `null` when it doesn't apply, and later versions SHALL only add fields.

#### Scenario: Emulator status as JSON
- **WHEN** the user runs `shogun emulator status --json` in a worktree whose emulator runs as `emulator-5556`
- **THEN** standard output is one line with an object whose `serial` is `emulator-5556` and `state` is `running`, and the command exits with code 0

#### Scenario: Unknown option on a command without JSON
- **WHEN** the user runs `shogun hook session-start --json`
- **THEN** it prints that the option is unknown and exits with code 2

### Requirement: Errors in JSON mode
When a command run with `--json` fails before producing its result, it SHALL print the error as text on standard error, print nothing on standard output and exit with the same code as without `--json`.

#### Scenario: Outside a repository
- **WHEN** the user runs `shogun clean --json` outside a Git repository
- **THEN** standard output is empty, the error is on standard error and the exit code is 1

### Requirement: No questions in JSON mode
A command run with `--json` SHALL never ask a question. Post-creation commands that aren't approved SHALL be skipped and listed in the result, and `trust --json` without `--yes` SHALL only report.

#### Scenario: Unapproved post-creation commands
- **WHEN** the user runs `shogun new TASK-123 --json` in a repository whose post-creation commands aren't approved
- **THEN** the worktree is created without running them, and the object lists them in `skippedPostCreateCommands`

#### Scenario: Trust without confirmation
- **WHEN** the user runs `shogun trust --json` in a repository with unapproved commands
- **THEN** the object lists the commands with `approved: false`, nothing is approved and nothing is asked

### Requirement: JSON result of worktree creation and provisioning
`shogun new --json` SHALL report the worktree's path and branch, the skipped post-creation commands and any post-creation failure. A post-creation failure SHALL still print the object, since the worktree exists, and exit with code 1. `shogun provision --check --json` SHALL list each reported file with its path and state, and exit with code 3 when there is any.

#### Scenario: Post-creation command fails
- **WHEN** `shogun new TASK-123 --json` creates the worktree and a post-creation command exits with code 2
- **THEN** standard output is the object with the path and a `postCreateFailure` naming the command and exit code 2, and the command exits with code 1

#### Scenario: Stale file
- **WHEN** the user runs `shogun provision --check --json` and `local.properties` differs from the base repository's
- **THEN** the object's `drift` lists `local.properties` with state `differs`, and the exit code is 3

### Requirement: JSON result of cleanup
`shogun clean --json` SHALL report whether it was a dry run, the candidates with why each qualifies, the worktrees kept because an agent runs in them, the ones skipped because their status couldn't be read, and, with `--yes`, what was removed, which branches were deleted and what failed.

#### Scenario: Dry run
- **WHEN** the user runs `shogun clean --json` with one merged worktree and one where an agent runs
- **THEN** the object has `dryRun: true`, the merged one in `candidates` with reason `merged`, the other in `keptForAgents`, and empty `removed`

#### Scenario: Removal
- **WHEN** the user runs `shogun clean --yes --delete-branches --json` with one merged worktree on `feature/TASK-1`
- **THEN** the object has `dryRun: false`, its path in `removed` and `feature/TASK-1` in `deletedBranches`

### Requirement: Show and trim the resource pools from the command line
`shogun pool status` SHALL print, for each resource kind, the profile's effective limit, how many are in use, and each one's project, worktree and state. `shogun pool trim` SHALL stop idle Gradle daemons over the Gradle limit and print what it stopped and kept. Both SHALL accept `--json`.

#### Scenario: Status
- **WHEN** the limit is 2 emulators and `main-repo` · `TASK-1` holds a running one
- **THEN** `shogun pool status` prints `emulators 1/2` followed by `main-repo · TASK-1 running`

#### Scenario: Trim
- **WHEN** the Gradle limit is 2 and three idle Gradle daemons run
- **THEN** `shogun pool trim` stops the longest-idle one and prints it, and exits with code 0

#### Scenario: JSON
- **WHEN** the user runs `shogun pool status --json`
- **THEN** it prints an object with each kind's limit, usage and holders
