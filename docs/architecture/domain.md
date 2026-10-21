# Domain layer

Lives in `core/src/main/kotlin/app/luxion/shogunai/domain`. Doesn't depend on any concrete infrastructure implementation.

## Models

### `ProjectConfig`

Configuration of a repository that worktrees are created against. It's what makes the flow reusable across projects, instead of hardcoding paths or file names.

| Field | Description |
|---|---|
| `baseRepositoryPath` | Absolute path of the main repository, from which Git is run. |
| `worktreesRoot` | Parent folder where worktrees are created. |
| `secretFiles` | "Local files": exact paths, relative to the base repository or absolute, copied into each new worktree on this machine only (e.g. `local.properties`). What the repository itself declares lives in `.shogunai.json` (see `RepositoryProvisioning`). |
| `agentLaunchConfig` | The project's default agent (`AgentLaunchConfig`, see below), used by every launch unless the user picks another one. |
| `additionalAgents` | The project's other agents, in order. `agents` is the default followed by these, and `agentNamed(displayName)` finds one by name, falling back to the default. Keeping the default in its own field means an older version, which ignores unknown keys, still launches it. |
| `simulator` | `SimulatorSettings` for this machine: the iOS simulator's device type and runtime (`null` means automatic). Only used when the repository declares the iOS simulator provider; never stored in the repository. |
| `emulator` | `EmulatorSettings` for this machine: the AVD to use (`null` means automatic) and `noWindow`. Only used when the repository declares the emulator provider; never stored in the repository. |
| `mainBranch` | Optional branch that worktrees are compared against to show their status (e.g. `develop`). `null` means it's detected from the repository (see `ResolveMainBranchUseCase`). |
| `editorOverride` | `EditorId` to open this project's worktrees with on this machine instead of the default IDE chosen in Preferences (`null` means the default). |
| `fetchBeforeCreate` | Whether the desktop app's "New worktree" dialog fetches `origin` before creating, as last chosen for this project. |

Path helpers:

- `worktreePathFor(taskId)` — `<worktreesRoot>/<taskId>`, with any `/` in the task id replaced by `-`, so a task id like `team/TASK-1` maps to one folder (`team-TASK-1`).
- `worktreePathForBranch(branch)` — directory for an existing branch: strips a known `BranchType` prefix (`feature/`, `fix/`) and replaces the remaining `/` with `-`, so `feature/TASK-123` lands in the same folder as a new-branch worktree for `TASK-123`.
- `baseSecretPath(fileName)` — the secret file inside the base repository.

`Project` pairs a `ProjectConfig` with an `id` and a display `name`; it's what the project catalog stores.

### `AgentLaunchConfig`

```kotlin
@Serializable
data class AgentLaunchConfig(
    val name: String? = null,
    val agentCommand: String = "claude",
    val useHeadroom: Boolean = true,
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: executable().replaceFirstChar { it.uppercase() }

    fun executable(): String = agentCommand.trim().substringBefore(' ').substringAfterLast('/')

    fun resolvedCommand(): String = if (useHeadroom) "headroom wrap $agentCommand" else agentCommand
}
```

A flat `data class` with default values, so it serializes with `kotlinx.serialization` without a polymorphic `SerializersModule`, and projects saved before a field existed still load. `agentCommand` may carry arguments (`claude --model opus`); `executable()` is its first word without folders. An agent is identified within its project by `displayName`: its `name`, or else its executable capitalized (`codex` → "Codex").

`WorktreeAgents` holds the per-worktree choice: a map from worktree path to agent display name, stored in `settings.json` (never in the repository). `agentFor` returns the worktree's agent, or the default; `choose` records a non-default agent and forgets the entry when the default is chosen; `prune` drops entries of the project whose worktree no longer exists or whose agent was renamed or removed.

### `BranchType`

Enum `FEATURE` / `FIX`, each with a `prefix` (`"feature"` / `"fix"`) prepended to the task ID when naming the branch: `feature/TASK-123`.

### `Worktree` / `BranchOption`

- `Worktree` — an active worktree as reported by `git worktree list`: `path`, `branch` (`null` if `detached`), `head`, `isMain`, `isBare`, `isLocked` (a `locked` line, e.g. a worktree an agent session is using) and `isPrunable` (a `prunable` line: its folder no longer exists).
- `BranchOption` — a branch offered when creating a worktree: `name`, `isCheckedOut` (already checked out in the base repository or another worktree) and `isRemote` (an `origin/` branch, named with that prefix). `withoutLocalTwins()` drops the remote branches that have a local branch with the same short name.

A `Worktree` returned by creation also carries `skippedPostCreateCommands`, the repository's post-creation commands that weren't run because they aren't approved (see [Repository trust](#repository-trust)).

### `WorktreeStatus` / `MainBranch` / `Divergence`

