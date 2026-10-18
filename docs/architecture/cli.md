# Command-line tool

The `shogun` command runs ShogunAi's worktree operations from a terminal, a script or an agent hook, without opening the desktop window. It lives in the **`cli`** module (`cli/src/main/kotlin/app/luxion/shogunai/cli`) and calls the same `core` use cases as the app, so a worktree created or cleaned from the command line ends up exactly as if the app had done it.

## Commands

What each command does, its options and its output are in the [command-line reference](../guide/cli.md). This table maps each command to the `core` code it runs and notes the details that only matter when changing it.

| Command | Runs | Implementation notes |
|---|---|---|
| `new` | `ResolveStartPointUseCase`, then `CreateWorktreeUseCase` or `CreateWorktreeFromBranchUseCase`, with `PrepareWorktreeEnvironmentUseCase` and `WriteEnvironmentFileUseCase` as the environment preparer | Without `--from`, the start point is `HEAD` resolved in the working directory, so a worktree created from a linked worktree branches from that worktree, as `git worktree add -b` does. An unknown ref fails before anything is created. |
| `provision` | `ProvisionWorktreeUseCase` | |
| `status` | `ListWorktreesUseCase`, `ResolveMainBranchUseCase`, `ReadWorktreeStatusUseCase`, `classifyOrigin` | |
| `clean` | `selectCleanupCandidates`, `CleanUpWorktreesUseCase`, `CloseHerdrWorkspacesUseCase`, and per option `StopGradleDaemonsUseCase`, `StopWorktreeEmulatorUseCase`, `DeleteSimulatorsUseCase`, `DeleteOrphanedDerivedDataUseCase` | Whatever removal deletes is read first: the Gradle daemons (for `idleDaemonsOfRemovedWorktrees`), the running emulators' serials (`ReadEmulatorStatesUseCase`, then `StopWorktreeEmulatorUseCase.stopSerials`), the simulators' UDIDs and the orphaned DerivedData. Orphaned simulators are looked for again after removing, so the simulator of a worktree whose folder was already gone is caught as an orphan; with only orphans the command still runs. A failed daemon stop, emulator stop or Herdr close goes to standard error without changing the exit code; a DerivedData folder that can't be deleted exits with `1`. |
| `gradle`, `gradle stop` | `ListGradleDaemonsUseCase`, `CheckGradleBuildCacheUseCase`, `StopGradleDaemonsUseCase` | Works outside a repository. Kotlin daemons are kept while a Gradle build runs. |
| `xcode`, `xcode clean` | `ListDerivedDataUseCase`, `DeleteOrphanedDerivedDataUseCase` | Without the provider or off macOS it exits with `0`. |
| `env` | `ResolveWorktreeEnvironmentUseCase` (reserving), `formatEnvironment` | |
| `emulator` | `StartWorktreeEmulatorUseCase`, `StopWorktreeEmulatorUseCase`, `ReadEmulatorStatesUseCase` | `status` never reserves a port. Without the provider: `EmulatorProviderNotDeclared`. |
| `simulator` | `StartWorktreeSimulatorUseCase`, `StopWorktreeSimulatorUseCase`, `ReadSimulatorStatesUseCase`, `KmpTestWiring` | `status` never creates a simulator. Without the provider: `IosSimulatorProviderNotDeclared`; off macOS: `IosSimulatorsNotSupported`. |
| `simulator gradle-init` | `KmpTestWiring` | `install` asks on standard input (`readInput`) unless `--yes`. |
| `hook worktree-create` | `CreateWorktreeUseCase`, or the existing worktree | Reads `name` and `cwd` from the hook's JSON; without a project the worktree goes to `<repo>/.claude/worktrees`. The path must be the only line on standard output. |
| `hook worktree-remove` | `RemoveWorktreeUseCase` (forced), `DeleteMergedBranchUseCase`, `CloseHerdrWorkspacesUseCase` | Forcing the removal is what Claude Code would do without the hook. |
| `hook session-start` | `ResolveWorktreeEnvironmentUseCase` | `CLAUDE_ENV_FILE` is read through an injected environment lookup. An unreadable `.shogunai.json` exits with `1` before writing. |
| `hook herdr` | `ProvisionWorktreeUseCase` on `worktree.created`, `DeleteMergedBranchUseCase` on `worktree.removed` | The event comes from `HERDR_PLUGIN_EVENT` and `HERDR_PLUGIN_EVENT_JSON`. On `worktree.removed` the repository comes from `data.workspace.worktree.repo_root`, or else from the project whose worktrees folder contains the path, and Herdr is never called. On `worktree.created` outside the project's worktrees folder it runs `$HERDR_BIN_PATH notification show` (`herdr` when unset). Missing or invalid variables exit with `2`. |
| `help`, `version` | | `version` prints `BuildInfo.VERSION`, the same as the app's. |

