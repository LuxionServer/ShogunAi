# command-line-tool Specification

## Purpose
Defines the `shogun` command, which runs ShogunAi's worktree operations from a terminal, a script or an agent hook without opening the desktop app.

## Requirements

### Requirement: Find the repository and its project
Every command SHALL work on the Git repository that contains the current directory, or the path it's given, resolved to its main worktree even when run from a linked worktree. When the desktop app has a project for that repository, the command SHALL use its worktrees folder, local files and main branch. `shogun new`, `shogun status`, `shogun open`, `shogun clean` and `shogun multirepo` SHALL also work in a multirepo folder, which isn't a Git repository, acting on the multirepo and its groups. `project list` and `app` without a path are the exceptions: they work outside a Git repository.

#### Scenario: Run from a linked worktree
- **WHEN** the user runs `shogun status` inside a linked worktree of `~/projects/main-repo`
- **THEN** the command works on `~/projects/main-repo` and lists all its worktrees

#### Scenario: Repository with a project
- **WHEN** the repository has a project in the desktop app with worktrees folder `~/projects/worktrees` and main branch `develop`
- **THEN** `shogun new` creates worktrees under `~/projects/worktrees` and `shogun status` compares against `develop`

#### Scenario: Multirepo folder
- **WHEN** the user runs `shogun status` in a multirepo folder, which isn't a Git repository
- **THEN** the command shows the multirepo's groups instead of failing

#### Scenario: Outside a Git repository
- **WHEN** the user runs a command other than `project list` or `app` outside any Git repository and multirepo folder
- **THEN** it prints an error saying no repository was found and exits with code 1

#### Scenario: Project commands outside a repository
- **WHEN** the user runs `shogun project list` or `shogun app` without a path outside any Git repository
- **THEN** the command runs and does not fail for lack of a repository

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
`shogun env [<path>] [--format sh|dotenv|json]` SHALL resolve the environment of the worktree containing the path, or the current directory, and print it: `sh` (the default) as `export NAME='value'` lines safe for `eval`, `dotenv` as `NAME=value` lines, `json` as an object. Declared variables SHALL only be printed while the repository is approved (see `worktree-environment`); when some await approval, a note naming them and `shogun trust` SHALL go to standard error. An empty environment SHALL print nothing on standard output and exit with code 0.

#### Scenario: Shell format
- **WHEN** the user runs `eval "$(shogun env)"` inside a worktree of an approved repository whose `PORT` resolves to 20001
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

#### Scenario: Variables awaiting approval
- **WHEN** the user runs `shogun env` in a worktree of a repository that declares `PORT` and isn't approved
- **THEN** `PORT` isn't printed, standard error names `PORT` and `shogun trust`, and the command exits with code 0

### Requirement: Deliver the environment through Claude Code's SessionStart hook
`shogun hook session-start` SHALL read the hook's JSON from standard input and, inside a Git repository, print the short workflow rules of `shogun guide` that apply to that location. Only when the repository is registered as a project SHALL it also resolve the environment of the worktree containing its `cwd` field, append one `export` line per variable to the file named by `CLAUDE_ENV_FILE`, and print a context line naming the variables without their values; declared variables awaiting approval SHALL be named in the context as awaiting the user's approval with `shogun trust`, and not delivered. In a group worktree it SHALL also add the group's context, and in a multirepo folder it SHALL print the multirepo's context. Anywhere else outside a Git repository it SHALL write and print nothing and exit with code 0.

#### Scenario: Session in a worktree
- **WHEN** Claude Code starts a session in a worktree of a registered, approved project whose `PORT` resolves to 20001
- **THEN** `export PORT='20001'` is appended to `$CLAUDE_ENV_FILE`, the context says this worktree's environment has `PORT` without showing `20001`, and it includes the rule not to change the worktree's branch

#### Scenario: No environment file variable
- **WHEN** `CLAUDE_ENV_FILE` isn't set
- **THEN** only the context is printed and the command exits with code 0