- `MainBranch` — the branch worktrees are compared against: its `name` (`develop`) and the `ref` used in Git commands (`origin/develop`, or `develop` when there's no remote-tracking branch).
- `Divergence` — commits `ahead` and `behind` with respect to another branch.
- `WorktreeStatus` — the state of one worktree: `uncommittedChanges`, `upstream` and `mainBranch` divergences (`null` when they don't apply), `lastCommitAt` and `neverCommitted`. `isNotStarted` is `neverCommitted`: the branch never had commits of its own (see `ReadWorktreeStatusUseCase`). `isMerged` is derived: the branch isn't "not started", has nothing the main branch lacks (`ahead == 0`) and the main branch has moved past it (`behind > 0`), so neither a freshly created branch nor one whose main branch moved on since it was created is reported as merged.

### `RepositoryProvisioning`

What the repository declares that each new worktree needs, read from files at the root of the base repository and versioned with it, so it's shared by everyone who clones it and doesn't depend on the machine:

| Field | Source |
|---|---|
| `copyPatterns` | The `copy` list of `.shogunai.json`. |
| `linkPatterns` | The `link` list of `.shogunai.json`: files symlinked to the base repository's copy instead of copied, so every worktree shares them (copied when the file system can't create links). |
| `postCreateCommands` | `hooks.postCreate` of `.shogunai.json`: commands as argv lists, run in each new or provisioned worktree once the user has approved them on this machine (see [Repository trust](#repository-trust)). A single list of strings is one command. |
| `worktreeIncludePatterns` | The lines of `.worktreeinclude` (Claude Code's file), skipping blank lines and `#` comments. |
| `providers` | The `providers` object of `.shogunai.json`: the providers the repository turns on, each keyed by its id with an object of options. `gradle` (`GradleProvider`, optional `daemonMaxHeap`, `idleTimeoutMinutes` and `kotlinDaemonMaxHeap`) makes the [Gradle daemons](#gradle-daemons) visible for the repository and, with settings, bounds the daemons of worktree builds; `emulator` (`EmulatorProvider`, optional `avd` the repository prefers) gives each worktree its own [Android emulator](#android-emulator); `ios-simulator` (`IosSimulatorProvider`, optional `device` and `runtime` preferences) gives each worktree its own [iOS simulator](#ios-simulator); `xcode` (`XcodeProvider`, optional `compilationCache`) shows and cleans up the [Xcode DerivedData](#xcode-deriveddata) and can turn on compilation caching. Unknown ids are ignored. |
| `environment` | The `env` object and `envFile` of `.shogunai.json`, as an `EnvironmentDeclaration` (see [Worktree environment](#worktree-environment)). |

`allPatterns` is the union of `copyPatterns` and `worktreeIncludePatterns` without duplicates; a file that also matches a `link` pattern is linked, not copied. Patterns use Git's ignore-pattern syntax, relative to the repository root: `local.properties` matches at any depth, `/local.properties` only at the root, `.idea/runConfigurations/` a whole folder, `.env*` a glob.

```json
{ "copy": ["/local.properties", ".env*", ".idea/runConfigurations/"], "providers": { "gradle": {} } }
```

Unknown fields of `.shogunai.json` are ignored, so later versions can add fields without breaking older ones.

What provisioning did to each file is a `ProvisionedFile` (`path` and `ProvisionMode`: `COPIED`, `LINKED`, `COPIED_INSTEAD_OF_LINKING`). `ProvisionDrift` (`path` and `ProvisionDriftState`: `MISSING`, `DIFFERS`, `NOT_LINKED`) is a provisioned file that is no longer as provisioning left it, as `CheckProvisionedFilesUseCase` reports it.

### Worktree environment

Variables each worktree gets, declared in `.shogunai.json` (`WorktreeEnvironment.kt`):

```json
{
  "env": { "PORT": "${port}", "API_URL": "http://localhost:${port:api}", "APP_SUFFIX": "-${worktree}" },
  "envFile": ".env.shogunai"
}
```

- `EnvironmentDeclaration` — the `variables` in file order and the optional `envFile`, a path relative to the worktree root that must stay inside it. `portKeys` lists the ports the declaration needs.
- `EnvironmentVariable` — a valid variable name (letters, digits and `_`, not starting with a digit, and not reserved) and its template, parsed when the file is read into `TemplatePart`s: `Literal`, `WorktreeName` (`${worktree}`, the worktree's folder name) and `Port(key)`. `${port}` is keyed by the variable's own name, so each variable gets its own port; `${port:<key>}` is keyed `port:<key>`, shared by every variable that uses it (`:` can't appear in a variable name, so the keys never collide). `$$` is a literal `$`; any other `${…}` throws `InvalidEnvironmentTemplate`, which reading turns into `InvalidRepositoryConfig`.
- `WorktreeEnvironment` — a resolved environment: the rendered `variables` and the `ports` reserved for the worktree.

Variable names that change how shells, runtimes or Git behave can't be declared: `PATH`, `HOME`, `SHELL`, `IFS`, `ENV`, `BASH_ENV`, `ZDOTDIR`, `PROMPT_COMMAND`, `NODE_OPTIONS`, `NODE_PATH`, `PYTHONPATH`, `PYTHONSTARTUP`, `PYTHONHOME`, `RUBYOPT`, `RUBYLIB`, `PERL5OPT`, `PERL5LIB`, `JAVA_TOOL_OPTIONS`, `_JAVA_OPTIONS`, `JDK_JAVA_OPTIONS`, `CLASSPATH` and every name starting with `LD_`, `DYLD_` or `GIT_`. Such a name makes `.shogunai.json` invalid (`RepositoryConfigIssue.ReservedVariableName` in the settings form). Variables that providers add, such as `GRADLE_OPTS`, aren't declared and aren't affected.

### Android emulator

The emulator provider (`Emulator.kt`) only acts for repositories that declare `"providers": { "emulator": {} }`; for any other repository nothing looks for the SDK, runs `adb` or adds variables.

- `EmulatorSettings` — the machine settings stored in `ProjectConfig.emulator`.
- `chooseAvd(requested, settings, preferred, available)` — the AVD to start: the one requested for this start, else the machine setting, else the repository's preferred AVD if this machine has it, else the first available one; `null` when there's none.
- Each worktree reserves the console port key `emulator` among the even ports 5554–5584 (the range `adb` scans), with the next port (the emulator's adb port) also free, and gets `ANDROID_SERIAL=emulator-<port>` (`emulatorSerial`). Android's Gradle plugin reads `ANDROID_SERIAL` in `installDebug`, `uninstall*` and `connectedAndroidTest` to use only that device.
- `SdkLocation` (SDK root, `emulator` and `adb` executables), `EmulatorState` (`STOPPED`, `BOOTING`, `RUNNING`) and `WorktreeEmulator` (serial and state) describe what the [Android SDK port](#androidsdk) reports.

### iOS simulator

The iOS simulator provider (`IosSimulator.kt`) only acts on macOS for repositories that declare `"providers": { "ios-simulator": {} }`.

- `SimulatorSettings` — the machine settings stored in `ProjectConfig.simulator`.
- `SimulatorRuntime` / `SimulatorDeviceType` — what Xcode has installed, as `simctl` reports it (identifier, display name, version, platform, and the device types a runtime supports).
- `chooseSimulator(requested, settings, preferred, runtimes)` — the runtime by display name (`iOS 18.5`, case-insensitive) from the first of this operation, the machine setting and the repository's preference that is installed, else the newest iOS runtime by version; then the device type by name among the runtime's iPhone and iPad types in the same order, else its newest iPhone: the highest model number in the name, and among those the shortest name (the base model, or the Pro when there's no base), whatever order Apple lists them in. `null` without an iOS runtime.
- Each worktree keeps the resource `ios-simulator`, the UDID of a simulator created for it and named `ShogunAi · <repository folder> · <worktree folder>` (`worktreeSimulatorName`), and gets `IOS_SIMULATOR_UDID=<UDID>` in its environment. The `ShogunAi · ` prefix is how orphaned simulators are told apart from the user's own.

### Herdr

`Herdr.kt` holds what the [Herdr port](#herdrclient) reports: `HerdrWorktreeOpening` (workspace id, its root pane id and whether Herdr already had the worktree open), `HerdrAgent` (an agent pane: the agent's `name`, its `AgentState` or `null` when Herdr reports `unknown`, the directory it works in and its pane id), plus `HerdrOpening` (`OPENED`, `FOCUSED`), the outcome of opening a worktree in Herdr.

### Gradle daemons

What the Gradle provider observes on the machine (`GradleDaemon.kt`):

- `DaemonProcess` — a running daemon: `pid`, `kind` (`DaemonKind.GRADLE` or `KOTLIN`), `gradleVersion` (Gradle daemons only), `residentMemoryBytes`, `lastBuildDirectory` (the directory of the last build a Gradle daemon received) and `state`.
- `DaemonState` — `Busy`, `Idle(since)`, `Unknown` (a Gradle daemon whose state can't be read) or `Untracked` (Kotlin daemons, which don't record one).
- `GradleDaemon` — a `DaemonProcess` with its `owner` in a repository: `DaemonOwner.Worktree(path)`, `RemovedWorktree` (its last build directory no longer exists) or `OtherProject`; `null` for Kotlin daemons, daemons without a known build directory, or outside a repository.
- `DaemonStopReport` — the outcome of a stop: `stopped`, `kept` (each with a `DaemonKeptReason`: `BUSY`, `STATE_UNKNOWN` or `BUILD_RUNNING`), `notDaemons` (requested pids that aren't daemons) and `failures`.

### Xcode DerivedData

What the Xcode provider finds in Xcode's DerivedData location (`DerivedData.kt`), only on macOS for repositories that declare `"providers": { "xcode": {} }`:

- `DerivedDataFolder` — one project's folder: `path`, `workspacePath` (the `.xcodeproj`, `.xcworkspace` or package recorded in its `info.plist`), `sizeBytes` (`null` until measured) and `lastAccessed`.
- `DerivedDataOwner` — `Worktree(path)` (the worktree of the repository containing the workspace, nested worktrees resolved), `OtherProject` (the workspace exists elsewhere), `Orphaned` (the workspace no longer exists) or `UnavailableVolume` (the workspace is on a volume that isn't mounted, so it can't be told apart from a live project).
- `DerivedDataListing` — every folder with its owner (`AttributedDerivedData`) and the size of the shared compilation cache when asked for; `orphans` and `ofWorktrees()` filter it.
- `DerivedDataDeletion` — the outcome of deleting orphans: `deleted`, `failures` (`DerivedDataDeletionFailure`: path and detail) and `bytesFreed`.

### `WorktreeOrigin` / `RunningAgent`

What the worktree overview shows about each worktree (`WorktreeOverview.kt`): `WorktreeOrigin` (`SHOGUNAI`, `CLAUDE_CODE`, `HERDR`, `OTHER`), the tool that created it as told by its location (`classifyOrigin`, see [Worktree overview](#worktree-overview)), and `RunningAgent`, an agent CLI running in it: its `name` and, when Herdr reports it, its `state` (`AgentState`: `WORKING`, `BLOCKED`, `DONE`, `IDLE`), the `stateSource` that reported it (`AgentStateSource`: `HERDR`, `CLAUDE_CODE`) and, when Herdr reports it, its `herdrPaneId`. `AgentSessionRecord` (`AgentSession.kt`) is what Claude Code's hooks leave per session: `sessionId`, `agent`, `state`, `updatedAt`, `transcriptPath` and `pid`. `waitsForUser` is true for a blocked or done agent.

### Tools

`Tools.kt` describes what the desktop app can launch a worktree in: `HostOs` (`MACOS`, `WINDOWS`, `LINUX`), `TerminalId` (`TERMINAL`, `ITERM2`, `WINDOWS_TERMINAL`, which uses PowerShell, `TMUX`, `ZELLIJ`), `EditorId` (`VS_CODE`, `INTELLIJ_IDEA`, `ANDROID_STUDIO`, `XCODE`, the last only on macOS), `DetectedTools` (the terminals and IDEs found on the machine) and `TerminalLaunch` (the worktree path, its variables, the agent command and the full launch line a terminal runs).

`ShellQuoting` quotes values for the shell they end up in: `posix` (single quotes, `'\''` for an embedded one), `posixCommand` (an argv list as a line, quoting only words that need it), `powershell` (single quotes, doubling every kind of single quote) and `windows` (double quotes).

### Updates

`Update.kt` models the update check: `AppVersion` (`X.Y.Z`, compared numerically; `parse` accepts an optional `v` and rejects anything else; `DEVELOPMENT` is `0.0.0`), `HostArchitecture` (`ARM64`, `X64`), `LatestRelease` (version, release page and `ReleaseAsset`s), `InstallChannel` (`HOMEBREW`, `DMG`, `SCOOP`, `WINGET`, `MSI`, `DEB`, `UNKNOWN`) and `UpdateAction`, what the user should do for a channel: run a command (`brew upgrade --cask shogunai`, `scoop update shogunai`, `winget upgrade LuxionServer.ShogunAi`), download an asset picked from the release by extension and architecture (with `sudo apt install ./<asset>` for a `.deb`), or open the release page. `UpdateCheck` is the outcome: `NotDue`, `UpToDate` or `Available` with the release, channel and action. `UpdatePreferences` holds `checkAutomatically` and `skippedVersion`.

`RecentWorktree` (`projectId`, `path`) is an entry of the recent worktrees the menu bar icon lists.

### `ThemeMode` / `AppLanguage`

User preferences: `LIGHT` / `DARK` / `SYSTEM` and `ENGLISH` / `SPANISH`. They're stored in `settings.json` (see [Infrastructure layer](infrastructure.md)).

### `WorktreeError`

`sealed class` of the flow's expected errors, so the UI decides what to show (and translates it) from structured data instead of parsing strings:

- `BaseRepositoryNotFound` — the configured base repo doesn't exist on disk.
- `WorktreeAlreadyExists` — there's already a directory at the destination path.
- `InvalidTaskId` — the task id isn't a valid Git ref segment once normalized.
- `SecretFileNotFound` — local files are missing in the base repo.
- `RepositoryNotFound` — the directory isn't inside a Git repository with a working tree (used by the CLI).
- `BareRepositoryNotSupported` — the directory is a bare repository, or inside one.
- `RepositoryHasNoCommits` — the repository has no commit to start a branch from.
- `LocalFileUnreadable` — `projects.json` or `settings.json` can't be parsed; keeps the path and the reason. The file is kept aside before it's written again (see [Infrastructure layer](infrastructure.md)).
- `UpdateCheckFailed` — the latest release couldn't be read from GitHub.
- `NotAWorktree` — the path given to provision is the main worktree or isn't one of the repository's worktrees.
- `InvalidRepositoryConfig` — `.shogunai.json` exists but isn't valid JSON, isn't an object, its `copy` or `link` isn't a list of strings, its `hooks.postCreate` isn't a command or a list of commands, its `providers` (or a known provider in it) isn't an object, a provider option has the wrong type, or its `env` or `envFile` is invalid (a reserved variable name included); keeps the file path and the detail.
- `RepositoryConfigChanged` — `.shogunai.json` changed on disk since the settings form read it, so it isn't overwritten.
- `NoFreePort` — every port from 20000 to 29999 is reserved or busy.
- `EnvironmentFileWriteFailed` — the declared `envFile` couldn't be written; keeps the path and the cause.
- `StartPointNotFound` — the start point given to `shogun new --from` doesn't name a commit.
- `ProcessListingFailed` — `lsof` or `ps` failed while detecting running agents (e.g. on Windows).
- `NoEmulatorPort` — every emulator console port from 5554 to 5584 is reserved or busy.
- `AndroidSdkNotFound` — the Android SDK wasn't found; keeps the places checked.
- `NoAvdAvailable` — the machine has no Android Virtual Device.
- `EmulatorBootTimeout` — the emulator didn't finish booting in time; it's left booting.
- `EmulatorExitedEarly` — the emulator process exited while booting; keeps the last lines of its log.
- `AndroidToolFailed` — `emulator` or `adb` failed; keeps the command, exit code and output.
- `EmulatorProviderNotDeclared` — an emulator command ran in a repository that doesn't declare the emulator provider.
- `NoIosRuntime` — Xcode has no iOS simulator runtime installed.
- `SimulatorBootTimeout` — the simulator didn't finish booting in time; it's left booting.
- `IosSimulatorProviderNotDeclared` — a simulator command ran in a repository that doesn't declare the iOS simulator provider.
- `IosSimulatorsNotSupported` — the host isn't macOS with Xcode.
- `AppleToolFailed` — `xcrun simctl` or another Xcode tool failed; keeps the command, exit code and output.
- `EnvironmentFileTracked` — the declared `envFile` is a file Git tracks in the worktree, so it isn't overwritten.
- `EnvironmentFileOutsideWorktree` — the declared `envFile` resolves, after following symlinks, outside the worktree.
- `InvalidRef` — a branch name or start point starts with `-`, so Git could read it as an option.
- `GitCommandFailed` — a Git command returned a non-zero exit code; keeps the command, exit code and Git's error output.
- `SecretCopyFailed` — copying or linking local or repository files failed after the worktree had already been created, including a file that would land outside the worktree.
- `PostCreateCommandFailed` — a post-creation command couldn't start or exited with an error; keeps the worktree, the command, its exit code and its error output. The worktree is kept.
- `ClipboardWriteFailed` — the launch command couldn't be copied to the clipboard.
- `GitLfsNotFound` — the repository uses Git LFS but `git-lfs` isn't installed.
- `BranchNotFound` — the requested existing branch doesn't exist locally.
- `BranchAlreadyCheckedOut` — the branch is already checked out in another worktree or in the base repository.
- `HerdrNotInstalled` — the `herdr` command wasn't found.
- `HerdrCommandFailed` — a `herdr` command failed; keeps the command and Herdr's message (e.g. no Herdr server running).
- `TerminalLaunchFailed`, `TerminalAutomationRefused` (macOS refused to let ShogunAi control Terminal or iTerm2) and `TerminalUnsupported` (not available on this system, or tmux older than 3.0) — opening a worktree in a terminal failed.
- `EditorLaunchFailed` and `FolderOpenFailed` — opening a worktree in an IDE or in the file manager failed; keep the tool's message.
- `WorktreeTargetNotFound`, `AmbiguousWorktreeTarget` and `CannotRemoveMainWorktree` — `shogun remove` was given a task id, path or branch that matches no worktree, several worktrees, or the main one.
- `AgentRunningInWorktree` — an agent runs in the worktree to remove; keeps the agents' names.

## Ports

Use cases depend on interfaces, not implementations. This is what allows testing them with doubles.

### `ShellCommandExecutor`

```kotlin
interface ShellCommandExecutor {
    suspend fun execute(command: List<String>, workingDirectory: String? = null): CommandResult
    suspend fun executeWithin(command: List<String>, timeout: Duration, workingDirectory: String? = null): CommandResult
}
```

The command is passed as **argv** (a list of strings), never as a string for a shell to interpret. Reason: to avoid differences between macOS's default shell (zsh) and Linux's (bash), and to avoid command escaping/injection issues.

`CommandResult` holds `command`, `exitCode`, `stdout`, `stderr` without interpreting their meaning — each use case decides what counts as success via `isSuccess` (`exitCode == 0`). A process that can't even start (e.g. the executable isn't in `PATH`) throws `CommandExecutionException` instead.

`executeWithin` bounds a command's run: one that doesn't finish in time is stopped and throws `CommandExecutionException.TimedOut`. Read-only commands pass a limit from `CommandTimeouts` (`GIT_READ`, 30 s, for Git reads; `LISTING`, 10 s, for device, process and tool listings; `NETWORK`, 60 s, for `git fetch`); commands that may legitimately run long (creating a worktree, post-creation commands, builds) use `execute`, with no limit. Its default implementation ignores the limit, so test doubles don't have to implement it. A cancelled coroutine always stops the command, and no command ever waits for input.

### `FileManager`

```kotlin
interface FileManager {
    fun exists(path: String): Boolean
    fun isInside(root: String, path: String): Boolean
    fun copy(source: String, destination: String)
    fun createSymbolicLink(target: String, link: String)
    fun isSymbolicLinkTo(link: String, target: String): Boolean
    fun hasSameContent(first: String, second: String): Boolean
    fun directorySize(path: String, excluding: Set<String> = emptySet()): Long
    fun isDirectory(path: String): Boolean
    fun modificationStamps(path: String): List<Pair<String, Long>>
    fun readProperties(path: String): Map<String, String>
    fun writeText(path: String, text: String)
    fun readText(path: String): String?
}
```

Abstracts the filesystem so that copying secrets and measuring worktrees is unit-testable without touching disk. `isInside` tells whether `path`, once its nearest existing ancestor is resolved through symlinks, is inside `root`; provisioning checks it before every copy, link and environment file write, so a hostile symlink in a branch can't make ShogunAi write outside the worktree. `copy` creates the destination's missing parent folders. `createSymbolicLink` replaces whatever is at `link` with a symlink, or throws `SymbolicLinksNotSupported` where links can't be created (e.g. Windows without the privilege); `isSymbolicLinkTo` and `hasSameContent` are what the provisioning check compares. `directorySize` adds up the regular files under `path`, skipping the folders in `excluding`. `modificationStamps` returns every file and folder under `path` (or `path` itself if it's a file) with its last-modified time, and nothing if it doesn't exist. `readProperties` reads a Java properties file (such as `gradle.properties`), empty if it's missing or unreadable. `writeText` replaces a file atomically, creating its folders; `readText` returns its content, or `null` if it's missing or unreadable.

### `ClipboardWriter`

```kotlin
interface ClipboardWriter {
    fun write(text: String): Result<Unit>
}
```

Writes the launch command of a worktree to the system clipboard.

### `RepositoryProvisioningSource`

```kotlin
interface RepositoryProvisioningSource {
    fun read(repositoryPath: String): RepositoryProvisioning
    fun fingerprint(repositoryPath: String): String
    fun addCopyPatterns(repositoryPath: String, patterns: List<String>, expectedFingerprint: String? = null)
    fun preview(repositoryPath: String, edit: RepositoryConfigEdit): String
    fun write(repositoryPath: String, edit: RepositoryConfigEdit, expectedFingerprint: String)
}
```

Reads the repository's provisioning (throwing `InvalidRepositoryConfig` for an unreadable `.shogunai.json`), appends patterns to `copy` (how the configuration screen moves local files into the repository), and previews and writes the providers and environment edited in the settings form (`RepositoryConfigEdit`). `fingerprint` is a hash of the file's content; a write given the fingerprint read earlier fails with `RepositoryConfigChanged` when the file changed meanwhile.

### `EnvironmentStateStore` / `PortProbe`

```kotlin
interface EnvironmentStateStore {
    fun readPorts(adminDirectory: String): Map<String, Int>
    fun writePorts(adminDirectory: String, ports: Map<String, Int>)
    fun readResources(adminDirectory: String): Map<String, String>
    fun writeResources(adminDirectory: String, resources: Map<String, String>)
    fun reservedPortsOnMachine(commonDirectory: String): Set<Int>
    fun recordedResourcesOnMachine(commonDirectory: String, key: String): Set<String>
    fun <T> withLock(block: () -> T): T
}

interface PortProbe {
    fun isFree(port: Int): Boolean
}
```

`EnvironmentStateStore` keeps each worktree's reserved ports and provider resources (such as a simulator's UDID) in Git's administrative directory for that worktree (`.git/worktrees/<id>/` for a linked one, `.git/` for the main one), lists every reservation on the machine (this repository's worktrees plus every worktree of any repository ShogunAi has stored reservations for), and runs a block while holding the machine's reservation lock. `PortProbe` tells whether a port is free on the machine.

### `AndroidSdk`

```kotlin
interface AndroidSdk {
    fun locate(repositoryPath: String): SdkLocation
    suspend fun listAvds(sdk: SdkLocation): List<String>
    suspend fun deviceStates(sdk: SdkLocation): Map<String, EmulatorState>
    fun startEmulator(sdk: SdkLocation, avd: String, consolePort: Int, noWindow: Boolean): EmulatorProcess
    suspend fun stopEmulator(sdk: SdkLocation, serial: String)
}
```

Everything the emulator provider needs from the Android SDK, used only for repositories that declare the provider: finding the SDK (`AndroidSdkNotFound` with the places checked), listing AVDs, the state of every running emulator in one call, starting an emulator detached on a console port, and stopping one. The started `EmulatorProcess` tells whether the process is still alive and gives the last lines of its log, so a boot wait can fail as soon as the emulator exits. Tool errors are `AndroidToolFailed`.

### `AppleSimulators` / `KmpTestWiring`

```kotlin
interface AppleSimulators {
    fun isSupported(): Boolean
    suspend fun runtimes(): List<SimulatorRuntime>
    suspend fun devices(): List<SimulatorDevice>
    suspend fun create(name: String, deviceTypeIdentifier: String, runtimeIdentifier: String): String
    suspend fun boot(udid: String)
    suspend fun show(udid: String)
    suspend fun shutdown(udid: String)
    suspend fun delete(udid: String)
}

interface KmpTestWiring {
    fun isWiredInRepository(repositoryPath: String): Boolean
    fun usesKotlinMultiplatform(repositoryPath: String): Boolean
    fun isInitScriptInstalled(): Boolean
    fun initScriptPath(): String
    fun installInitScript()
    fun uninstallInitScript()
}
```

`AppleSimulators` is everything the iOS simulator provider needs from Xcode's simulators, used only on macOS for repositories that declare the provider: installed runtimes (with the device types each supports), existing devices with their state, and creating, booting, showing in Xcode's simulator viewer (Simulator or Device Hub), shutting down and deleting one. Tool errors are `AppleToolFailed`. `KmpTestWiring` tells whether the repository's Gradle build scripts apply the Kotlin Multiplatform plugin, whether they pass `IOS_SIMULATOR_UDID` to KMP's simulator tests, and installs or removes the machine-wide Gradle init script that does it for every project.

### `HerdrConfigSource`

```kotlin
interface HerdrConfigSource {
    fun worktreesRoot(): String
}
```

Where [Herdr](https://herdr.dev) creates its worktrees, so they can be told apart from other locations. It never fails: without a readable configuration it returns Herdr's default folder.

### `HerdrClient`

```kotlin
interface HerdrClient {
    suspend fun openWorktree(repository: String, path: String, label: String): HerdrWorktreeOpening
    suspend fun splitPane(paneId: String, cwd: String, environment: List<Pair<String, String>>): String
    suspend fun closePane(paneId: String)
    suspend fun runInPane(paneId: String, command: String)
    suspend fun openWorktreeWorkspaces(repository: String): Map<String, String>
    suspend fun closeWorkspace(workspaceId: String)
    suspend fun listAgents(): List<HerdrAgent>
    suspend fun focusAgent(paneId: String)
}
```

The few [Herdr](https://herdr.dev) operations ShogunAi uses to open a worktree there and to close it again: `openWorktreeWorkspaces` returns, for the worktrees of a repository that Herdr has open, their path and workspace id; `closeWorkspace` closes a workspace and the processes in its panes. The rest: open an existing worktree as a focused workspace grouped under its repository (or focus it if it's already open), split a pane into a focused one whose shell starts in a directory with extra variables, close a pane, and submit a command in a pane. `listAgents` returns every agent pane Herdr tracks with its state, and `focusAgent` brings one agent's pane to the front. Pane ids aren't stored, since Herdr renumbers them when panes move; the ones from `listAgents` are only used right away. Errors are `HerdrNotInstalled` and `HerdrCommandFailed`.

### `GradleDaemonSource`

```kotlin
interface GradleDaemonSource {
    suspend fun list(): List<DaemonProcess>
}
```

Lists the Gradle and Kotlin daemons running on the machine with their state, or fails with `ProcessListingFailed` when processes can't be listed.

### `DerivedDataSource`

```kotlin
interface DerivedDataSource {
    fun isSupported(): Boolean
    suspend fun folders(): List<DerivedDataFolder>
    suspend fun size(folder: DerivedDataFolder): Long
    suspend fun compilationCacheBytes(): Long?
    fun projectExists(workspacePath: String): Boolean
    fun isOnUnavailableVolume(workspacePath: String): Boolean
    suspend fun delete(folder: DerivedDataFolder)
}
```

Everything the Xcode provider needs from Xcode's DerivedData: whether the host is macOS, the project folders in the current location without their sizes (never Xcode's shared caches, which have no `info.plist`), the size of one folder measured on demand and cancellable, the size of the shared compilation cache (`null` when it doesn't exist), whether a workspace still exists or sits on an unmounted volume, and deleting one folder.

### `TerminalLauncher` / `EditorLauncher` / `FolderLauncher` / `ToolDetector`

```kotlin
interface TerminalLauncher {
    val terminal: TerminalId
    suspend fun open(launch: TerminalLaunch): Result<Unit>
}

interface EditorLauncher {
    val editor: EditorId
    suspend fun open(path: String): Result<Unit>
}

interface FolderLauncher {
    suspend fun open(path: String): Result<Unit>
}

interface ToolDetector {
    suspend fun detect(): DetectedTools
}
```

One `TerminalLauncher` per supported terminal opens a new window or tab in the worktree and runs the launch line there; one `EditorLauncher` per IDE opens the worktree's folder; `FolderLauncher` shows it in the system's file manager; `ToolDetector` finds which terminals and IDEs the machine has. Failures are the typed `TerminalLaunchFailed`, `TerminalAutomationRefused`, `TerminalUnsupported`, `EditorLaunchFailed` and `FolderOpenFailed`.

### `ReleaseSource`

```kotlin
interface ReleaseSource {
    suspend fun latest(): Result<LatestRelease>
}
```

The latest published release of ShogunAi, or `UpdateCheckFailed`.

### `RepositoryTrust` / `PostCreateApproval`

```kotlin
interface RepositoryTrust {
    fun isApproved(repository: String, commands: List<List<String>>): Boolean
    fun approve(repository: String, commands: List<List<String>>)
    fun revoke(repository: String)
}

fun interface PostCreateApproval {
    suspend fun decide(repository: String, commands: List<List<String>>): PostCreateDecision
}
```

`RepositoryTrust` remembers, per repository and on this machine, the exact post-creation commands the user approved; a different list isn't approved. `PostCreateApproval` asks the user when they aren't, and answers `RUN` or `SKIP`: the desktop app shows the commands in the dialog that started the creation, the CLI asks on a terminal, and hooks and `--json` use `PostCreateApproval.SKIP`. `RepositoryTrust.NONE` approves nothing, which is the default for use cases built without a trust store.

### `AgentSessionStore`

Keeps the `AgentSessionRecord`s of the sessions of a worktree: `write`, `remove` and `list`, by worktree path. `FileAgentSessionStore` is the implementation (see [infrastructure](infrastructure.md#fileagentsessionstore)); the CLI's `hook agent-state` writes and `DetectRunningAgentsUseCase` reads and deletes dead records.

### Repositories

- `ProjectRepository` — `list()`, `save(project)`, `delete(id)` over the project catalog.
- `ThemePreferenceRepository` / `LanguagePreferenceRepository` — `load()` / `save(...)` of each user preference.
- `ShellPreferenceRepository` — the desktop shell's state: last destination, window bounds, sidebar and collapsed groups, the last project used to create, the palette's recent items, the grouping of each view, the recent projects and worktrees, and the tray options.
- `ToolPreferenceRepository` — the chosen terminal and default IDE.
- `UpdatePreferenceRepository` — `load()`, `setCheckAutomatically(enabled)` and `skip(version)`.

## Use cases

The worktree use cases are built per project (they receive its `ProjectConfig`) and return `Result<T>` (using `runCatching`), with typed errors as `WorktreeError`. Every Git command runs from the base repository.

### `CreateWorktreeUseCase`

Creates a worktree on a **new** branch `<type>/<taskId>`. The task id is normalized first (`normalizeTaskId`: trimmed, whitespace runs replaced with `-`) and validated as a Git ref segment; a start point that starts with `-` is `InvalidRef`. Then, in this order:

1. **Upfront validations** (base repo exists, destination free, local files present) and **resolving the repository's files** — done *before* touching Git so as not to leave a half-done worktree.
2. `git worktree add <path> -b <branch> [<startPoint>]`. The caller resolves the optional start point to a commit id with `ResolveStartPointUseCase` (`git rev-parse --verify --quiet <ref>^{commit}` in its own directory, failing with `StartPointNotFound`), so `HEAD` means the worktree the command runs in even though Git runs in the base repository. The desktop app passes the ref chosen in its "Start from" selector, resolved the same way; without one, the branch starts at the base repository's HEAD.
3. Copying the local files and the repository's `copy` files into the new directory, at the same relative paths, and symlinking the `link` files to the base repository's copies (copying them where links aren't supported). Every destination must stay inside the worktree once symlinks are resolved (`FileManager.isInside`).
4. Preparing its environment through the `WorktreeEnvironmentPreparer` it receives: `PrepareWorktreeEnvironmentUseCase` resolves it with reservation (`ResolveWorktreeEnvironmentUseCase`) and, when `envFile` is declared, `WriteEnvironmentFileUseCase` writes it in dotenv format, refusing a file Git tracks (`git ls-files --error-unmatch`, `EnvironmentFileTracked`) and reporting write errors as `EnvironmentFileWriteFailed`. The preparer defaults to `WorktreeEnvironmentPreparer.NONE`, which tests that don't exercise the environment rely on; the app and the CLI always pass the real one.
5. Running the repository's post-creation commands in the worktree, only when approved (see [Repository trust](#repository-trust)). Commands that aren't approved and that the user doesn't approve now are returned in the worktree's `skippedPostCreateCommands`. A failing command is `PostCreateCommandFailed`, and the worktree is kept, since everything before it succeeded.

Resolving the repository's files reads `RepositoryProvisioning` and, if it declares any pattern, runs `git ls-files --others --ignored -z -x <pattern>…`: every file not tracked by Git that matches a pattern (tracked files already come with the checkout). Without patterns no Git command runs. An unreadable `.shogunai.json` (`InvalidRepositoryConfig`) or a failing `git ls-files` (`GitCommandFailed`) stops creation before anything is created. Local files are checked strictly (`SecretFileNotFound`), patterns leniently: a pattern with no match copies nothing.

If copying or preparing the environment fails **after** the worktree was created, it's rolled back with `git worktree remove --force` and the branch the creation made is deleted with `git branch -D -- <branch>` (both best-effort) before propagating the error, so a retry finds nothing in its way. If `git worktree add` fails because `git-lfs` is missing, the half-created worktree and its new branch are removed the same way and `GitLfsNotFound` is returned.

### `CreateWorktreeFromBranchUseCase`

Same flow for a branch that **already exists**, locally or only on `origin`. The name is looked up exactly with `git show-ref --verify --quiet`: a local branch (`refs/heads/<name>`) runs `git worktree add <path> <branch>` (without `-b`); otherwise `origin/<name>`, or a name given with the `origin/` prefix, creates a local branch that tracks it with `git worktree add --track -b <name> <path> origin/<name>`, and a rollback deletes that new branch. Either way the folder is the one `worktreePathForBranch` gives the local name, and files, environment and post-creation commands follow as in `CreateWorktreeUseCase`. A branch already checked out elsewhere fails with `BranchAlreadyCheckedOut`, an unknown one with `BranchNotFound`, and a name starting with `-` with `InvalidRef`.

### `ListBranchesUseCase`

Lists local branches and `origin/` branches (`git for-each-ref --format=%(refname) refs/heads refs/remotes/origin/`, skipping `origin/HEAD`) and flags the local ones already checked out, using `git worktree list --porcelain`. The UI shows the flagged ones disabled, and in "Existing branch" hides remote branches that have a local twin (`withoutLocalTwins`).

### `FetchRemoteUseCase`

Runs `git fetch --prune origin` in the base repository with the network timeout, Git's credential prompts off (`GIT_TERMINAL_PROMPT=0`, so missing credentials fail instead of waiting), and reports a failure as `GitCommandFailed`. `hasOrigin()` tells whether the repository has an `origin` remote, which is when the desktop app offers the fetch. `shogun new --fetch` and the "Fetch from origin first" option use it before resolving the start point or the branch.

### `ListWorktreesUseCase`

Runs `git worktree list --porcelain` (a stable format meant to be parsed, unlike the default human-readable output) and parses the result with `parseWorktreeList`. Marks `isMain = true` on the worktree whose path is the same directory as `config.baseRepositoryPath` (`sameDirectory`: symlinks resolved, trailing slashes and separators ignored, case-insensitive on macOS and Windows). When it is given a `FileManager` (the desktop app does), a base repository that no longer exists fails with `BaseRepositoryNotFound` without running Git.

### `ResolveMainBranchUseCase`

Resolves the `MainBranch` once per list load, from the base repository and without network access: `config.mainBranch` if set, otherwise the `origin` remote's default branch (`git symbolic-ref refs/remotes/origin/HEAD`), falling back to a local `main` and then `master`. A named branch is compared through `origin/<name>` when that ref exists (`git rev-parse --verify`), because merges usually reach the remote first, and through the local branch otherwise. Returns `null` when nothing can be resolved.

### `ReadWorktreeStatusUseCase`

Reads a `WorktreeStatus`, running each command in the worktree's own folder:

- `git --no-optional-locks status --porcelain` — uncommitted changes. Optional locks are disabled so `git status` never rewrites the index; otherwise reading statuses would change the metadata that auto refresh watches. It's the only required command: if it fails (e.g. the folder is gone), the whole status fails.
- `git rev-list --left-right --count @{upstream}...HEAD` — upstream divergence, skipped on a detached HEAD and `null` if there's no upstream.
- `git rev-list --left-right --count <main ref>...HEAD` — main branch divergence, skipped when there's no main branch or the worktree is on it.
- `git log -1 --format=%ct` — last commit date, `null` if there are no commits.
- `git reflog show --format=%H refs/heads/<branch>` — only when the branch would otherwise read as merged and has no upstream: a reflog with a single entry (its creation) that is still the branch's tip means the branch never had commits of its own, so it reads as "not started" (`neverCommitted`) instead of merged.

Every command runs with the Git read timeout.

Squash and rebase merges aren't detected as merged, and remote-tracking branches are only as fresh as the user's last fetch.

### `CleanUpWorktreesUseCase`

`selectCleanup(worktrees, statuses, runningAgents)` picks the worktrees to clean up: not main, not locked, and either prunable or merged into the main branch with no uncommitted changes. Worktrees without a loaded status are never picked, and a qualifying worktree where an agent is running is moved to `keptForRunningAgent` with its agents, so neither the desktop app nor `shogun clean` removes it. `selectCleanupCandidates` returns only the candidates.

`CleanUpWorktreesUseCase` takes those candidates, the `MainBranch` and whether to delete branches, and returns a `CleanupReport` (`removed`, `keptBranches`, `failures`, `herdrFailures`). Each existing worktree is removed with `git worktree remove` (never `--force`), and missing ones with a single `git worktree prune`. A failing step is recorded and the rest continue. Then the branches of every removed worktree, pruned ones included, are deleted only if `git merge-base --is-ancestor <branch> <main ref>` still succeeds, and then with `git branch -D`: `-d` checks against the base repository's `HEAD`, which in a git-flow repository may not contain a branch merged into `develop`.

### Worktree overview

- `classifyOrigin(worktree, config, herdrWorktreesRoot)` returns the `WorktreeOrigin` of a non-main worktree: `CLAUDE_CODE` if its path is inside a `.claude/worktrees/` folder (checked first, since that folder can be inside the worktrees root), `HERDR` if it's inside Herdr's worktrees folder (when one is given, read through [`HerdrConfigSource`](#herdrconfigsource)), `SHOGUNAI` if it's inside `config.worktreesRoot`, `OTHER` otherwise. Paths are compared with `/` separators and without trailing slashes, so Windows paths work too.
- `isInactive(worktree, status, agents, now)` is true for a non-main worktree with no uncommitted changes, no running agents and a last commit older than `INACTIVITY_THRESHOLD` (14 days).
- `DetectRunningAgentsUseCase` returns the `RunningAgent`s per worktree path, from two sources read concurrently. The process scan runs `lsof -d cwd -F pcn` once to get every process's working directory, assigns each process to the worktree with the longest path containing it (`owningWorktree`, so a process in `.claude/worktrees/<name>` isn't attributed to the main worktree), and runs `ps -o pid=,comm=` for those processes. Their executable's file name is matched against the known agent CLIs (`claude`, `codex`, `gemini`, `cursor-agent`, `opencode`, `aider`, `copilot`) and the executables of every agent of the project. It can search several projects at once (`AgentSearch`, a project's agent commands and worktrees): `lsof` and `ps` still run once, and a project's own agents are only recognized in that project's worktrees. The `lsof` command name isn't used because Claude Code renames its process to its version. When it's given a `HerdrClient`, it also reads `listAgents()` (5-second limit) and attributes each Herdr agent to a worktree by its directory: it enriches the process-detected agent of the same name with its Herdr state and pane, or is added when the process scan can't see it, and several panes of one agent in a worktree keep the most urgent state (blocked, done, working, idle). A Herdr failure (not installed, no server, timeout) gives no Herdr data, never fails detection, and makes the next 30 seconds skip Herdr. If `lsof` or `ps` fail (e.g. on Windows) and Herdr reports nothing, it fails with `WorktreeError.ProcessListingFailed`.
- Given an `AgentSessionStore`, it also reads the records of every worktree and gives the `claude` agent the state of its most urgent live session (blocked, done, working) with source `CLAUDE_CODE`. A record is live while its `pid` is a `claude` process found by the same `ps` call (so a reused pid doesn't revive it); a record without a pid is live while a `claude` process runs in that worktree and it is under 12 hours old. Dead records are deleted. A blocked or done record whose transcript was modified more than one second after the record reads as working. Herdr's state, applied after, wins; a Herdr agent without a state keeps the hook's.
- `worktreeActivity(worktree, status, agents, now)` classifies a worktree for the desktop app's views and the menu bar: `NeedsYou` when one of its agents is blocked or done (`waitsForUser`), `AgentWorking` when any other agent runs in it, then `Missing`, `Unknown` (status not read), `NeedsYou` for uncommitted changes, `Idle` (main worktree), `InProgress` for a "not started" branch, `Merged`, `Inactive`, `NeedsYou` for unpushed commits, and `InProgress` otherwise. The first three are the *active* ones (`isActive`).
- `MeasureWorktreeSizeUseCase` returns a worktree's size with `FileManager.directorySize`, on the IO dispatcher, excluding every other listed worktree nested inside it. It returns `null` for a missing folder.
- `ReadGitMetadataFingerprintUseCase` returns a fingerprint of the main repository's Git metadata: the sorted (path relative to `.git`, last-modified time) pairs of `HEAD`, `index`, `packed-refs`, `FETCH_HEAD` and everything under `refs` and `worktrees`, read on the IO dispatcher. Commits, checkouts, locks, fetches and worktrees added or removed by any tool change it; `objects` isn't included. It's empty when `<baseRepositoryPath>/.git` isn't a directory.

### `ClaudeCodeHooks`

Installs, updates, checks and removes ShogunAi's hooks in a Claude Code `settings.json`, working on the parsed JSON tree (never on text) through `FileManager`. The desired hooks are one table (`DESIRED`: event → command); a hook is ShogunAi's when it is a `command` hook whose command starts with `shogun hook `. `previewInstall` / `previewUninstall` compute a `HooksPreview` (added, replaced and removed hooks); `install` removes ShogunAi hooks that differ and appends the missing ones as their own matcher group, `uninstall` removes them and the groups and events left empty, and neither writes when the preview is empty. `status` gives `HooksInstallState.INSTALLED`, `OUTDATED` or `MISSING` with the events that differ. A file that isn't a JSON object, or whose `hooks` isn't one, is `WorktreeError.InvalidClaudeSettings` and is never written. `userSettingsPath` honors `CLAUDE_CONFIG_DIR`; `projectSettingsPath` is `<repository>/.claude/settings.json`.

### `RemoveWorktreeUseCase`

Runs `git worktree remove [--force] <path>` and, optionally, `git branch -d -- <branch>` to delete the local branch (safe delete, not `-D`). It returns a `WorktreeRemoval` whose `herdrFailure` is the error from closing the worktree's Herdr workspace, when that failed (see [`CloseHerdrWorkspacesUseCase`](#closeherdrworkspacesusecase)).

!!! note
    Git doesn't allow removing a worktree from inside itself, which is another reason every command runs from the base repository.

### `ReleaseWorktreeResourcesUseCase` / `resolveWorktreeTarget`

Removing one worktree also releases what its providers hold, in two steps around the removal. `plan(config, worktree, allWorktrees)` reads, while the worktree still exists, its running emulator, its booted simulator and the idle Gradle daemons whose last build was in it, each only when the repository declares that provider; `release(config, plan)` stops them after the removal succeeded. Failures are collected as messages in `ReleasedResources.failures` instead of failing: `shogun remove` reports them and the desktop app ignores them. Cleanup keeps its own batched, opt-in stops.

`resolveWorktreeTarget(target, worktrees, config, canonicalPath)` is how `shogun remove` finds its worktree: an existing path of a listed worktree, else a branch matching exactly one worktree's branch, else a task id mapped with `worktreePathFor`. No match is `WorktreeTargetNotFound`, several are `AmbiguousWorktreeTarget` with their paths, and the main worktree is `CannotRemoveMainWorktree`. `findWorktreeTarget` does the same search without refusing the main worktree, for `shogun open`.

### `CloseHerdrWorkspacesUseCase`

Closes the Herdr workspace of removed worktrees, in two steps around the removal. `openWorkspaces(paths)` makes one `worktree list` call for the base repository, before anything is removed (afterwards Git no longer lists the worktree), and returns the workspace id of each given path that Herdr has open, matching paths with `normalizePath`. Any failure (Herdr not installed or not running, an unexpected response) gives an empty map: if ShogunAi can't see Herdr there's nothing to close and nothing to report. `close(workspaces, removedPaths)` closes only the workspaces of the paths that were actually removed (pruned missing worktrees included) and returns a `CleanupFailure` for each close that failed instead of throwing. `RemoveWorktreeUseCase` and `CleanUpWorktreesUseCase` take it as an optional parameter and `null` leaves their behaviour unchanged; a Git failure closes nothing.

### `CopyWorktreeLaunchCommandUseCase`

Copies `cd <worktree path> && <agent command>` to the clipboard, so the user pastes it in the terminal of their choice. The path is quoted with `ShellQuoting.posix` (`ShellQuoting.windows` on Windows), so no character in it is interpreted by the shell it's pasted into, and the command is the given agent's `resolvedCommand()`, the project's default when none is given.

### `OpenWorktreeInTerminalUseCase`

Opens a worktree in the chosen terminal with its environment and an agent (the default when none is given). It picks the `TerminalLauncher` for that `TerminalId` (`TerminalUnsupported` when there's none), resolves the worktree's environment as `shogun env` does, and builds one launch line: for POSIX terminals `cd <path> && export NAME=<value> … && <agent command>`, every value quoted with `ShellQuoting.posix`; for Windows Terminal the PowerShell equivalent, `Set-Location -LiteralPath <path>; $env:NAME = <value>; …; <agent command>`. The launcher receives the line and the parts it's built from, since tmux sets the variables and folder itself and only types the agent command.

### `OpenWorktreeInHerdrUseCase`

Opens a worktree in Herdr with its environment and an agent (the project's default unless another one is given). It asks Herdr to open the worktree for the project's base repository, labelled with its folder name; if Herdr already had it open, that workspace is now focused and it returns `FOCUSED` without touching its panes. Otherwise it resolves the worktree's environment (the same as `shogun env`, provider variables such as `ANDROID_SERIAL` included, reserving ports if needed); when it isn't empty, it splits the root pane into one whose shell starts in the worktree with those variables and closes the root pane, so the workspace keeps a single pane. Then it runs the agent's `resolvedCommand()` in that pane and returns `OPENED`. When Herdr already reports an agent in the worktree, the desktop app focuses that agent's pane with `HerdrClient.focusAgent` instead of calling this use case. `shogun open <target> --herdr` calls it too, without that agent check.

### `ResolveStartPointUseCase`

Resolves the ref a new branch starts from to a commit id with `git rev-parse --verify --quiet <ref>^{commit}`, run in the caller's directory, so `HEAD` means the worktree the command runs in and not the base repository. An unknown ref fails with `StartPointNotFound` before anything is created. Used by `shogun new`, the `WorktreeCreate` hook and the desktop app's "Start from" selector, which resolves its ref in the base repository after the optional fetch.

### `ResolveRepositoryUseCase`

Finds the base repository (main worktree) that contains a directory, for front ends that start from a directory instead of a project, such as the CLI. It runs `git rev-parse --path-format=absolute --git-common-dir --is-bare-repository` there: the common Git folder is shared by every worktree, so when it's a `.git` folder its parent is the main worktree whether the directory is the main worktree, a subfolder or a linked worktree. Otherwise the main worktree is the common folder's `core.worktree` when set (a submodule), or the first entry of `git worktree list --porcelain` (a repository with a separate Git folder). A bare repository is `BareRepositoryNotSupported`; anything that isn't a repository is `RepositoryNotFound`.

### `DeleteMergedBranchUseCase`

Deletes a local branch only when it is contained in the project's main branch, resolved with `ResolveMainBranchUseCase` (`git merge-base --is-ancestor <branch> <main ref>`, then `git branch -D -- <branch>`, the same rule cleanup uses), and returns whether it was deleted instead of failing. A branch merged only into its own upstream is kept, and a name starting with `-` is `InvalidRef`. Used by the CLI's Claude Code `WorktreeRemove` and Herdr hooks, where a kept branch is expected rather than an error.

### `ProvisionWorktreeUseCase`

Applies the same files as creation (local files and the repository's provisioning, see `CreateWorktreeUseCase`) to an **existing** linked worktree, e.g. one created by another tool. The path must be one of the repository's worktrees (from `ListWorktreesUseCase`) and not the main one (`NotAWorktree`). Files are copied (or linked) over existing ones, the environment is prepared the same way (reserving ports for a worktree another tool created), the approved post-creation commands run, and it returns a `ProvisionResult`: each `ProvisionedFile` and the skipped commands. Unlike creation, a failed copy or environment doesn't remove anything: the worktree isn't ShogunAi's to delete. Creation and provisioning share the same copy and command functions.

### `CheckProvisionedFilesUseCase`

Compares a linked worktree with what provisioning would give it now, without changing anything: each file that should be copied and is missing or differs from the base repository's (`hasSameContent`), and each file that should be linked and is missing or isn't a link to the base repository's copy, as a `ProvisionDrift`. `shogun provision --check` and the desktop app's worktree detail use it.

### Repository trust

`.shogunai.json` is committed, so whoever controls a branch controls what it declares. Its post-creation commands only run once the user approved that exact list on this machine (`RepositoryTrust`, keyed by the repository's real path). Creation and provisioning check the store first and ask `PostCreateApproval` only when the list isn't approved; `RUN` records the approval before running, `SKIP` returns the commands as skipped. A changed list (after a pull, say) asks again. Saving `hooks.postCreate` from the desktop app's settings approves the saved list, since the user wrote it, and the settings can approve or revoke it explicitly.

### `RepairWorktreesUseCase`

Re-links the base repository and its worktrees after they were moved on disk. Git stores absolute paths in both directions (`<worktree>/.git` points to the base repository and `.git/worktrees/<name>/gitdir` points back), so a move breaks them. It runs `git worktree repair` from the (new) base repository; worktrees that no longer exist at their old path but do exist at the same relative location under the new `worktreesRoot` are passed as arguments, so Git fixes both directions. The UI runs it after saving a project whose base repository path or worktrees root changed.

### `ResolveWorktreeEnvironmentUseCase`

Resolves a worktree's environment from the base repository's declaration and the providers it declares. Each needed port is a `PortRequest` (a key, candidate ports, ports that must also be free, and the error when none is left): declared `${port}` keys ask for 20000–29999 (`declaredPortRequests`); providers add theirs through `EnvironmentContribution`s, given by the injected `ProviderContributions` factory (`DEFAULT` knows the emulator; the app and the CLI pass one that also builds the iOS simulator's, which needs the `AppleSimulators` port and the project's machine settings), together with the variables they derive from their ports. With nothing declared and no contributing provider it returns an empty `WorktreeEnvironment` without running any command. Otherwise it finds the worktree's administrative and common Git directories (`git rev-parse --path-format=absolute --git-dir --git-common-dir` in the worktree) and, while holding the machine's lock:

1. keeps the worktree's stored ports whose keys are still requested, releasing the rest;
2. reserves each missing key at the first candidate that, together with its extra ports, no worktree on the machine has reserved and `PortProbe` reports free (otherwise the request's error, e.g. `NoFreePort`);
3. writes the reservations only if they changed.

Contributions can also ask for resources (`ResourceRequest`: a key, and how to check, create and discard one). With reservation, a stored resource that still `exists` is kept; otherwise one is created outside the lock (creating a simulator runs `xcrun` for about a second) and recorded under it, and if another process recorded one in the meantime, the new one is discarded and the recorded one used. Without reservation resources are only read. Provider variables come first and declared `env` variables override a provider variable with the same name. A reserved port is never checked again, because the worktree's own server is expected to listen on it. With `reserve = false` (used to show ports without side effects) it only reads the stored ports, without locking or writing, and returns no variables until every port is reserved. `formatEnvironment` renders the variables as `sh` (`export NAME='value'`, quoted with `ShellQuoting.posix`, safe for `eval`), `dotenv` (`NAME=value`, double-quoted unless the value is plain, with `\`, `"`, newlines, `$` and backticks escaped, so sourcing the file never runs a command) or `json`.

Finding a worktree's Git directories is the only part that runs a process; when it's given a `WorktreeEnvironmentCache` (the desktop app does), it remembers them per worktree path until the cache is invalidated, which the app does whenever a project reloads its worktree list. The CLI uses no cache.

### `PrepareWorktreeEnvironmentUseCase` / `WriteEnvironmentFileUseCase`

`PrepareWorktreeEnvironmentUseCase` is the `WorktreeEnvironmentPreparer` that creation and provisioning run once the files are copied: it resolves the worktree's environment with reservation and, when the declaration has an `envFile`, calls `WriteEnvironmentFileUseCase`. That one writes the variables in dotenv format at the declared path, refusing a path that resolves outside the worktree (`EnvironmentFileOutsideWorktree`) or a file Git tracks (`git ls-files --error-unmatch`, `EnvironmentFileTracked`), and reporting write errors as `EnvironmentFileWriteFailed`. `WorktreeEnvironmentPreparer.NONE` does nothing, for tests that don't exercise the environment.

### iOS simulator use cases

The provider's contribution comes from `MachineProviderContributions`, which the app and the CLI inject into `ResolveWorktreeEnvironmentUseCase`: the emulator's, plus, on macOS for repositories that declare `ios-simulator`, a resource request whose `create` picks the device type and runtime with `chooseSimulator` (this operation, the project's `SimulatorSettings`, the repository's preferences; `NoIosRuntime` if none) and runs `simctl create` with `worktreeSimulatorName`, whose `exists` checks the device list, and whose `discard` deletes it. `requesting(settings)` returns the same factory with this operation's device type and runtime.

- `StartWorktreeSimulatorUseCase(config, worktreePath, requested)` resolves the environment through a creating resolver (so the simulator exists), boots it unless it's already booted, and polls `devices()` every second until it's booted; after 120 s it fails with `SimulatorBootTimeout` and leaves it booting.
- `StopWorktreeSimulatorUseCase` reads the recorded UDID without creating and shuts the simulator down if it's running; with none recorded there's nothing to stop.
- `ReadSimulatorStatesUseCase` maps each worktree with a recorded, existing simulator to its UDID, name and state, from one `devices()` call.
- `DeleteSimulatorsUseCase` deletes the given simulators that still exist (shutting them down first), and `orphans(config)` lists the simulators named with the `ShogunAi · ` prefix whose UDID no worktree on the machine has recorded (`recordedOnMachine`). Simulators without the prefix are never touched.

All of them fail with `IosSimulatorProviderNotDeclared` or `IosSimulatorsNotSupported` (or return nothing, for the states and orphans) when the repository doesn't declare the provider or the host isn't macOS, before running `xcrun`.

### Emulator use cases

All three fail with `EmulatorProviderNotDeclared` (or return nothing, for the states) when the repository doesn't declare the emulator provider, before touching the SDK.

- `StartWorktreeEmulatorUseCase(config, worktreePath, avd?, noWindow?)` resolves the worktree's environment (reserving its emulator port if needed), returns at once if its serial is already running, otherwise starts the AVD from `chooseAvd` (`NoAvdAvailable` if none) with the window setting from the call or the project, unless it's already booting, and polls `deviceStates` every 2 s until it's running. If the emulator process it started exits meanwhile, it fails at once with `EmulatorExitedEarly` and the end of the emulator's log; after 180 s it fails with `EmulatorBootTimeout` and leaves the emulator booting. The pause is injected, so tests don't wait.
- `StopWorktreeEmulatorUseCase(config, worktreePath)` reads the worktree's emulator port without reserving; with none reserved there's nothing to stop. `stopSerials` stops the given serials that are running, which the cleanup uses with serials read before removing worktrees.
- `ReadEmulatorStatesUseCase(config, worktrees)` maps each worktree with a reserved emulator port to its serial and state, from stored reservations and a single `deviceStates` call; worktrees without a reservation are left out.

### Gradle daemon use cases

Used when the repository declares the Gradle provider (and by `shogun gradle` anywhere):

- `ListGradleDaemonsUseCase` lists the daemons from `GradleDaemonSource` and, given the repository's worktrees, attributes each Gradle daemon by its last build directory: `RemovedWorktree` if that folder no longer exists, otherwise the worktree with the longest path containing it (the same rule as running agents, so nested worktrees win), otherwise `OtherProject`.
- `StopGradleDaemonsUseCase` stops the given pids, or every daemon. It lists the daemons again first, so the decision uses their current state: a Gradle daemon is stopped only if `Idle`, a Kotlin daemon only if no Gradle daemon is `Busy` or `Unknown`. Stopping runs `kill <pid>` (SIGTERM, so the JVM runs Gradle's shutdown hooks). One failure doesn't stop the rest.
- `idleDaemonsOfRemovedWorktrees` picks, from a listing taken before a cleanup, the idle Gradle daemons whose last build was in one of the removed worktrees. The cleanup passes them to `StopGradleDaemonsUseCase`, whose fresh check still keeps a daemon that became busy.
- `CheckGradleBuildCacheUseCase` tells whether `org.gradle.caching=true` is set in the repository's `gradle.properties` or in `$GRADLE_USER_HOME/gradle.properties`. With Gradle's local build cache on, a new worktree reuses task outputs built in other worktrees, because cache keys don't depend on the checkout's path.
- `gradleSettingsContribution` is the provider's environment contribution when `GradleProvider.hasSettings`: one variable, `GRADLE_OPTS`, built by `gradleOpts` from `-Dorg.gradle.jvmargs="…"`, `-Dorg.gradle.daemon.idletimeout=<ms>` and `-Dorg.gradle.project.kotlin.daemon.jvmargs=-Xmx<size>`, each only for a declared setting. System properties in `GRADLE_OPTS` override `gradle.properties`, the user's included. `gradleDaemonArguments` keeps the effective `org.gradle.jvmargs` (`effectiveGradleJvmArgs`: the user's `gradle.properties`, else the base repository's) with every `-Xmx` replaced by the declared heap, or the heap alone when there are none or they contain a double quote. The value doesn't depend on the worktree, so every worktree's daemons are compatible and reused across worktrees; a `GRADLE_OPTS` declared in `env` wins, as for any provider variable.

### Xcode use cases

Used on macOS when the repository declares the Xcode provider:

- `ListDerivedDataUseCase(worktrees, includeCompilationCache, measure)` attributes every folder from `DerivedDataSource`: a workspace that doesn't exist is `UnavailableVolume` if its volume isn't mounted, otherwise `Orphaned`; an existing one belongs to the worktree with the longest path containing it (`owningWorktree`, the same rule as running agents and Gradle daemons), otherwise to `OtherProject`. `measure` says which folders get their size: `NONE`, `ORPHANS` or `ALL`. Orphans are machine-wide: once its project is gone, a folder can't be used by anything, whichever tool created it.
- `DeleteOrphanedDerivedDataUseCase` lists the folders again, keeps those still orphaned, checks each workspace once more right before deleting it (a project restored in the meantime keeps its folder), measures each orphan that has no size yet so `bytesFreed` is right, and returns a `DerivedDataDeletion`; one failure doesn't stop the rest. A removed worktree's folder is orphaned by definition, so a cleanup only runs this use case after the removals.
- `xcodeCompilationCacheContribution` is the provider's environment contribution when `XcodeProvider.compilationCache` is set, on macOS (`MachineProviderContributions`): a resource `xcode-compilation-cache` whose `create` writes `~/.shogunai/xcode/compilation-cache.xcconfig` (`COMPILATION_CACHE_ENABLE_CACHING` and Swift and Clang prefix mapping, five `KEY = YES` lines) and whose `exists` checks the file holds that content, plus the variable `XCODE_XCCONFIG_FILE=<path>`. Being a resource, it's only written when resolving with reservation and is re-created if deleted; the file is the same for every repository on the machine. An `XCODE_XCCONFIG_FILE` declared in `env` wins.

### Update check use cases

- `CheckForUpdateUseCase(mode)` reads the preferences and, in `AUTOMATIC` mode, returns `NotDue` when the automatic check is off or the running version is a development build (`0.0.0`). Otherwise it asks `ReleaseSource` for the latest release: a version not newer than the running one, or, in automatic mode, not newer than the skipped version, is `UpToDate`; a newer one is `Available` with the install channel and its `UpdateAction`. A failed read is returned as the failure; the desktop app ignores it in automatic mode and shows it in manual mode.
- `DetectInstallChannelUseCase` tells how the running app was installed, and is only called when a newer version exists: on macOS `HOMEBREW` when a `shogunai` folder exists in `/opt/homebrew/Caskroom` or `/usr/local/Caskroom`, else `DMG`; on Windows `SCOOP` when the app runs from `\scoop\apps\shogunai\`, else `WINGET` when `winget list --id LuxionServer.ShogunAi --exact` lists it (10-second limit), else `MSI`; on Linux `DEB` when the app lives under `/opt/shogunai`, else `UNKNOWN`.
