## MODIFIED Requirements

### Requirement: Find the repository and its project
Every command SHALL work on the Git repository that contains the current directory, or the path it's given, resolved to its main worktree even when run from a linked worktree. When the desktop app has a project for that repository, the command SHALL use its worktrees folder, local files and main branch. `project list` and `app` without a path are the exceptions: they work outside a Git repository.

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
`shogun new <task-id> [--fix] [--from <ref>]` SHALL create a worktree on a new `feature/` (or `fix/`) branch, and `shogun new --branch <branch>` one from an existing local branch, with the same folder, provisioning, validation and rollback as the desktop app. The new branch SHALL start at `<ref>` when `--from` is given, and otherwise at the HEAD of the worktree containing the current directory.

#### Scenario: New branch
- **WHEN** the user runs `shogun new TASK-123` in a repository with a project
- **THEN** a worktree on `feature/TASK-123` is created in the project's worktrees folder, provisioned, and its path is the only line on standard output

#### Scenario: Start commit reported
- **WHEN** the user runs `shogun new TASK-123 --from origin/develop` and `origin/develop` points to `a41d265 Merge pull request #12`
- **THEN** standard error says the branch starts from `origin/develop` at `a41d265 Merge pull request #12`, and standard output has only the path

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
- **THEN** a worktree for the existing branch is created and provisioned, its path is printed, and no start commit is reported

#### Scenario: No project and no worktrees folder
- **WHEN** the repository has no project in the desktop app and the user runs `shogun new TASK-123` without `--worktrees-root`
- **THEN** the command explains that a worktrees folder is needed, creates nothing and exits with code 2

#### Scenario: Creation fails
- **WHEN** creation fails, e.g. because the destination folder already exists
- **THEN** the error is printed on standard error and the command exits with code 1

### Requirement: Show worktree status from the command line
`shogun status` SHALL print one line per worktree of the repository with its path, branch (or detached), origin (ShogunAi, Claude Code, Herdr or other), uncommitted changes, divergence from its upstream and from its base, and whether it's merged into its base or the main branch, the same information the desktop app shows.

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

#### Scenario: Worktree merged into its recorded base
- **WHEN** a worktree's branch is merged into its recorded base `epic/redesign`, which isn't in the main branch yet
- **THEN** its line says it's merged, and its JSON has `status.merged: true`

### Requirement: List Gradle daemons from the command line
`shogun gradle` SHALL print one line per Gradle or Kotlin daemon with its process id, kind, Gradle version, memory, state and, inside a Git repository, its worktree, followed by the total memory. It SHALL work outside a Git repository. Inside a repository that declares the Gradle provider it SHALL also print the build cache notice when it applies.

#### Scenario: Daemons running
- **WHEN** the user runs `shogun gradle` in a repository while an idle and a busy Gradle daemon are running
- **THEN** each one has a line with its state and worktree, and the total memory is printed

#### Scenario: Busy daemon held by another session
- **WHEN** a daemon is busy with `./gradlew benchRun` in the `api` worktree, started two hours ago by the session of the `apps` worktree, and the user runs `shogun gradle`
- **THEN** under the daemon's line a line names the client's process id, `benchRun`, the `api` worktree's directory, about two hours, and the `apps` worktree's session

#### Scenario: Holder in JSON
- **WHEN** the user runs `shogun gradle --json` while a daemon is busy and another is idle
- **THEN** the busy daemon has a `holder` with its directory, start time, client process id, tasks and session when known, and the idle one has `"holder": null`

#### Scenario: No daemons
- **WHEN** no daemon is running
- **THEN** the command says so and exits with code 0

#### Scenario: Outside a repository
- **WHEN** the user runs `shogun gradle` outside any Git repository
- **THEN** the daemons are listed without a worktree column

#### Scenario: Build cache off
- **WHEN** the repository declares the Gradle provider and doesn't enable the build cache
- **THEN** the output ends with the build cache notice

### Requirement: Print a worktree's environment from the command line
`shogun env [<path>] [--format sh|dotenv|json]` SHALL resolve the environment of the worktree containing the path, or the current directory, and print it: `sh` (the default) as `export NAME='value'` lines safe for `eval`, `dotenv` as `NAME=value` lines, `json` as an object. An empty environment SHALL print nothing on standard output and exit with code 0.

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
`shogun hook session-start` SHALL read the hook's JSON from standard input and, inside a Git repository, print the short workflow rules of `shogun guide` that apply to that location. In a group worktree it SHALL also add the group's context, and in a multirepo folder it SHALL print the multirepo's context. Anywhere else outside a Git repository it SHALL write and print nothing and exit with code 0.

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
`shogun emulator start [<path>] [--avd <name>] [--no-window]`, `shogun emulator stop [<path>]` and `shogun emulator status [<path>]` SHALL act on the emulator of the worktree containing the path or the current directory, printing its serial and state, or `not acquired` when the worktree holds no emulator. In a repository that doesn't declare the emulator provider they SHALL fail with exit code 1 and say how to declare it.

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