#### Scenario: Repository without environment
- **WHEN** the repository declares no `env`
- **THEN** nothing is written to `$CLAUDE_ENV_FILE`, the context has the workflow rules but no variables line, and the command exits with code 0

#### Scenario: Main checkout
- **WHEN** Claude Code starts a session in the repository's main checkout
- **THEN** the context says to create a worktree with `shogun new` for each change and not to change the checkout's branch

#### Scenario: Herdr not available
- **WHEN** Herdr isn't installed or its server isn't running
- **THEN** the context has no rule about `shogun open --herdr` nor about messaging other sessions

#### Scenario: Short context
- **WHEN** a session starts in a worktree of a repository with every provider declared
- **THEN** the workflow rules in the context take at most ten lines and end with how to read the rest with `shogun guide`

#### Scenario: Group worktree
- **WHEN** the `cwd` is inside a worktree of a group
- **THEN** the worktree's environment is delivered as for any worktree and the context also describes the group

#### Scenario: Multirepo folder
- **WHEN** the `cwd` is a multirepo folder
- **THEN** the multirepo's context is printed, nothing is written to `$CLAUDE_ENV_FILE`, and the command exits with code 0

#### Scenario: Not a repository
- **WHEN** the `cwd` isn't inside a Git repository and isn't a multirepo folder
- **THEN** nothing is written or printed and the command exits with code 0

#### Scenario: Invalid declaration
- **WHEN** `.shogunai.json` of a registered project can't be read
- **THEN** the error is printed on standard error and the command exits with code 1, without writing to `$CLAUDE_ENV_FILE`

#### Scenario: Repository not registered
- **WHEN** Claude Code starts a session in a cloned repository that isn't registered as a project and whose `.shogunai.json` declares `env`
- **THEN** nothing is written to `$CLAUDE_ENV_FILE`, no environment is resolved nor reserved, the context has only the workflow rules, and the command exits with code 0

#### Scenario: Variables awaiting approval
- **WHEN** a session starts in a worktree of a registered project that declares `PORT` and isn't approved
- **THEN** nothing about `PORT` is written to `$CLAUDE_ENV_FILE`, and the context names `PORT` as awaiting the user's approval with `shogun trust`

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
`shogun provision --check [<path>]` SHALL compare the provisioned files of the worktree at `<path>`, or the current directory when omitted, with the base repository's, without changing anything. It SHALL print one line per reported file with its relative path and state (missing, differs, not linked or deleted in base), and exit with code 0 when nothing is reported and code 3 otherwise.

#### Scenario: Stale secret
- **WHEN** the worktree's `.env` differs from the base repository's and the user runs `shogun provision --check`
- **THEN** a line reports `.env` as differing, no file is copied or linked, and the command exits with code 3

#### Scenario: File deleted from the base folder
- **WHEN** `keys/old.pem` was copied from the local folder `keys` and later deleted from the base folder
- **THEN** a line reports `keys/old.pem` as deleted in base, nothing is deleted, and the command exits with code 3

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
`shogun new`, `provision`, `trust`, `clean`, `gradle`, `gradle stop`, `xcode`, `xcode clean`, `emulator`, `simulator`, `headless`, `remove`, `repair` and `open` SHALL accept `--json`. With it, a command SHALL print one compact JSON object on a single line on standard output instead of its text, with the same exit code. Every documented field SHALL be present, `null` when it doesn't apply, and later versions SHALL only add fields.

#### Scenario: Emulator status as JSON
- **WHEN** the user runs `shogun emulator status --json` in a worktree whose emulator runs as `emulator-5556`
- **THEN** standard output is one line with an object whose `serial` is `emulator-5556` and `state` is `running`, and the command exits with code 0