Errors are printed from the `WorktreeError` message. The hook commands receive standard input through `ShogunCli`'s `readInput`, so tests pass the JSON directly.

## Repository and project

Every command starts from a directory (the current one, or the path given to `provision`):

1. `ResolveRepositoryUseCase` finds the base repository with `git rev-parse --git-common-dir`, so it works from the main worktree, any subfolder or a linked worktree.
2. The project is the one in `~/.shogunai/projects.json` whose base repository path is the same directory, compared by canonical path (so `/tmp/…` and `/private/tmp/…` on macOS match). It provides the worktrees folder, the local files and the main branch.
3. Without a project, the CLI uses a configuration with no local files and a detected main branch. Only `new` needs a worktrees folder; it asks for `--worktrees-root <dir>` (relative paths are resolved against the current directory).

## Structure

- `CliCommand.kt` — `parseArguments(args)` turns the arguments into a sealed `CliCommand` or a usage error. Hand-written: a dozen commands with about twenty options, each with its own small rules (`--from` not with `--branch`, `--avd` only with `start`, aliases such as `-y`), don't justify a parsing library and its startup cost.
- `ShogunCli.kt` — `ShogunCli(ports…, workingDirectory, out, err).run(args): Int` executes a command. It receives the ports and the output streams, so `ShogunCliTest` drives every command with `core`'s test fixtures and string buffers.
- `Main.kt` — `main` and `runShogun(args)` build `ShogunCli` with the real implementations (`ProcessBuilderShellCommandExecutor`, `NioFileManager`, `JsonProjectRepository`, `RepositoryProvisioningFiles`, `ProcessGradleDaemonSource`, `NioEnvironmentStateStore`, `SocketPortProbe`, `LocalAndroidSdk`, `SimctlAppleSimulators`, `FileKmpTestWiring`, `HerdrConfigFile`, `MacDerivedDataSource`; `ShogunCli` defaults its `HerdrClient` to `CliHerdrClient`), and resolves environments through `MachineProviderContributions`, which reads a repository's simulator settings from its project.

The module applies Gradle's `application` plugin, so `./gradlew :cli:run --args="status"` runs it during development and `:cli:distZip` builds the standalone zip.

## How it's shipped

The packaged desktop app has a jlink runtime without a `java` binary, so the CLI can't be started by a script reusing it. Instead, **the app's own launcher is the command**: `desktopApp`'s `main(args)` calls `runShogun` and exits when it receives arguments (ignoring macOS's legacy `-psn_…` argument), and opens the window otherwise. The CLI path never loads AWT or Compose, so no window or Dock icon appears, and it starts in about a tenth of a second.

| Platform | How `shogun` is installed |
|---|---|
| macOS (Homebrew) | The Cask links `ShogunAi.app/Contents/MacOS/ShogunAi` as `shogun`. |
| Linux (`.deb`) | The launcher is `/opt/shogunai/bin/ShogunAi`; link it as `shogun` by hand ([Installation](../guide/installation.md#the-shogun-command) shows how). |
| Windows, or anywhere with Java 21+ | `shogun-<version>.zip` from the release: `bin/shogun` and `bin/shogun.bat`. The Windows `.msi` launcher has no console, so it can't print. |

The reasoning and the startup measurements are in the `shogun-cli` change's `design.md` (`openspec/changes/archive/`).