### Requirement: Tell Claude how to start its emulator
When the repository declares the emulator provider, the context line printed by `shogun hook session-start` SHALL say how to get the worktree's emulator: when the worktree holds one, that it is `ANDROID_SERIAL` and that `shogun emulator start` starts it; otherwise, that `shogun emulator start` acquires and starts it and prints the `ANDROID_SERIAL` to export.

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
`shogun simulator start|stop|status [<path>] [--device <type>] [--runtime <runtime>]` SHALL act on the simulator of the worktree containing the path or the current directory, printing its UDID and state, or `not acquired` when the worktree holds none; `--device` and `--runtime` only apply when the simulator has to be created. In a repository without the provider the commands SHALL fail with exit code 1 and say how to declare it.

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

### Requirement: Remove a worktree from the command line
`shogun remove <target>` SHALL remove the linked worktree identified by a task id, a path or a branch name. It SHALL refuse a worktree with uncommitted or untracked changes, a locked worktree, or one with a running agent, unless `--force` is given; it SHALL never remove the main worktree. It SHALL stop the resources the repository's providers keep for that worktree, as cleanup does.

#### Scenario: Remove by task id
- **WHEN** the user runs `shogun remove TASK-123` and the worktree is clean
- **THEN** the worktree is removed, its emulator and simulator are stopped if the providers are on, its branch is kept, and the command exits with code 0

#### Scenario: Changes without force
- **WHEN** the worktree has uncommitted changes and the user runs `shogun remove TASK-123`
- **THEN** nothing is removed, the error says the worktree has changes and suggests `--force`, and the command exits with code 1

#### Scenario: Locked without force
- **WHEN** the worktree is locked and the user runs `shogun remove TASK-123`
- **THEN** nothing is removed, the error says the worktree is locked, with the lock reason, and suggests `--force`, and the command exits with code 1

#### Scenario: Locked with force
- **WHEN** the worktree is locked and the user runs `shogun remove TASK-123 --force`
- **THEN** the worktree is removed and the command exits with code 0

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

#### Scenario: Delete a branch merged into its recorded base
- **WHEN** the user runs `shogun remove TASK-123 --delete-branch` and the branch is merged into its recorded base `epic/redesign` but not into the main branch
- **THEN** the worktree and its branch are removed

### Requirement: Tell the agent how to check its desktop app
When the repository declares the desktop app provider on macOS, the context printed by `shogun hook session-start` SHALL say in one line to check the app hidden with `shogun headless start` instead of computer use, and to read `shogun guide resources` for how to drive and capture it.

#### Scenario: Context with the provider
- **WHEN** a Claude Code session starts on macOS in a worktree of a repository that declares the provider
- **THEN** the context has one line that mentions `shogun headless start` and `shogun guide resources`

#### Scenario: Full rule in the guide
- **WHEN** the user runs `shogun guide resources` on macOS in a worktree of a repository that declares the provider
- **THEN** the output mentions `shogun headless capture`, not to use the MCP's `take_screenshot`, hiding again after `restart`, what still needs the desktop and `shogun desktop acquire --wait` for it

#### Scenario: Context on another platform
- **WHEN** a session starts on Linux in a worktree of a repository that declares the provider
- **THEN** the context has no line about `shogun headless`

## ADDED Requirements

### Requirement: Commands that work in a multirepo folder
`shogun new`, `shogun status`, `shogun open`, `shogun clean` and `shogun multirepo` SHALL also work in a multirepo folder, which isn't a Git repository, acting on the multirepo and its groups.

#### Scenario: Status in a multirepo folder
- **WHEN** the user runs `shogun status` in a multirepo folder, which isn't a Git repository
- **THEN** the command shows the multirepo's groups instead of failing

### Requirement: Report the new worktree's path and start commit
On success, `shogun new` SHALL print the worktree's absolute path alone on standard output and, for a new branch, the commit it starts at, with its short hash and subject, and the `--from` ref when given, on standard error.