#### Scenario: Headless status as JSON
- **WHEN** the user runs `shogun headless status --json` in a worktree whose headless app runs hidden
- **THEN** standard output is one line with an object whose `state` is `running`, `hidden` is `true` and `pid` is the app's process id, and the command exits with code 0

#### Scenario: Unknown option on a command without JSON
- **WHEN** the user runs `shogun hook session-start --json`
- **THEN** it prints that the option is unknown and exits with code 2

### Requirement: Errors in JSON mode
When a command run with `--json` fails before producing its result, it SHALL print the error as text on standard error, print nothing on standard output and exit with the same code as without `--json`.

#### Scenario: Outside a repository
- **WHEN** the user runs `shogun clean --json` outside a Git repository
- **THEN** standard output is empty, the error is on standard error and the exit code is 1

### Requirement: No questions in JSON mode
A command run with `--json` SHALL never ask a question. When the repository isn't approved, its post-creation commands and declared variables SHALL be skipped and listed in the result, and `trust --json` without `--yes` SHALL only report.

#### Scenario: Unapproved post-creation commands
- **WHEN** the user runs `shogun new TASK-123 --json` in a repository whose post-creation commands aren't approved
- **THEN** the worktree is created without running them, and the object lists them in `skippedPostCreateCommands`

#### Scenario: Trust without confirmation
- **WHEN** the user runs `shogun trust --json` in a repository with unapproved commands
- **THEN** the object lists the commands with `approved: false`, nothing is approved and nothing is asked

#### Scenario: Unapproved variables
- **WHEN** the user runs `shogun new TASK-123 --json` in a repository that declares `PORT` and isn't approved
- **THEN** the worktree is created without `PORT`, and the object lists `PORT` in `skippedVariables`

### Requirement: JSON result of worktree creation and provisioning
`shogun new --json` SHALL report the worktree's path and branch, the skipped post-creation commands, the names of the skipped declared variables and any post-creation failure. A post-creation failure SHALL still print the object, since the worktree exists, and exit with code 1. `shogun provision --check --json` SHALL list each reported file with its path and state, and exit with code 3 when there is any.

#### Scenario: Post-creation command fails
- **WHEN** `shogun new TASK-123 --json` creates the worktree and a post-creation command exits with code 2
- **THEN** standard output is the object with the path and a `postCreateFailure` naming the command and exit code 2, and the command exits with code 1

#### Scenario: Stale file
- **WHEN** the user runs `shogun provision --check --json` and `local.properties` differs from the base repository's
- **THEN** the object's `drift` lists `local.properties` with state `differs`, and the exit code is 3

#### Scenario: Nothing skipped
- **WHEN** `shogun new TASK-123 --json` creates a worktree in an approved repository
- **THEN** the object has empty `skippedPostCreateCommands` and `skippedVariables`

### Requirement: JSON result of cleanup
`shogun clean --json` SHALL report whether it was a dry run, the candidates with why each qualifies, the worktrees kept because an agent runs in them, the ones skipped because their status couldn't be read, and, with `--yes`, what was removed, which branches were deleted and what failed.

#### Scenario: Dry run
- **WHEN** the user runs `shogun clean --json` with one merged worktree and one where an agent runs
- **THEN** the object has `dryRun: true`, the merged one in `candidates` with reason `merged`, the other in `keptForAgents`, and empty `removed`

#### Scenario: Removal
- **WHEN** the user runs `shogun clean --yes --delete-branches --json` with one merged worktree on `feature/TASK-1`
- **THEN** the object has `dryRun: false`, its path in `removed` and `feature/TASK-1` in `deletedBranches`

### Requirement: Open a worktree in Herdr from the command line
`shogun open <target> --herdr` SHALL open the worktree identified by a task id, a path or a branch name in Herdr, as the desktop app's "Open in Herdr" does, including the main worktree, running the project's default agent or the one named by `--agent <name>` (ignoring case). With `--json` it SHALL print `path`, `branch`, `herdr` (`opened` or `focused`) and `agent` (`null` when only focused).

