# Command-line tool

The `shogun` command runs ShogunAi's worktree operations from a terminal, a script or an agent hook, without opening the desktop window. It lives in the **`cli`** module (`cli/src/main/kotlin/app/luxion/shogunai/cli`) and calls the same `core` use cases as the app, so a worktree created or cleaned from the command line ends up exactly as if the app had done it.

## Commands

| Command | What it does | Output |
|---|---|---|
| `shogun new <task-id> [--fix]` | Creates a worktree on a new `feature/` (or `fix/`) branch, provisioned like the app does (`CreateWorktreeUseCase`). | The worktree's absolute path, alone on standard output. |
| `shogun new --branch <branch>` | Creates a worktree for an existing local branch (`CreateWorktreeFromBranchUseCase`). | Same. |
| `shogun provision [<path>]` | Copies the local files and the repository's provisioning into an existing worktree, e.g. one created by another tool (`ProvisionWorktreeUseCase`). Defaults to the current directory. | One copied relative path per line. |
| `shogun status` | Lists every worktree of the repository with its state. | One line per worktree: path, branch, then details (`main`, `origin=shogunai\|claude-code\|other`, `locked`, `changes=N`, `ahead=N behind=N` against the upstream, `merged`, `folder-missing`). |
| `shogun clean [--yes] [--delete-branches] [--stop-gradle-daemons] [--stop-emulators] [--delete-simulators]` | Selects the same worktrees as the app's "Clean up" (`selectCleanupCandidates`) and only lists them; with `--yes` removes them (`CleanUpWorktreesUseCase`), and with `--delete-branches` also deletes their merged branches. With `--stop-gradle-daemons` it lists the Gradle daemons before removing anything and afterwards stops the idle ones whose last build was in a removed worktree (`idleDaemonsOfRemovedWorktrees`, `StopGradleDaemonsUseCase`); a daemon that can't be listed or stopped is reported on standard error without changing the exit code. `--stop-emulators` does the same with the running emulators of the removed worktrees, whose serials are read before removing, since removal deletes their reservations (`ReadEmulatorStatesUseCase`, `StopWorktreeEmulatorUseCase.stopSerials`). `--delete-simulators` deletes the iOS simulators of the removed worktrees, read the same way, and the orphaned ShogunAi simulators (`DeleteSimulatorsUseCase`), looked for again after removing so that the simulator of a worktree whose folder was already gone, unreadable before, is caught as an orphan; with only orphans to delete the command still runs. | `would remove …` / `would stop Gradle daemon …`, or `removed …` / `kept branch …` / `stopped …` / `kept …`, failures on standard error. |
| `shogun gradle` | Lists the Gradle and Kotlin daemons on the machine (`ListGradleDaemonsUseCase`). Works outside a repository; inside one, it attributes each Gradle daemon to a worktree and, if the repository declares the Gradle provider without `org.gradle.caching=true`, ends with the build cache notice (`CheckGradleBuildCacheUseCase`). | One line per daemon: pid, kind and version, `memory=`, `state=busy\|idle\|unknown` (with `idle-for=`), `worktree=<path>\|removed\|other-project`; then `total memory=… daemons=N`. |
| `shogun gradle stop [<pid>...]` | Stops every idle daemon, or only the given ones (`StopGradleDaemonsUseCase`). Busy and unknown daemons are kept, and Kotlin daemons too while a Gradle build runs. A pid that isn't a daemon, or a failed `kill`, exits with `1`. | `stopped <pid> …` / `kept <pid> … (<reason>)`. |
| `shogun hook worktree-create` | Claude Code's `WorktreeCreate` hook: reads the hook's JSON from standard input (`name`, `cwd`), creates a provisioned worktree for `name` on `feature/<name>` in the repository of `cwd` (the project's worktrees folder, or `<repo>/.claude/worktrees` without a project), or reuses it if it already exists. | The worktree path, the only line on standard output, as the hook requires. |
| `shogun hook worktree-remove` | Claude Code's `WorktreeRemove` hook: reads `worktree_path` from standard input, removes that linked worktree with `git worktree remove --force` (what Claude Code would do without the hook) and deletes its branch only if merged (`DeleteMergedBranchUseCase`). | Nothing; a kept branch is noted on standard error. |
| `shogun env [<path>] [--format sh\|dotenv\|json]` | Resolves the environment of the worktree containing the path or the current directory (`ResolveWorktreeEnvironmentUseCase`, reserving ports the first time) and prints it with `formatEnvironment`. An empty environment prints nothing. | `sh` (default): `export NAME='value'` lines for `eval "$(shogun env)"`; `dotenv`; `json`. |
| `shogun emulator start\|stop\|status [<path>] [--avd <name>] [--no-window]` | Acts on the Android emulator of the worktree containing the path or the current directory, for repositories that declare the emulator provider (`EmulatorProviderNotDeclared`, exit `1`, otherwise). `start` reserves the console port if needed, starts the AVD (`--avd` and `--no-window` override the project's machine settings for this start) and waits for the boot (`StartWorktreeEmulatorUseCase`); `stop` stops it; `status` reads it without reserving. | `emulator-5556 running\|booting\|stopped`, or a hint when no port is reserved yet. |
| `shogun simulator start\|stop\|status [<path>] [--device <type>] [--runtime <runtime>]` | Acts on the iOS simulator of the worktree containing the path or the current directory, on macOS, for repositories that declare the iOS simulator provider (`IosSimulatorProviderNotDeclared` / `IosSimulatorsNotSupported`, exit `1`, otherwise). `start` creates the simulator if needed (`--device` and `--runtime` only apply then) and boots it, waiting until it's booted; `stop` shuts it down; `status` reads it without creating it and says whether the repository's KMP tests are wired to it (`KmpTestWiring`), showing the snippet when they aren't. | `<UDID> booted\|booting\|shutdown  <name>`, or a hint when it doesn't exist yet. |
| `shogun simulator gradle-init install\|uninstall [--yes]` | Installs or removes the machine-wide Gradle init script that passes `IOS_SIMULATOR_UDID` to every project's KMP simulator tests. `install` prints the path and the script and asks `[y/N]` on standard input unless `--yes` is given; anything but `y` installs nothing. | `Installed <path>`, `Nothing installed.` or `Removed <path>`. |
| `shogun hook session-start` | Claude Code's `SessionStart` hook: reads `cwd` from the hook's JSON, resolves that worktree's environment, appends it as `export` lines to the file in `CLAUDE_ENV_FILE` (read through an injected lookup) and prints one line of context for Claude, which also says how to start the worktree's emulator, or boot its iOS simulator and use it with `xcodebuild -destination "id=$IOS_SIMULATOR_UDID"`, when the repository declares those providers. Does nothing outside a repository or without an environment; an unreadable `.shogunai.json` exits with `1` before writing. | `ShogunAi environment of worktree <name>: PORT=20001, …` |
| `shogun help`, `shogun --version` | Usage and version (`BuildInfo.VERSION`, the same as the app's). | |

Exit codes: `0` success, `1` the operation failed (error on standard error, from the `WorktreeError` message), `2` wrong usage (including hook input that isn't the expected JSON). The hook commands receive standard input through `ShogunCli`'s `readInput`, so tests pass the JSON directly. How to configure them, and recipes for other agents, are in [Using with agents](../agents.md). Output is plain English text; only `new`'s single line is meant to be consumed by scripts for now.

## Repository and project

Every command starts from a directory (the current one, or the path given to `provision`):

1. `ResolveRepositoryUseCase` finds the base repository with `git rev-parse --git-common-dir`, so it works from the main worktree, any subfolder or a linked worktree.
2. The project is the one in `~/.shogunai/projects.json` whose base repository path is the same directory, compared by canonical path (so `/tmp/…` and `/private/tmp/…` on macOS match). It provides the worktrees folder, the local files and the main branch.
3. Without a project, the CLI uses a configuration with no local files and a detected main branch. Only `new` needs a worktrees folder; it asks for `--worktrees-root <dir>` (relative paths are resolved against the current directory).

## Structure

- `CliCommand.kt` — `parseArguments(args)` turns the arguments into a sealed `CliCommand` or a usage error. Hand-written: four commands and a few flags don't justify a parsing library and its startup cost.
- `ShogunCli.kt` — `ShogunCli(ports…, workingDirectory, out, err).run(args): Int` executes a command. It receives the ports and the output streams, so `ShogunCliTest` drives every command with `core`'s test fixtures and string buffers.
- `Main.kt` — `main` and `runShogun(args)` build `ShogunCli` with the real implementations (`ProcessBuilderShellCommandExecutor`, `NioFileManager`, `JsonProjectRepository`, `RepositoryProvisioningFiles`, `ProcessGradleDaemonSource`, `NioEnvironmentStateStore`, `SocketPortProbe`, `LocalAndroidSdk`, `SimctlAppleSimulators`, `FileKmpTestWiring`), and resolves environments through `MachineProviderContributions`, which reads a repository's simulator settings from its project.

The module applies Gradle's `application` plugin, so `./gradlew :cli:run --args="status"` runs it during development and `:cli:distZip` builds the standalone zip.

## How it's shipped

The packaged desktop app has a jlink runtime without a `java` binary, so the CLI can't be started by a script reusing it. Instead, **the app's own launcher is the command**: `desktopApp`'s `main(args)` calls `runShogun` and exits when it receives arguments (ignoring macOS's legacy `-psn_…` argument), and opens the window otherwise. The CLI path never loads AWT or Compose, so no window or Dock icon appears, and it starts in about a tenth of a second.

| Platform | How `shogun` is installed |
|---|---|
| macOS (Homebrew) | The Cask links `ShogunAi.app/Contents/MacOS/ShogunAi` as `shogun`. |
| Linux (`.deb`) | The launcher is `/opt/shogunai/bin/ShogunAi`; link it as `shogun` by hand (the README shows how). |
| Windows, or anywhere with Java 21+ | `shogun-<version>.zip` from the release: `bin/shogun` and `bin/shogun.bat`. The Windows `.msi` launcher has no console, so it can't print. |

The reasoning and the startup measurements are in the `shogun-cli` change's `design.md` (`openspec/changes/archive/`).