#### Scenario: Start commit and ref reported
- **WHEN** the user runs `shogun new TASK-123 --from origin/develop` and `origin/develop` points to `a41d265 Merge pull request #12`
- **THEN** standard error says the branch starts from `origin/develop` at `a41d265 Merge pull request #12`, and standard output has only the path

#### Scenario: No start commit for an existing branch
- **WHEN** the user runs `shogun new --branch hotfix/login`
- **THEN** its path is printed and no start commit is reported

### Requirement: Worktree status as JSON
`shogun status --json` SHALL print the information of `shogun status`, plus each worktree's HEAD, its divergence from the main branch and its last commit time, as a single JSON document on standard output. Later versions SHALL only add fields to it, and never rename, remove or change the type of one.

#### Scenario: One JSON document
- **WHEN** the user runs `shogun status --json` in a repository with the main worktree, a merged worktree and one with two uncommitted changes
- **THEN** standard output is one JSON object with `repository`, `mainBranch` and a `worktrees` array with one object per worktree, where the main one has `main: true` and `origin: null`, the merged one has `status.merged: true`, and the other has `status.uncommittedChanges: 2`

#### Scenario: Every field present
- **WHEN** the user runs `shogun status --json` and a worktree's folder no longer exists, and another worktree has a detached HEAD
- **THEN** the first has `folderMissing: true` and `status: null`, and the second has `branch: null`; every field is present in every worktree object

### Requirement: Show the build holding a busy Gradle daemon
Under each busy daemon with a holding build, `shogun gradle` SHALL print the build: the client's process id and tasks when known, its directory, how long it has been running and the agent session and worktree that started it when known. `shogun gradle --json` SHALL add a `holder` object to each daemon, or `null`.

#### Scenario: Holding build printed
- **WHEN** a daemon is busy with `./gradlew benchRun` in the `api` worktree, started two hours ago by the session of the `apps` worktree, and the user runs `shogun gradle`
- **THEN** under the daemon's line a line names the client's process id, `benchRun`, the `api` worktree's directory, about two hours, and the `apps` worktree's session

#### Scenario: Holder object in JSON
- **WHEN** the user runs `shogun gradle --json` while a daemon is busy and another is idle
- **THEN** the busy daemon has a `holder` with its directory, start time, client process id, tasks and session when known, and the idle one has `"holder": null`

### Requirement: Leave out variables awaiting approval in `shogun env`
`shogun env` SHALL only print declared variables while the repository is approved (see `worktree-environment`). When some await approval, a note naming them and `shogun trust` SHALL go to standard error.

#### Scenario: Repository not approved
- **WHEN** the user runs `shogun env` in a worktree of a repository that declares `PORT` and isn't approved
- **THEN** `PORT` isn't printed, standard error names `PORT` and `shogun trust`, and the command exits with code 0

### Requirement: Deliver a registered project's environment at session start
Only when the repository is registered as a project SHALL `shogun hook session-start` resolve the environment of the worktree containing its `cwd` field, append one `export` line per variable to the file named by `CLAUDE_ENV_FILE`, and print a context line naming the variables without their values. Declared variables awaiting approval SHALL be named in the context as awaiting the user's approval with `shogun trust`, and not delivered.

#### Scenario: Environment delivered
- **WHEN** Claude Code starts a session in a worktree of a registered, approved project whose `PORT` resolves to 20001
- **THEN** `export PORT='20001'` is appended to `$CLAUDE_ENV_FILE` and the context says this worktree's environment has `PORT` without showing `20001`

#### Scenario: Unregistered repository
- **WHEN** Claude Code starts a session in a cloned repository that isn't registered as a project and whose `.shogunai.json` declares `env`
- **THEN** nothing is written to `$CLAUDE_ENV_FILE`, no environment is resolved nor reserved, and the context has only the workflow rules

#### Scenario: Awaiting approval
- **WHEN** a session starts in a worktree of a registered project that declares `PORT` and isn't approved
- **THEN** nothing about `PORT` is written to `$CLAUDE_ENV_FILE`, and the context names `PORT` as awaiting the user's approval with `shogun trust`

### Requirement: Acquire the emulator when starting it
`shogun emulator start` SHALL acquire the emulator from the pool when needed, print on standard error any emulator released to make room, and print the `export ANDROID_SERIAL=…` line to run when the emulator was just acquired.