#### Scenario: Open by task id
- **WHEN** the user runs `shogun open TASK-123 --herdr` and Herdr doesn't have the worktree open
- **THEN** the worktree opens in Herdr with the project's default agent, the output names the path and the agent, and the command exits with code 0

#### Scenario: Already open
- **WHEN** the user runs `shogun open TASK-123 --herdr` and Herdr already has the worktree open
- **THEN** its workspace is focused, no agent is started, the output says it was already open, and the command exits with code 0

#### Scenario: Another agent
- **WHEN** the project has an additional agent named `Codex` and the user runs `shogun open TASK-123 --herdr --agent codex`
- **THEN** that agent's launch command runs in the new workspace

#### Scenario: Unknown agent
- **WHEN** the user runs `shogun open TASK-123 --herdr --agent missing`
- **THEN** nothing is opened, the error lists the project's agents, and the command exits with code 2

#### Scenario: Main worktree
- **WHEN** the target is the main worktree
- **THEN** it opens in Herdr like any other worktree

#### Scenario: No destination
- **WHEN** the user runs `shogun open TASK-123`
- **THEN** nothing is opened, the error says `--herdr` is needed, and the command exits with code 2

#### Scenario: Unknown target
- **WHEN** the target matches no worktree
- **THEN** nothing is opened and the command exits with code 1

#### Scenario: Result as JSON
- **WHEN** the user runs `shogun open TASK-123 --herdr --json` and the worktree opens
- **THEN** standard output is one line with an object whose `herdr` is `opened` and `agent` is the agent's name

### Requirement: Report each agent's state in status
`shogun status` SHALL show each agent's state whatever reports it, Herdr or Claude Code's hooks, and `shogun status --json` SHALL add `state` (`working`, `blocked`, `done`, `idle` or `null`) and `stateSource` (`herdr`, `claude-code` or `null`) to each agent, keeping `herdrState` as before.

#### Scenario: Agent state from Claude Code's hooks
- **WHEN** Claude is blocked in a worktree, as recorded by its `Notification` hook, and Herdr doesn't report it
- **THEN** the line shows `claude(blocked)` and the JSON agent has `state` `blocked`, `stateSource` `claude-code` and `herdrState` `null`

#### Scenario: Agent state from Herdr
- **WHEN** Herdr reports Claude as working
- **THEN** the JSON agent has `state` `working`, `stateSource` `herdr` and `herdrState` `working`

### Requirement: Record agent states through Claude Code's hooks
`shogun hook agent-state` SHALL read the hook's JSON from standard input and record the session's state for the worktree containing its `cwd`: working on `UserPromptSubmit`, blocked on `Notification`, done on `Stop`, and remove the record on `SessionEnd`. It SHALL print nothing, never block the session, and exit with code 0 whenever the input is valid, also outside a repository.

#### Scenario: Permission prompt
- **WHEN** Claude Code calls the hook with `hook_event_name` `Notification` from a worktree
- **THEN** the session is recorded as blocked for that worktree and nothing is printed

#### Scenario: Session ends
- **WHEN** Claude Code calls the hook with `SessionEnd`
- **THEN** the session's record is removed

#### Scenario: Outside a repository
- **WHEN** the hook's `cwd` isn't inside a Git repository
- **THEN** nothing is recorded and the command exits with code 0

#### Scenario: Invalid input
- **WHEN** standard input isn't the hook's JSON
- **THEN** the error is printed on standard error and the command exits with code 2

### Requirement: Session records stay out of the working tree
Session records SHALL be stored in the worktree's Git administrative folder, so they never appear as changes, aren't copied to other worktrees and disappear when the worktree is removed.

#### Scenario: Record written
- **WHEN** a session's state is recorded in a worktree
- **THEN** `git status` in that worktree shows no new file

### Requirement: Manage the hooks from the command line
`shogun hooks` SHALL offer `install`, `uninstall` and `status`, as specified in `claude-code-hooks`, with `--project` to target the repository's settings, `--yes` to skip confirmation and `--json` for machine-readable output.

#### Scenario: Help
- **WHEN** the user runs `shogun hooks --help`
- **THEN** the three subcommands and their options are described

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

### Requirement: Trim DerivedData to the quota
`shogun xcode trim` SHALL apply the DerivedData quota of the machine settings with the same rules as the desktop app, attributing folders to the worktrees of every saved project. It SHALL print each folder released with its size, the space freed and the usage against the quota, and name the worktrees in use when the quota is still exceeded. `--dry-run` SHALL only list what would be released, and `--json` SHALL print the same information as JSON.

#### Scenario: Over the quota
- **WHEN** the quota is 50 GB, usage is 62 GB and an unused worktree folder of 15 GB is the oldest
- **THEN** `shogun xcode trim` prints `released <path>`, `freed 15 GB` and the usage against the quota

#### Scenario: Automatic quota
- **WHEN** the quota is Automatic and resolves to 40 GB
- **THEN** it prints the usage against 40 GB, says the quota is automatic, and `--json` reports the quota mode `automatic` with its size

#### Scenario: Dry run
- **WHEN** the user runs `shogun xcode trim --dry-run` over the quota
- **THEN** it prints `would release <path>` for each candidate and deletes nothing

#### Scenario: Still exceeded
- **WHEN** the remaining folders belong to worktrees in use
- **THEN** it prints that the quota is still exceeded and names those worktrees

### Requirement: Trim without quota or provider
`shogun xcode trim` SHALL say so and delete nothing when the user chose "No quota" or when the Automatic quota can't be computed, and SHALL say the provider isn't active when the repository doesn't declare Xcode or the OS isn't macOS. In every case it SHALL exit with `0`.

#### Scenario: No quota
- **WHEN** the user chose "No quota"
- **THEN** it says that no DerivedData quota is set, deletes nothing and exits with `0`

#### Scenario: Automatic quota unknown
- **WHEN** the quota is Automatic and the free space of the DerivedData volume can't be read
- **THEN** it says the automatic quota couldn't be computed, deletes nothing and exits with `0`

#### Scenario: Provider not active
- **WHEN** the repository doesn't declare the Xcode provider
- **THEN** it says the provider isn't active and exits with `0`

### Requirement: Show the detected stacks from the command line
`shogun stacks` SHALL print one line per stack detected in the repository with its evidence and, per suggested provider, whether the repository already declares it, followed by one line per suggested mobile secret with its path, kind and suggested `link` pattern, and one line per keystore warning. Providers not supported by the platform SHALL NOT be suggested. It SHALL change nothing and exit with code 0 when nothing is detected.

#### Scenario: Stacks detected
- **WHEN** the user runs `shogun stacks` in a repository with `pom.xml` that declares no provider
- **THEN** the output lists Maven with `pom.xml` and the suggested provider `maven`

#### Scenario: Already declared
- **WHEN** the repository already declares the `maven` provider
- **THEN** the output shows it as declared and not as a suggestion

#### Scenario: Mobile secret suggested
- **WHEN** the user runs `shogun stacks` in an Android repository with an ignored `local.properties` that nothing provisions
- **THEN** a line names `local.properties`, its kind and the pattern `/local.properties` to add to `link`, and nothing is written

#### Scenario: Keystore warning
- **WHEN** `keystore.properties` contains `storeFile=../signing/release.jks`
- **THEN** a line warns that `storeFile` in `keystore.properties` points outside the repository

#### Scenario: Nothing detected
- **WHEN** no stack and no mobile secret is detected
- **THEN** the command says so and exits with code 0

#### Scenario: Outside a repository
- **WHEN** the user runs `shogun stacks` outside any Git repository
- **THEN** an error on standard error says so and the command exits with code 1