#### Scenario: Acquired on start
- **WHEN** the user runs `shogun emulator start` in a worktree that holds no emulator and the pool has room
- **THEN** a console port is reserved, the emulator boots, and the output includes its serial and `export ANDROID_SERIAL=emulator-<port>`

#### Scenario: Emulator released to make room
- **WHEN** the user runs `shogun emulator start` in a worktree that holds no emulator and the pool released another worktree's idle emulator to make room
- **THEN** standard error names the released emulator

### Requirement: Warn that the adb server is shared
When the repository declares the emulator provider, the context printed by `shogun hook session-start` SHALL warn that the adb server is shared with other worktrees' agents, so the agent must not restart it nor pick a device other than `ANDROID_SERIAL`.

#### Scenario: Warning in the context
- **WHEN** a Claude Code session starts in a worktree of a repository that declares the emulator provider
- **THEN** the context line says not to run `adb kill-server` or `adb start-server` and not to pass `-s`, `-e` or `-d` to adb

### Requirement: Acquire the simulator when starting it
`shogun simulator start` SHALL acquire the simulator from the pool when needed, print on standard error any simulator released to make room, and print the `export IOS_SIMULATOR_UDID=…` line to run when it was just acquired.

#### Scenario: Acquired on start
- **WHEN** the user runs `shogun simulator start` in a worktree that holds no simulator and the pool has room
- **THEN** the simulator is created and booted, and the output includes `export IOS_SIMULATOR_UDID=<UDID>`

#### Scenario: Simulator released to make room
- **WHEN** the user runs `shogun simulator start` in a worktree that holds no simulator and the pool released another worktree's idle simulator to make room
- **THEN** standard error names the released simulator

### Requirement: Say whether KMP tests use the simulator
`shogun simulator status` SHALL also say whether the repository's KMP tests are wired to the simulator, only when the repository applies the Kotlin Multiplatform plugin.

#### Scenario: Unwired Kotlin Multiplatform repository
- **WHEN** the user runs `shogun simulator status` in a Kotlin Multiplatform repository and no build script mentions `IOS_SIMULATOR_UDID`
- **THEN** the output includes the state and says the KMP tests aren't wired, pointing to `shogun simulator gradle-init install`

#### Scenario: Not Kotlin Multiplatform
- **WHEN** the user runs `shogun simulator status` in a repository whose build scripts don't apply the Kotlin Multiplatform plugin
- **THEN** the output is the simulator's state alone, without any line about KMP tests

### Requirement: Delete the branch of a removed worktree
With `--delete-branch`, `shogun remove` SHALL delete the branch only when merged into its base or the main branch, and keep it otherwise, saying why; with `--force-delete-branch` it SHALL delete the branch even if unmerged. A kept branch SHALL NOT make the command fail. With `--json`, `branchDeleted` SHALL name the deleted branch or be `null`, and `branchKeptReason` SHALL say why a requested deletion didn't happen or be `null`.

#### Scenario: Merged branch deleted
- **WHEN** the user runs `shogun remove TASK-123 --delete-branch` and the branch is merged into the main branch
- **THEN** the worktree and its branch are removed

#### Scenario: Unmerged branch kept without failing
- **WHEN** the user runs `shogun remove TASK-123 --delete-branch` and the branch isn't merged into the main branch
- **THEN** the worktree is removed, the branch is kept with a message saying it isn't merged, and the command exits with code 0

#### Scenario: Unmerged branch force-deleted
- **WHEN** the user runs `shogun remove TASK-123 --force-delete-branch` and the branch isn't merged into the main branch
- **THEN** the worktree and the branch are removed

#### Scenario: Branch fields in JSON
- **WHEN** the user runs `shogun remove TASK-123 --delete-branch --json` and the branch is kept
- **THEN** the object has `branchDeleted: null` and `branchKeptReason` with the reason

### Requirement: Full rule for checking the desktop app
When the repository declares the desktop app provider on macOS, `shogun guide resources` SHALL keep the full rule for checking the app: drive it with the Hot Reload MCP, capture it with `shogun headless capture` instead of the MCP's screenshot, re-read the tree after each view change, hide it again with `shogun headless start` after `restart`, and say what still needs the desktop and that for it the agent takes the desktop turn with `shogun desktop acquire --wait` first.

#### Scenario: Full rule printed
- **WHEN** the user runs `shogun guide resources` on macOS in a worktree of a repository that declares the provider
- **THEN** the output mentions `shogun headless capture`, not to use the MCP's `take_screenshot`, hiding again after `restart`, what still needs the desktop and `shogun desktop acquire --wait` for it