### Requirement: List Maven daemons from the command line
`shogun maven` SHALL print one line per Maven daemon with its process id, memory and, inside a Git repository, its worktree, followed by the total memory. It SHALL work outside a Git repository.

#### Scenario: Daemons running
- **WHEN** the user runs `shogun maven` while two Maven daemons run
- **THEN** each has a line with its memory and worktree, and the total memory is printed

#### Scenario: No daemons
- **WHEN** no Maven daemon is running
- **THEN** the command says so and exits with code 0

### Requirement: List and take down Compose projects from the command line
`shogun compose` SHALL print one line per Compose project of the repository's worktrees with its status and worktree. `shogun compose down` SHALL take down the projects of removed worktrees, and `shogun compose down <worktree>` the project of the given worktree, both keeping volumes unless `--volumes` is given. It SHALL report what was taken down and what failed with the reason, and exit with code 1 when the given worktree doesn't exist or the repository doesn't declare the provider.

#### Scenario: List
- **WHEN** the user runs `shogun compose` and two projects exist
- **THEN** each has a line with its status and worktree

#### Scenario: Take down orphans
- **WHEN** the user runs `shogun compose down` and the project of a removed worktree exists
- **THEN** that project is taken down, volumes are kept and the output says so

#### Scenario: Remove volumes
- **WHEN** the user runs `shogun compose down --volumes`
- **THEN** the volumes of the projects taken down are removed too

#### Scenario: Provider not declared
- **WHEN** the repository doesn't declare the Compose provider
- **THEN** an error on standard error says so, Docker isn't run and the command exits with code 1

### Requirement: List leftover Docker resources from the command line
After the projects, `shogun compose` SHALL print one line per volume, network or image left by the projects of removed worktrees, with its kind and project, followed by the `shogun compose prune` command that removes them.

#### Scenario: Leftover resources listed
- **WHEN** the user runs `shogun compose` and a removed worktree left the volume `main-repo-task-9_db-data`
- **THEN** a line names that volume, its kind and its project, and says `shogun compose prune --volumes` removes it

### Requirement: Take down Compose projects when cleaning up from the command line
With `--yes`, `shogun clean --down-compose-projects` SHALL also take down the Compose projects of the removed worktrees and remove the networks and built images those projects leave, keeping their volumes unless `--compose-volumes` is also given, and report how many projects were taken down, which resources were removed and what failed. Without the option no project SHALL be taken down and no resource removed.

#### Scenario: Projects of removed worktrees
- **WHEN** the user runs `shogun clean --yes --down-compose-projects` and a removed worktree had a project with a built image and a volume
- **THEN** that project is taken down, its image is removed, its volume is kept and the output says so

#### Scenario: Volumes too
- **WHEN** the user runs `shogun clean --yes --down-compose-projects --compose-volumes`
- **THEN** the volumes of the removed worktrees' projects are removed too

#### Scenario: Volumes option alone
- **WHEN** the user runs `shogun clean --yes --compose-volumes` without `--down-compose-projects`
- **THEN** a usage error says `--compose-volumes` needs `--down-compose-projects` and nothing is removed

#### Scenario: Without the option
- **WHEN** the user runs `shogun clean --yes`
- **THEN** no Compose project is taken down and no Docker resource is removed

#### Scenario: Option without confirmation
- **WHEN** the user runs `shogun clean --down-compose-projects` without `--yes`
- **THEN** nothing is removed or taken down, and the output also lists the projects that would be taken down

### Requirement: Start and build Compose projects from the command line
`shogun compose up [<worktree>] [--build] [-- <args>…]` SHALL start the services of the given worktree, or of the worktree containing the current directory, and `shogun compose build [<worktree>] [-- <args>…]` SHALL build them, both with the worktree's environment, passing the arguments after `--` to Docker Compose. Docker Compose's output SHALL be shown as it runs, and the command SHALL exit with Docker Compose's failure as code 1.

#### Scenario: Up in the current worktree
- **WHEN** the user runs `shogun compose up` inside worktree `TASK-123` of `main-repo`
- **THEN** the services start under project `main-repo-task-123` with the worktree's ports, and the output names the project

#### Scenario: Up with a build and a service
- **WHEN** the user runs `shogun compose up TASK-123 --build -- api`
- **THEN** `docker compose up --detach --build api` runs in that worktree with its environment

#### Scenario: Build
- **WHEN** the user runs `shogun compose build`
- **THEN** the services of the current worktree are built with its environment

#### Scenario: Unknown worktree
- **WHEN** the user runs `shogun compose up TASK-404` and no such worktree exists
- **THEN** an error on standard error says so, Docker isn't run and the command exits with code 1

#### Scenario: Failure
- **WHEN** Docker Compose fails
- **THEN** the reason is printed on standard error and the command exits with code 1

#### Scenario: JSON
- **WHEN** the user runs `shogun compose up --json`
- **THEN** standard output has one object with the worktree, the project name and whether it succeeded, and Docker Compose's own output goes to standard error

### Requirement: Remove leftover Docker resources from the command line
`shogun compose prune [--volumes] [--yes]` SHALL remove the networks and built images left by the projects of removed worktrees, and their volumes with `--volumes`. Without `--yes` it SHALL only list what it would remove and remove nothing. It SHALL report each resource removed and each failure with the reason, and exit with code 1 when any removal fails.

#### Scenario: Dry run
- **WHEN** the user runs `shogun compose prune` and a removed worktree left a network, an image and a volume
- **THEN** the output lists the network and the image as to be removed, the volume as kept, and nothing is removed

#### Scenario: Remove
- **WHEN** the user runs `shogun compose prune --yes`
- **THEN** the network and the image are removed, the volume is kept and the output says so

#### Scenario: With volumes
- **WHEN** the user runs `shogun compose prune --yes --volumes`
- **THEN** the volume is removed too

#### Scenario: Nothing left
- **WHEN** no removed worktree left any resource
- **THEN** the output says there is nothing to remove and the command exits with code 0

#### Scenario: JSON
- **WHEN** the user runs `shogun compose prune --yes --json`
- **THEN** the object lists the removed resources with their kind, name and project, and the failures with their reason

### Requirement: Report removed files and skipped links from the command line
`shogun provision [<path>]` SHALL list, after the provisioned files, each file it deleted because it was deleted from its base folder and each symbolic link it skipped inside a local folder, each marked as such. `shogun new` and the worktree creation hook SHALL print the skipped links as a note on standard error, keeping standard output unchanged.

#### Scenario: Provisioning after a deletion
- **WHEN** `keys/old.pem` was deleted from the base folder and the user runs `shogun provision`
- **THEN** a line marks `keys/old.pem` as removed

#### Scenario: Skipped link on creation
- **WHEN** the user runs `shogun new TASK-123` and the local folder `keys` holds the symbolic link `shared.pem`
- **THEN** standard output is only the worktree's path and standard error notes that `keys/shared.pem` was skipped

### Requirement: JSON result of removed files and skipped links
`shogun provision --json` SHALL add the lists `removed` and `skippedLinks`, and `shogun new --json` the list `skippedLinks`, each with paths relative to the repository root and empty when there is nothing to report. `shogun provision --check --json` SHALL report a file deleted from its base folder with state `deleted-in-base`. `shogun stacks --json` SHALL add `secrets`, each with `path`, `kind` and `pattern`, and `keystoreWarnings`, each with `file`, `key` and `value`.

#### Scenario: Provision with a removal
- **WHEN** the user runs `shogun provision --json` and `keys/old.pem` is removed
- **THEN** the object's `removed` lists `keys/old.pem` and `skippedLinks` is empty

#### Scenario: Stacks with a secret
- **WHEN** the user runs `shogun stacks --json` and `app/google-services.json` is suggested
- **THEN** `secrets` holds an entry with path `app/google-services.json`, kind `firebase-config` and pattern `/app/google-services.json`

### Requirement: Manage a worktree's headless app from the command line
`shogun headless start [<path>] [--visible] [--fresh]`, `shogun headless stop [<path>]`, `shogun headless status [<path>]` and `shogun headless capture [<path>] [--output <file>]` SHALL act on the headless app of the worktree containing the path or the current directory. `start` SHALL print the process id, `capture` the PNG's path, `status` the state. In a repository without the provider they SHALL exit with code 1 and say how to declare it.

#### Scenario: Start
- **WHEN** the user runs `shogun headless start` in a worktree of a repository that declares the provider
- **THEN** the app starts hidden, the command prints its process id and exits with code 0

#### Scenario: Capture to a file
- **WHEN** the user runs `shogun headless capture --output /tmp/example/window.png` while the app is running
- **THEN** the window's PNG is written to that path, the command prints it and exits with code 0

#### Scenario: Capture without a running app
- **WHEN** the user runs `shogun headless capture` and the worktree's app isn't running
- **THEN** the error says to run `shogun headless start` first and the command exits with code 1

#### Scenario: Status when stopped
- **WHEN** the user runs `shogun headless status` and the app isn't running
- **THEN** it prints `stopped` and exits with code 0

#### Scenario: Repository without the provider
- **WHEN** the user runs `shogun headless start` in a repository without the provider
- **THEN** an error on standard error explains how to declare `"providers": { "desktop-app": { "module": ":desktopApp" } }` and the command exits with code 1

#### Scenario: Outside a worktree
- **WHEN** the user runs `shogun headless start` outside a Git repository
- **THEN** the command says it needs a worktree and exits with code 1

### Requirement: Tell the agent how to check its desktop app
When the repository declares the desktop app provider on macOS, the context printed by `shogun hook session-start` SHALL say to check the app with `shogun headless start` and the Hot Reload MCP instead of computer use, to capture it with `shogun headless capture` instead of the MCP's screenshot, to re-read the tree after each view change, to hide it again with `shogun headless start` after `restart`, what still needs the desktop, and that for it the agent takes the desktop turn with `shogun desktop acquire --wait` first.

#### Scenario: Context with the provider
- **WHEN** a Claude Code session starts on macOS in a worktree of a repository that declares the provider
- **THEN** the context mentions `shogun headless start`, `shogun headless capture`, not to use the MCP's `take_screenshot`, hiding again after `restart`, what still needs the desktop and `shogun desktop acquire --wait` for it

#### Scenario: Context on another platform
- **WHEN** a session starts on Linux in a worktree of a repository that declares the provider
- **THEN** the context has no line about `shogun headless`

### Requirement: Report the desktop turn in status
`shogun status` SHALL show on the line of the worktree that holds the desktop turn that it holds it and until when, and on the line of each waiting worktree its place in line. `shogun status --json` SHALL add `desktop` to every worktree object: `null`, `{ "role": "holder", "since", "expiresAt" }` or `{ "role": "waiting", "position", "since" }`, with ISO-8601 times.

#### Scenario: Holder and waiter
- **WHEN** `TASK-1` holds the desktop turn until 14:10 and `TASK-2` is first in line
- **THEN** `TASK-1`'s line says it holds the desktop until 14:10 and `TASK-2`'s line says it waits for it, first in line

#### Scenario: JSON
- **WHEN** the user runs `shogun status --json` while `TASK-1` holds the turn and no other worktree waits
- **THEN** `TASK-1`'s object has `desktop.role` `holder` with its `expiresAt`, and every other worktree object has `desktop: null`

#### Scenario: Turn held by another repository
- **WHEN** a worktree of `api` holds the turn and the user runs `shogun status` in `main-repo`
- **THEN** no line of `main-repo` mentions the desktop
